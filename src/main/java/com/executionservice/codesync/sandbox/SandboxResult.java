package com.executionservice.codesync.sandbox;

import com.executionservice.codesync.entity.ExecutionJob;

public class SandboxResult {

    private String              stdout;
    private String              stderr;
    private int                 exitCode;
    private long                executionTimeMs;
    private long                memoryUsedKb;
    private ExecutionJob.Status status;

    // ── No-arg constructor ────────────────────────────────────────────────────
    public SandboxResult() {}

    // ── All-arg constructor ───────────────────────────────────────────────────
    public SandboxResult(String stdout, String stderr, int exitCode,
                         long executionTimeMs, long memoryUsedKb,
                         ExecutionJob.Status status) {
        this.stdout         = stdout;
        this.stderr         = stderr;
        this.exitCode       = exitCode;
        this.executionTimeMs = executionTimeMs;
        this.memoryUsedKb   = memoryUsedKb;
        this.status         = status;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String              getStdout()          { return stdout; }
    public String              getStderr()          { return stderr; }
    public int                 getExitCode()        { return exitCode; }
    public long                getExecutionTimeMs() { return executionTimeMs; }
    public long                getMemoryUsedKb()    { return memoryUsedKb; }
    public ExecutionJob.Status getStatus()          { return status; }

    // ── Setters ───────────────────────────────────────────────────────────────
    public void setStdout(String stdout)                      { this.stdout = stdout; }
    public void setStderr(String stderr)                      { this.stderr = stderr; }
    public void setExitCode(int exitCode)                     { this.exitCode = exitCode; }
    public void setExecutionTimeMs(long executionTimeMs)      { this.executionTimeMs = executionTimeMs; }
    public void setMemoryUsedKb(long memoryUsedKb)            { this.memoryUsedKb = memoryUsedKb; }
    public void setStatus(ExecutionJob.Status status)         { this.status = status; }

    // ── Static factory for error results ─────────────────────────────────────
    public static SandboxResult error(String message) {
        return SandboxResult.builder()
                .stdout("")
                .stderr("Internal error: " + message)
                .exitCode(-1)
                .executionTimeMs(0L)
                .memoryUsedKb(0L)
                .status(ExecutionJob.Status.FAILED)
                .build();
    }

    // ── Builder ───────────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final SandboxResult r = new SandboxResult();

        public Builder stdout(String stdout)                      { r.stdout = stdout;               return this; }
        public Builder stderr(String stderr)                      { r.stderr = stderr;               return this; }
        public Builder exitCode(int exitCode)                     { r.exitCode = exitCode;           return this; }
        public Builder executionTimeMs(long ms)                   { r.executionTimeMs = ms;          return this; }
        public Builder memoryUsedKb(long kb)                      { r.memoryUsedKb = kb;             return this; }
        public Builder status(ExecutionJob.Status status)         { r.status = status;               return this; }
        public SandboxResult build()                              { return r; }
    }
}
