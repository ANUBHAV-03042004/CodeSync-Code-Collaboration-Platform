package com.executionservice.codesync.service;

import com.executionservice.codesync.entity.ExecutionJob;
import com.executionservice.codesync.repository.ExecutionJobRepository;
import com.executionservice.codesync.sandbox.SandboxResult;
import com.executionservice.codesync.sandbox.SandboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private static final String EXEC_EXCHANGE   = "codesync.execution.exchange";
    private static final String EXEC_RESULT_KEY = "execution.job.result";

    private final ExecutionJobRepository jobRepository;
    private final SandboxService         sandboxService;
    private final SimpMessagingTemplate  messagingTemplate;
    private final RabbitTemplate         rabbitTemplate;

    /**
     * FIX — self-injection via ApplicationContext.
     *
     * Root cause of "Job not found" IllegalArgumentException:
     *
     * submitJob() is @Transactional. Inside it, executeAsync() is called
     * directly on `this`. @Async is implemented via Spring AOP proxy —
     * a self-call on `this` bypasses the proxy entirely, so executeAsync()
     * runs synchronously on the same thread WITHOUT the @Async wrapper.
     *
     * Consequence: executeAsync() runs BEFORE the @Transactional commit of
     * submitJob() flushes to the DB. When findById(jobId) is called inside
     * executeAsync(), the row doesn't exist yet → IllegalArgumentException.
     *
     * Fix: inject the Spring-managed proxy of this bean via ApplicationContext
     * and call self.executeAsync() through the proxy instead of `this`.
     * The proxy honours @Async and schedules execution on the sandboxExecutor
     * thread pool AFTER the current transaction commits.
     */
    private final ApplicationContext applicationContext;

    public ExecutionService(ExecutionJobRepository jobRepository,
                            SandboxService sandboxService,
                            SimpMessagingTemplate messagingTemplate,
                            RabbitTemplate rabbitTemplate,
                            ApplicationContext applicationContext) {
        this.jobRepository      = jobRepository;
        this.sandboxService     = sandboxService;
        this.messagingTemplate  = messagingTemplate;
        this.rabbitTemplate     = rabbitTemplate;
        this.applicationContext = applicationContext;
    }

    // ── Submit job ────────────────────────────────────────────────────────────

    @Transactional
    public ExecutionJob submitJob(Long userId, Long projectId, Long fileId,
                                   String language, String sourceCode, String stdin) {
        ExecutionJob job = ExecutionJob.builder()
                .jobId(UUID.randomUUID().toString())
                .userId(userId)
                .projectId(projectId)
                .fileId(fileId)
                .language(language)
                .sourceCode(sourceCode)
                .stdin(stdin)
                .status(ExecutionJob.Status.QUEUED)
                .build();

        jobRepository.save(job);
        log.info("Job {} queued [lang={}, user={}]", job.getJobId(), language, userId);

        // FIX: call through the Spring proxy so @Async is honoured AND the
        // async execution starts AFTER this @Transactional method commits,
        // meaning the job row is in the DB when findById() is called.
        String jobId = job.getJobId();
        getSelf().executeAsync(jobId);

        return job;
    }

    @Async("sandboxExecutor")
    public void executeAsync(String jobId) {
        // FIX: use findById with a helpful error — job must exist by now
        // because we are called through the proxy after the transaction commits
        ExecutionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        job.setStatus(ExecutionJob.Status.RUNNING);
        jobRepository.save(job);
        notifyStatusChange(job);

        try {
            streamLine(job.getUserId(), jobId, "⚙️  Executing " + job.getLanguage() + " code...\n");

            SandboxResult result = sandboxService.execute(
                    job.getLanguage(), job.getSourceCode(), job.getStdin());

            job.setStdout(result.getStdout());
            job.setStderr(result.getStderr());
            job.setExitCode(result.getExitCode());
            job.setExecutionTimeMs(result.getExecutionTimeMs());
            job.setMemoryUsedKb(result.getMemoryUsedKb());
            job.setStatus(result.getStatus());
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);

            if (result.getStdout() != null && !result.getStdout().isEmpty())
                streamLine(job.getUserId(), jobId, result.getStdout());
            if (result.getStderr() != null && !result.getStderr().isEmpty())
                streamLine(job.getUserId(), jobId, "[stderr] " + result.getStderr());

            streamLine(job.getUserId(), jobId,
                    "\n✅ Exited with code " + result.getExitCode()
                    + " in " + result.getExecutionTimeMs() + "ms");

            // Publish result — best-effort, never crash the job on broker failure
            try {
                rabbitTemplate.convertAndSend(EXEC_EXCHANGE, EXEC_RESULT_KEY, Map.of(
                        "jobId",           jobId,
                        "status",          result.getStatus().name(),
                        "exitCode",        result.getExitCode(),
                        "executionTimeMs", result.getExecutionTimeMs()
                ));
            } catch (AmqpException e) {
                log.warn("Result event not published for job {} — RabbitMQ unavailable: {}",
                        jobId, e.getMessage());
            }

            log.info("Job {} completed [status={}, exitCode={}]",
                    jobId, result.getStatus(), result.getExitCode());

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            job.setStatus(ExecutionJob.Status.FAILED);
            job.setStderr("Execution error: " + e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
            streamLine(job.getUserId(), jobId, "\n❌ Execution failed: " + e.getMessage());
        }

        notifyStatusChange(job);
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @Transactional
    public void cancelJob(String jobId, Long requestingUserId) {
        ExecutionJob job = findOrThrow(jobId);
        if (!job.getUserId().equals(requestingUserId))
            throw new SecurityException("Only the job owner can cancel it");

        if (job.getStatus() == ExecutionJob.Status.QUEUED ||
            job.getStatus() == ExecutionJob.Status.RUNNING) {
            job.setStatus(ExecutionJob.Status.CANCELLED);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
            streamLine(job.getUserId(), jobId, "\n🛑 Job cancelled");
            log.info("Job {} cancelled by user {}", jobId, requestingUserId);
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<ExecutionJob> getJobById(String jobId) {
        return jobRepository.findById(jobId);
    }

    @Transactional(readOnly = true)
    public List<ExecutionJob> getJobsByUser(Long userId) {
        return jobRepository.findRecentByUser(userId);
    }

    @Transactional(readOnly = true)
    public List<ExecutionJob> getJobsByProject(Long projectId) {
        return jobRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public List<String> getSupportedLanguages() {
        return sandboxService.getSupportedLanguages();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats(Long userId) {
        return Map.of(
                "totalJobs",  jobRepository.countByUserId(userId),
                "byLanguage", jobRepository.countByLanguage()
        );
    }

    // ── WebSocket helpers ─────────────────────────────────────────────────────

    private void streamLine(Long userId, String jobId, String line) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/execution/" + jobId,
                Map.of("jobId", jobId, "output", line, "ts", System.currentTimeMillis())
        );
    }

    private void notifyStatusChange(ExecutionJob job) {
        messagingTemplate.convertAndSendToUser(
                job.getUserId().toString(),
                "/queue/execution/" + job.getJobId() + "/status",
                Map.of("jobId", job.getJobId(), "status", job.getStatus().name())
        );
    }

    private ExecutionJob findOrThrow(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }

    /**
     * Returns the Spring-managed proxy of this bean so @Async calls
     * go through the AOP proxy instead of bypassing it via `this`.
     */
    private ExecutionService getSelf() {
        return applicationContext.getBean(ExecutionService.class);
    }
}
