package com.executionservice.codesync;

import com.executionservice.codesync.entity.ExecutionJob;
import com.executionservice.codesync.repository.ExecutionJobRepository;
import com.executionservice.codesync.sandbox.SandboxResult;
import com.executionservice.codesync.sandbox.SandboxService;
import com.executionservice.codesync.service.ExecutionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    @Mock ExecutionJobRepository jobRepository;
    @Mock SandboxService         sandboxService;
    @Mock SimpMessagingTemplate  messagingTemplate;
    @Mock RabbitTemplate         rabbitTemplate;
    @Mock ApplicationContext     applicationContext;

    ExecutionService executionService;

    private static final Long   USER_ID     = 1L;
    private static final Long   PROJECT_ID  = 100L;
    private static final String LANGUAGE    = "python";
    private static final String SOURCE_CODE = "print('Hello')";

    @BeforeEach
    void setUp() {
        executionService = new ExecutionService(
                jobRepository, sandboxService, messagingTemplate,
                rabbitTemplate, applicationContext);

        // FIX: lenient() prevents UnnecessaryStubbingException.
        // This stub is only needed by tests that call submitJob() which
        // internally calls getSelf().executeAsync().
        // Tests that call executeAsync() directly never touch getSelf() —
        // Mockito strict mode would flag a plain when(...) as unnecessary.
        lenient().when(applicationContext.getBean(ExecutionService.class))
                 .thenReturn(executionService);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1 — submitJob persists job with QUEUED status
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("submitJob")
    class SubmitJobTests {

        @Test
        @DisplayName("saves a new job with QUEUED status and returns it")
        void submitJob_savesQueuedJob() {
            when(jobRepository.save(any(ExecutionJob.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.findById(anyString()))
                    .thenAnswer(inv -> Optional.of(buildJobWithId(inv.getArgument(0))));
            when(sandboxService.execute(anyString(), anyString(), any()))
                    .thenReturn(completedResult());

            ExecutionJob result = executionService.submitJob(
                    USER_ID, PROJECT_ID, null, LANGUAGE, SOURCE_CODE, null);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ExecutionJob.Status.QUEUED);
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getLanguage()).isEqualTo(LANGUAGE);
            assertThat(result.getSourceCode()).isEqualTo(SOURCE_CODE);
            assertThat(result.getJobId()).isNotBlank();
            verify(jobRepository, atLeastOnce()).save(any(ExecutionJob.class));
        }

        @Test
        @DisplayName("generates a unique UUID jobId for each submission")
        void submitJob_generatesUniqueJobId() {
            when(jobRepository.save(any(ExecutionJob.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.findById(anyString()))
                    .thenAnswer(inv -> Optional.of(buildJobWithId(inv.getArgument(0))));
            when(sandboxService.execute(anyString(), anyString(), any()))
                    .thenReturn(completedResult());

            ExecutionJob job1 = executionService.submitJob(USER_ID, PROJECT_ID, null, LANGUAGE, SOURCE_CODE, null);
            ExecutionJob job2 = executionService.submitJob(USER_ID, PROJECT_ID, null, LANGUAGE, SOURCE_CODE, null);

            assertThat(job1.getJobId()).isNotEqualTo(job2.getJobId());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2 — getJobById
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getJobById")
    class GetJobByIdTests {

        @Test
        @DisplayName("returns present Optional when job exists")
        void getJobById_returnsJob() {
            ExecutionJob job = buildSampleJob("job-123", ExecutionJob.Status.COMPLETED);
            when(jobRepository.findById("job-123")).thenReturn(Optional.of(job));

            Optional<ExecutionJob> result = executionService.getJobById("job-123");

            assertThat(result).isPresent();
            assertThat(result.get().getJobId()).isEqualTo("job-123");
            assertThat(result.get().getStatus()).isEqualTo(ExecutionJob.Status.COMPLETED);
        }

        @Test
        @DisplayName("returns empty Optional when job does not exist")
        void getJobById_returnsEmptyWhenNotFound() {
            when(jobRepository.findById("missing-id")).thenReturn(Optional.empty());

            Optional<ExecutionJob> result = executionService.getJobById("missing-id");

            assertThat(result).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3 — cancelJob
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("cancelJob")
    class CancelJobTests {

        @Test
        @DisplayName("cancels a QUEUED job owned by the requesting user")
        void cancelJob_cancelsQueuedJob() {
            ExecutionJob job = buildSampleJob("job-cancel", ExecutionJob.Status.QUEUED);
            when(jobRepository.findById("job-cancel")).thenReturn(Optional.of(job));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            executionService.cancelJob("job-cancel", USER_ID);

            assertThat(job.getStatus()).isEqualTo(ExecutionJob.Status.CANCELLED);
            assertThat(job.getCompletedAt()).isNotNull();
            verify(jobRepository).save(job);
        }

        @Test
        @DisplayName("throws SecurityException when a different user tries to cancel")
        void cancelJob_throwsWhenWrongUser() {
            ExecutionJob job = buildSampleJob("job-sec", ExecutionJob.Status.QUEUED);
            when(jobRepository.findById("job-sec")).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> executionService.cancelJob("job-sec", 999L))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("owner");
        }

        @Test
        @DisplayName("does NOT cancel an already COMPLETED job")
        void cancelJob_ignoresCompletedJob() {
            ExecutionJob job = buildSampleJob("job-done", ExecutionJob.Status.COMPLETED);
            when(jobRepository.findById("job-done")).thenReturn(Optional.of(job));

            executionService.cancelJob("job-done", USER_ID);

            assertThat(job.getStatus()).isEqualTo(ExecutionJob.Status.COMPLETED);
            verify(jobRepository, never()).save(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 4 — getJobsByUser
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("getJobsByUser returns jobs from repository")
    void getJobsByUser_returnsJobs() {
        List<ExecutionJob> jobs = List.of(
                buildSampleJob("j1", ExecutionJob.Status.COMPLETED),
                buildSampleJob("j2", ExecutionJob.Status.FAILED));
        when(jobRepository.findRecentByUser(USER_ID)).thenReturn(jobs);

        List<ExecutionJob> result = executionService.getJobsByUser(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getJobId()).isEqualTo("j1");
        assertThat(result.get(1).getStatus()).isEqualTo(ExecutionJob.Status.FAILED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 5 — executeAsync COMPLETED
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("executeAsync persists COMPLETED status when sandbox succeeds")
    void executeAsync_persistsCompletedStatus() {
        ExecutionJob job = buildSampleJob("job-async", ExecutionJob.Status.QUEUED);
        when(jobRepository.findById("job-async")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sandboxService.execute(anyString(), anyString(), any())).thenReturn(completedResult());

        executionService.executeAsync("job-async");

        ArgumentCaptor<ExecutionJob> captor = ArgumentCaptor.forClass(ExecutionJob.class);
        verify(jobRepository, atLeast(2)).save(captor.capture());
        ExecutionJob lastSaved = captor.getAllValues().stream()
                .filter(j -> j.getStatus() == ExecutionJob.Status.COMPLETED)
                .findFirst().orElse(null);

        assertThat(lastSaved).isNotNull();
        assertThat(lastSaved.getStdout()).isEqualTo("Hello\n");
        assertThat(lastSaved.getExitCode()).isEqualTo(0);
        assertThat(lastSaved.getCompletedAt()).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 6 — executeAsync FAILED
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("executeAsync marks job FAILED when sandbox throws exception")
    void executeAsync_marksFailedOnException() {
        ExecutionJob job = buildSampleJob("job-fail", ExecutionJob.Status.QUEUED);
        when(jobRepository.findById("job-fail")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sandboxService.execute(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Docker daemon not reachable"));

        executionService.executeAsync("job-fail");

        ArgumentCaptor<ExecutionJob> captor = ArgumentCaptor.forClass(ExecutionJob.class);
        verify(jobRepository, atLeast(2)).save(captor.capture());
        ExecutionJob failedJob = captor.getAllValues().stream()
                .filter(j -> j.getStatus() == ExecutionJob.Status.FAILED)
                .findFirst().orElse(null);

        assertThat(failedJob).isNotNull();
        assertThat(failedJob.getStderr()).contains("Docker daemon not reachable");
        assertThat(failedJob.getCompletedAt()).isNotNull();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ExecutionJob buildSampleJob(String jobId, ExecutionJob.Status status) {
        ExecutionJob job = new ExecutionJob();
        job.setJobId(jobId);
        job.setUserId(USER_ID);
        job.setProjectId(PROJECT_ID);
        job.setLanguage(LANGUAGE);
        job.setSourceCode(SOURCE_CODE);
        job.setStatus(status);
        return job;
    }

    /** Builds a job using the UUID provided by thenAnswer — used in submitJob tests */
    private ExecutionJob buildJobWithId(String jobId) {
        return buildSampleJob(jobId, ExecutionJob.Status.QUEUED);
    }

    /** Standard COMPLETED sandbox result for happy-path tests */
    private SandboxResult completedResult() {
        return SandboxResult.builder()
                .stdout("Hello\n").stderr("").exitCode(0)
                .executionTimeMs(42L).memoryUsedKb(512L)
                .status(ExecutionJob.Status.COMPLETED)
                .build();
    }
}
