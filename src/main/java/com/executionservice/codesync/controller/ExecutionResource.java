package com.executionservice.codesync.controller;

import com.executionservice.codesync.entity.ExecutionJob;
import com.executionservice.codesync.service.ExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/executions")
@Tag(name = "Execution", description = "Code execution endpoints — submit, poll, cancel jobs")
public class ExecutionResource {

    // FIX: explicit constructor instead of @RequiredArgsConstructor
    private final ExecutionService executionService;

    public ExecutionResource(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @Operation(summary = "Submit a new code execution job")
    @PostMapping
    public ResponseEntity<ExecutionJob> submit(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody SubmitRequest req) {
        ExecutionJob job = executionService.submitJob(
                Long.parseLong(userId), req.projectId(), req.fileId(),
                req.language(), req.sourceCode(), req.stdin());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @Operation(summary = "Get a job by ID")
    @GetMapping("/{jobId}")
    public ResponseEntity<ExecutionJob> getJob(@PathVariable String jobId) {
        return executionService.getJobById(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get all jobs for a user")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ExecutionJob>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(executionService.getJobsByUser(userId));
    }

    @Operation(summary = "Get all jobs for a project")
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<ExecutionJob>> getByProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(executionService.getJobsByProject(projectId));
    }

    @Operation(summary = "Cancel a running or queued job")
    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable String jobId,
            @RequestHeader("X-User-Id") String userId) {
        executionService.cancelJob(jobId, Long.parseLong(userId));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "List all supported programming languages")
    @GetMapping("/languages")
    public ResponseEntity<List<String>> getSupportedLanguages() {
        return ResponseEntity.ok(executionService.getSupportedLanguages());
    }

    @Operation(summary = "Get execution statistics for a user")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(executionService.getStats(Long.parseLong(userId)));
    }

    record SubmitRequest(Long projectId, Long fileId,
                         String language, String sourceCode, String stdin) {}
}
