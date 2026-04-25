package com.collabservice.codesync.controller;

import com.collabservice.codesync.service.CollabSessionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CollabResource.class)
@DisplayName("CollabResource REST Controller Tests")
class CollabResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CollabSessionService sessionService;

    // ── POST /api/v1/sessions ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/sessions — create session")
    class CreateSession {

        @Test
        @WithMockUser
        @DisplayName("returns 201 Created with sessionId on success")
        void returns201WithSessionId() throws Exception {
            when(sessionService.createSession(anyLong(), anyLong(), anyLong(),
                    anyString(), anyInt(), anyBoolean(), any()))
                    .thenReturn("abc-123");

            mockMvc.perform(post("/api/v1/sessions")
                            .with(csrf())
                            .header("X-User-Id", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "projectId": 1,
                                  "fileId": 10,
                                  "language": "java",
                                  "maxParticipants": 5,
                                  "passwordProtected": false,
                                  "password": null
                                }
                                """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sessionId").value("abc-123"));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when X-User-Id header is missing")
        void returns400WhenUserIdMissing() throws Exception {
            mockMvc.perform(post("/api/v1/sessions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"projectId\":1,\"fileId\":1,\"language\":\"java\",\"maxParticipants\":5,\"passwordProtected\":false}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── POST /api/v1/sessions/{sessionId}/join ────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/sessions/{id}/join")
    class JoinSession {

        @Test
        @WithMockUser
        @DisplayName("returns 200 OK when join succeeds")
        void returns200OnJoin() throws Exception {
            doNothing().when(sessionService).joinSession(anyString(), anyLong());

            mockMvc.perform(post("/api/v1/sessions/session-abc/join")
                            .with(csrf())
                            .header("X-User-Id", "5"))
                    .andExpect(status().isOk());

            verify(sessionService).joinSession("session-abc", 5L);
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when session is not active")
        void returns400WhenSessionInactive() throws Exception {
            doThrow(new IllegalStateException("Session is not active"))
                    .when(sessionService).joinSession(anyString(), anyLong());

            mockMvc.perform(post("/api/v1/sessions/dead-session/join")
                            .with(csrf())
                            .header("X-User-Id", "5"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── POST /api/v1/sessions/{sessionId}/leave ───────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("POST /leave — returns 200 OK and delegates to service")
    void leaveSession_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/session-abc/leave")
                        .with(csrf())
                        .header("X-User-Id", "7"))
                .andExpect(status().isOk());

        verify(sessionService).leaveSession("session-abc", 7L);
    }

    // ── POST /api/v1/sessions/{sessionId}/end ────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("POST /end — returns 200 OK and ends session")
    void endSession_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/session-abc/end")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(sessionService).endSession("session-abc");
    }

    // ── POST /api/v1/sessions/{sessionId}/kick/{targetUserId} ─────────────────

    @Test
    @WithMockUser
    @DisplayName("POST /kick/{targetUserId} — returns 200 OK")
    void kick_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/session-abc/kick/99")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(sessionService).kickParticipant("session-abc", 99L);
    }

    // ── GET /api/v1/sessions/{sessionId}/participants ─────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /participants — returns set of userId strings")
    void getParticipants_returnsSet() throws Exception {
        when(sessionService.getParticipants("session-abc"))
                .thenReturn(Set.of("1", "2", "3"));

        mockMvc.perform(get("/api/v1/sessions/session-abc/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ── GET /api/v1/sessions/{sessionId}/active ───────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /active — returns active=true when session exists")
    void isActive_returnsTrue() throws Exception {
        when(sessionService.isSessionActive("session-abc")).thenReturn(true);

        mockMvc.perform(get("/api/v1/sessions/session-abc/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /active — returns active=false when session expired")
    void isActive_returnsFalse() throws Exception {
        when(sessionService.isSessionActive("expired")).thenReturn(false);

        mockMvc.perform(get("/api/v1/sessions/expired/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}