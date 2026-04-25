package com.collabservice.codesync.controller;

import com.collabservice.codesync.service.CollabSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.Set;

/**
 * STOMP message handlers for the collab-service.
 *
 * Client connects to: ws://localhost:8084/ws/collab/websocket
 *
 * Subscriptions:
 *   /topic/session.{id}.edit     <- edit deltas
 *   /topic/session.{id}.cursor   <- cursor positions
 *   /topic/session.{id}.events   <- join/leave/kick/end
 *   /user/queue/kicked            <- personal kick notification
 *
 * Sends to:
 *   /app/session.{id}.edit       -> broadcastEditDelta
 *   /app/session.{id}.cursor     -> updateCursor
 *   /app/session.{id}.leave      -> leaveSession
 */
@Controller
public class CollabWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(CollabWebSocketController.class);

    private final CollabSessionService sessionService;

    public CollabWebSocketController(CollabSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** Client sends an edit delta; broadcast to all other participants */
    @MessageMapping("/session.{sessionId}.edit")
    public void handleEdit(@DestinationVariable String sessionId,
                            @Header("X-User-Id") String userId,
                            @Payload Map<String, Object> delta) {
        log.debug("Edit delta from user {} in session {}", userId, sessionId);
        sessionService.broadcastEditDelta(sessionId, Long.parseLong(userId), delta);
    }

    /** Client sends cursor position; relay to all participants */
    @MessageMapping("/session.{sessionId}.cursor")
    public void handleCursor(@DestinationVariable String sessionId,
                              @Header("X-User-Id") String userId,
                              @Payload Map<String, Object> pos) {
        int line = ((Number) pos.get("line")).intValue();
        int col  = ((Number) pos.get("col")).intValue();
        sessionService.updateCursor(sessionId, Long.parseLong(userId), line, col);
    }

    /** Client notifies it is leaving the session */
    @MessageMapping("/session.{sessionId}.leave")
    public void handleLeave(@DestinationVariable String sessionId,
                             @Header("X-User-Id") String userId) {
        sessionService.leaveSession(sessionId, Long.parseLong(userId));
    }

    /** On subscribe: return the current participant list for this session */
    @SubscribeMapping("/session.{sessionId}.participants")
    public Set<String> onSubscribeParticipants(@DestinationVariable String sessionId) {
        return sessionService.getParticipants(sessionId);
    }

    /** On subscribe: return all current cursor positions */
    @SubscribeMapping("/session.{sessionId}.cursors")
    public Map<Object, Object> onSubscribeCursors(@DestinationVariable String sessionId) {
        return sessionService.getCursors(sessionId);
    }
}
