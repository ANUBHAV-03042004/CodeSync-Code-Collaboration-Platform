package com.versionservice.codesync.controller;

import com.versionservice.codesync.entity.Snapshot;
import com.versionservice.codesync.service.VersionService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/versions")
public class VersionResource {

    private final VersionService versionService;

    public VersionResource(VersionService versionService) {
        this.versionService = versionService;
    }

    // POST /api/v1/versions
    @PostMapping
    public ResponseEntity<Snapshot> createSnapshot(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody CreateSnapshotReq req) {
        Snapshot snap = versionService.createSnapshot(
                req.projectId(), req.fileId(), Long.parseLong(userId),
                req.message(), req.content(), req.branch());
        return ResponseEntity.status(HttpStatus.CREATED).body(snap);
    }

    // GET /api/v1/versions/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Snapshot> getById(@PathVariable Long id) {
        return versionService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/v1/versions/file/{fileId}/history
    @GetMapping("/file/{fileId}/history")
    public ResponseEntity<List<Snapshot>> getHistory(@PathVariable Long fileId) {
        return ResponseEntity.ok(versionService.getFileHistory(fileId));
    }

    // GET /api/v1/versions/file/{fileId}/latest
    @GetMapping("/file/{fileId}/latest")
    public ResponseEntity<Snapshot> getLatest(@PathVariable Long fileId) {
        return versionService.getLatest(fileId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/v1/versions/project/{projectId}
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<Snapshot>> getByProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(versionService.getByProject(projectId));
    }

    // GET /api/v1/versions/project/{projectId}/branches
    @GetMapping("/project/{projectId}/branches")
    public ResponseEntity<List<String>> getBranches(@PathVariable Long projectId) {
        return ResponseEntity.ok(versionService.getBranches(projectId));
    }

    // GET /api/v1/versions/project/{projectId}/branch/{branch}
    @GetMapping("/project/{projectId}/branch/{branch}")
    public ResponseEntity<List<Snapshot>> getByBranch(@PathVariable Long projectId,
                                                       @PathVariable String branch) {
        return ResponseEntity.ok(versionService.getByBranch(projectId, branch));
    }

    // GET /api/v1/versions/diff?a={id}&b={id}
    @GetMapping("/diff")
    public ResponseEntity<List<Map<String, Object>>> diff(
            @RequestParam Long a, @RequestParam Long b) {
        return ResponseEntity.ok(versionService.diffSnapshots(a, b));
    }

    // POST /api/v1/versions/{id}/restore
    @PostMapping("/{id}/restore")
    public ResponseEntity<Snapshot> restore(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(versionService.restoreSnapshot(id, Long.parseLong(userId)));
    }

    // POST /api/v1/versions/branch
    @PostMapping("/branch")
    public ResponseEntity<Snapshot> createBranch(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody CreateBranchReq req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(versionService.createBranch(req.fileId(), Long.parseLong(userId), req.branchName()));
    }

    // PUT /api/v1/versions/{id}/tag
    @PutMapping("/{id}/tag")
    public ResponseEntity<Snapshot> tagSnapshot(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(versionService.tagSnapshot(id, body.get("tag")));
    }

    record CreateSnapshotReq(Long projectId, Long fileId,
                              String message, String content, String branch) {}
    record CreateBranchReq(Long fileId, String branchName) {}
}
