package com.collabservice.codesync.controller;

import com.collabservice.codesync.service.CollabSessionService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/sessions")
public class CollabResource {

    private final CollabSessionService sessionService;

    public CollabResource(CollabSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody CreateSessionReq req) {
        String sessionId = sessionService.createSession(
                req.projectId(), req.fileId(), Long.parseLong(userId),
                req.language(), req.maxParticipants(),
                req.passwordProtected(), req.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("sessionId", sessionId));
    }

    @PostMapping("/{sessionId}/join")
    public ResponseEntity<Void> join(@PathVariable String sessionId,
                                      @RequestHeader("X-User-Id") String userId) {
        sessionService.joinSession(sessionId, Long.parseLong(userId));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{sessionId}/leave")
    public ResponseEntity<Void> leave(@PathVariable String sessionId,
                                       @RequestHeader("X-User-Id") String userId) {
        sessionService.leaveSession(sessionId, Long.parseLong(userId));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<Void> end(@PathVariable String sessionId) {
        sessionService.endSession(sessionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{sessionId}/kick/{targetUserId}")
    public ResponseEntity<Void> kick(@PathVariable String sessionId,
                                      @PathVariable Long targetUserId) {
        sessionService.kickParticipant(sessionId, targetUserId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{sessionId}/participants")
    public ResponseEntity<Set<String>> participants(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.getParticipants(sessionId));
    }

    @GetMapping("/{sessionId}/active")
    public ResponseEntity<Map<String, Boolean>> isActive(@PathVariable String sessionId) {
        return ResponseEntity.ok(Map.of("active", sessionService.isSessionActive(sessionId)));
    }

    record CreateSessionReq(Long projectId, Long fileId, String language,
                             int maxParticipants, boolean passwordProtected, String password) {}
}
