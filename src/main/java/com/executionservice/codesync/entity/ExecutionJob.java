package com.executionservice.codesync.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "execution_jobs",
       indexes = {
           @Index(name = "idx_job_user",    columnList = "userId"),
           @Index(name = "idx_job_status",  columnList = "status"),
           @Index(name = "idx_job_project", columnList = "projectId")
       })
public class ExecutionJob {

    @Id
    @Column(length = 36)
    private String jobId;

    private Long projectId;
    private Long fileId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String language;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String sourceCode;

    @Column(columnDefinition = "TEXT")
    private String stdin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.QUEUED;

    @Column(columnDefinition = "TEXT")
    private String stdout;

    @Column(columnDefinition = "TEXT")
    private String stderr;

    private Integer exitCode;
    private Long    executionTimeMs;
    private Long    memoryUsedKb;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private int priority = 0;

    public enum Status {
        QUEUED, RUNNING, COMPLETED, FAILED, TIMED_OUT, CANCELLED
    }

    // ── No-arg constructor ────────────────────────────────────────────────────
    public ExecutionJob() {}

    // ── Getters ───────────────────────────────────────────────────────────────
    public String getJobId()              { return jobId; }
    public Long getProjectId()            { return projectId; }
    public Long getFileId()               { return fileId; }
    public Long getUserId()               { return userId; }
    public String getLanguage()           { return language; }
    public String getSourceCode()         { return sourceCode; }
    public String getStdin()              { return stdin; }
    public Status getStatus()             { return status; }
    public String getStdout()             { return stdout; }
    public String getStderr()             { return stderr; }
    public Integer getExitCode()          { return exitCode; }
    public Long getExecutionTimeMs()      { return executionTimeMs; }
    public Long getMemoryUsedKb()         { return memoryUsedKb; }
    public LocalDateTime getCreatedAt()   { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public int getPriority()              { return priority; }

    // ── Setters ───────────────────────────────────────────────────────────────
    public void setJobId(String jobId)                    { this.jobId = jobId; }
    public void setProjectId(Long projectId)              { this.projectId = projectId; }
    public void setFileId(Long fileId)                    { this.fileId = fileId; }
    public void setUserId(Long userId)                    { this.userId = userId; }
    public void setLanguage(String language)              { this.language = language; }
    public void setSourceCode(String sourceCode)          { this.sourceCode = sourceCode; }
    public void setStdin(String stdin)                    { this.stdin = stdin; }
    public void setStatus(Status status)                  { this.status = status; }
    public void setStdout(String stdout)                  { this.stdout = stdout; }
    public void setStderr(String stderr)                  { this.stderr = stderr; }
    public void setExitCode(Integer exitCode)             { this.exitCode = exitCode; }
    public void setExecutionTimeMs(Long executionTimeMs)  { this.executionTimeMs = executionTimeMs; }
    public void setMemoryUsedKb(Long memoryUsedKb)        { this.memoryUsedKb = memoryUsedKb; }
    public void setCreatedAt(LocalDateTime createdAt)     { this.createdAt = createdAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public void setPriority(int priority)                 { this.priority = priority; }

    // ── Builder ───────────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ExecutionJob job = new ExecutionJob();

        public Builder jobId(String jobId)                    { job.jobId = jobId;                   return this; }
        public Builder projectId(Long projectId)              { job.projectId = projectId;           return this; }
        public Builder fileId(Long fileId)                    { job.fileId = fileId;                 return this; }
        public Builder userId(Long userId)                    { job.userId = userId;                 return this; }
        public Builder language(String language)              { job.language = language;             return this; }
        public Builder sourceCode(String sourceCode)          { job.sourceCode = sourceCode;         return this; }
        public Builder stdin(String stdin)                    { job.stdin = stdin;                   return this; }
        public Builder status(Status status)                  { job.status = status;                 return this; }
        public Builder stdout(String stdout)                  { job.stdout = stdout;                 return this; }
        public Builder stderr(String stderr)                  { job.stderr = stderr;                 return this; }
        public Builder exitCode(Integer exitCode)             { job.exitCode = exitCode;             return this; }
        public Builder executionTimeMs(Long ms)               { job.executionTimeMs = ms;            return this; }
        public Builder memoryUsedKb(Long kb)                  { job.memoryUsedKb = kb;               return this; }
        public Builder completedAt(LocalDateTime completedAt) { job.completedAt = completedAt;       return this; }
        public Builder priority(int priority)                 { job.priority = priority;             return this; }
        public ExecutionJob build()                           { return job; }
    }
}
