package com.collabservice.codesync.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * STOMP WebSocket configuration.
 *
 * Uses Spring's simple in-memory broker for STOMP routing.
 * Redis is used directly by CollabSessionService for pub/sub and state storage —
 * it does NOT act as a STOMP broker (Redis has no STOMP protocol support).
 *
 * For horizontal scaling across multiple collab-service pods, Redis pub/sub
 * in CollabSessionService already handles fan-out. The simple broker handles
 * per-instance STOMP routing.
 *
 * Topic layout:
 *   /topic/session.{sessionId}.edit     — OT/CRDT edit deltas
 *   /topic/session.{sessionId}.cursor   — cursor position updates
 *   /topic/session.{sessionId}.events   — join/leave/kick events
 *
 * Client sends to:
 *   /app/session.{sessionId}.edit
 *   /app/session.{sessionId}.cursor
 *   /app/session.{sessionId}.leave
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory STOMP broker — no external dependency needed.
        // Redis pub/sub in CollabSessionService handles cross-instance broadcasting.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/collab")
                .setAllowedOriginPatterns("*")
                .withSockJS();   // SockJS fallback for environments blocking raw WebSocket
    }
}
