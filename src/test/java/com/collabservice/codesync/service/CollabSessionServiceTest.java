package com.collabservice.codesync.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollabSessionService Tests")
class CollabSessionServiceTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private StringRedisTemplate   redisTemplate;
    @Mock private RabbitTemplate        rabbitTemplate;

    // Redis ops mocks
    @Mock private ValueOperations<String, String>        valueOps;
    @Mock private SetOperations<String, String>          setOps;
    @Mock private HashOperations<String, Object, Object> hashOps;

    @InjectMocks
    private CollabSessionService service;

    // ── setUp ─────────────────────────────────────────────────────────────────
    // No global stubs here — each nested class only stubs the ops it actually
    // triggers. Putting all three stubs in a top-level @BeforeEach caused
    // UnnecessaryStubbingException in tests that only hit one or two ops
    // (e.g. leaveSession uses setOps only, never valueOps or hashOps).

    // ── createSession ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createSession()")
    class CreateSession {

        @BeforeEach
        void setUp() {
            // createSession uses valueOps (set active key) + setOps (add participant)
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(redisTemplate.opsForSet()).thenReturn(setOps);
        }

        @Test
        @DisplayName("returns a non-null UUID sessionId")
        void returnsUuidSessionId() {
            String sessionId = service.createSession(1L, 10L, 42L, "java", 5, false, null);
            assertThat(sessionId).isNotBlank().hasSize(36);
        }

        @Test
        @DisplayName("stores ACTIVE flag in Redis with 30-min TTL")
        void storesActiveFlagInRedis() {
            service.createSession(1L, 10L, 42L, "python", 5, false, null);
            verify(valueOps).set(
                    argThat(k -> k.contains(":active")),
                    eq("ACTIVE"),
                    eq(30L),
                    eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("adds owner as first participant in Redis set")
        void addsOwnerAsParticipant() {
            service.createSession(1L, 10L, 42L, "java", 5, false, null);
            verify(setOps).add(argThat(k -> k.contains(":participants")), eq("42"));
        }

        @Test
        @DisplayName("broadcasts SESSION_CREATED event over STOMP")
        void broadcastsStompEvent() {
            service.createSession(1L, 10L, 42L, "java", 5, false, null);
            verify(messagingTemplate).convertAndSend(
                    argThat(dest -> dest.contains(".events")),
                    (Object) argThat(payload ->
                            ((Map<?, ?>) payload).get("type").equals("SESSION_CREATED")));
        }

        @Test
        @DisplayName("publishes audit event to RabbitMQ")
        void publishesAuditEvent() {
            service.createSession(1L, 10L, 42L, "java", 5, false, null);
            verify(rabbitTemplate).convertAndSend(
                    anyString(),
                    eq("collab.session.created"),
                    any(Map.class));
        }

        @Test
        @DisplayName("RabbitMQ failure does NOT propagate — real-time path unaffected")
        void rabbitFailureDoesNotPropagate() {
            doThrow(new RuntimeException("Broker down")).when(rabbitTemplate)
                    .convertAndSend(anyString(), anyString(), any(Map.class));

            assertThatNoException().isThrownBy(
                    () -> service.createSession(1L, 10L, 42L, "java", 5, false, null));

            verify(messagingTemplate).convertAndSend(anyString(), (Object) any(Map.class));
        }
    }

    // ── joinSession ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("joinSession()")
    class JoinSession {

        // No @BeforeEach stub — setOps is only reached in the happy-path test.
        // throwsWhenSessionInactive and refreshesTtlOnJoin both exit before
        // setOps.add() is called, so stubbing opsForSet() in setUp() would be
        // flagged as unnecessary by Mockito for those two tests.

        @Test
        @DisplayName("adds userId to participant set and broadcasts PARTICIPANT_JOINED")
        void addsParticipantAndBroadcasts() {
            String sessionId = "test-session-id";
            when(redisTemplate.opsForSet()).thenReturn(setOps);   // only this test reaches setOps.add()
            when(redisTemplate.hasKey("session:" + sessionId + ":active")).thenReturn(true);

            service.joinSession(sessionId, 99L);

            verify(setOps).add(argThat(k -> k.contains(":participants")), eq("99"));
            verify(messagingTemplate).convertAndSend(
                    argThat(dest -> dest.contains(".events")),
                    (Object) argThat(p ->
                            ((Map<?, ?>) p).get("type").equals("PARTICIPANT_JOINED")));
        }

        @Test
        @DisplayName("throws IllegalStateException when session is not active")
        void throwsWhenSessionInactive() {
            when(redisTemplate.hasKey(anyString())).thenReturn(false);

            assertThatThrownBy(() -> service.joinSession("dead-session", 1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not active");
        }

        @Test
        @DisplayName("refreshes TTL on join")
        void refreshesTtlOnJoin() {
            String sessionId = "test-session";
            when(redisTemplate.hasKey("session:" + sessionId + ":active")).thenReturn(true);
            when(redisTemplate.opsForSet()).thenReturn(setOps); // joinSession calls addParticipantToRedis before expire

            service.joinSession(sessionId, 7L);

            verify(redisTemplate).expire(
                    "session:" + sessionId + ":active", 30L, TimeUnit.MINUTES);
        }
    }

    // ── leaveSession ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("leaveSession()")
    class LeaveSession {

        @BeforeEach
        void setUp() {
            // leaveSession uses setOps (remove participant) only
            when(redisTemplate.opsForSet()).thenReturn(setOps);
        }

        @Test
        @DisplayName("removes userId from Redis set and broadcasts PARTICIPANT_LEFT")
        void removesParticipantAndBroadcasts() {
            service.leaveSession("sid", 5L);

            verify(setOps).remove("session:sid:participants", "5");
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/session.sid.events"),
                    (Object) argThat(p ->
                            ((Map<?, ?>) p).get("type").equals("PARTICIPANT_LEFT")));
        }

        @Test
        @DisplayName("publishes audit event to RabbitMQ on leave")
        void publishesAuditEvent() {
            service.leaveSession("sid", 5L);
            verify(rabbitTemplate).convertAndSend(anyString(),
                    eq("collab.session.left"), any(Map.class));
        }
    }

    // ── endSession ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("endSession()")
    class EndSession {

        // endSession calls redisTemplate.delete() directly — no opsFor* needed

        @Test
        @DisplayName("deletes all three Redis keys for the session")
        void deletesAllRedisKeys() {
            service.endSession("sid");

            verify(redisTemplate).delete("session:sid:active");
            verify(redisTemplate).delete("session:sid:participants");
            verify(redisTemplate).delete("session:sid:cursors");
        }

        @Test
        @DisplayName("broadcasts SESSION_ENDED event over STOMP")
        void broadcastsEndedEvent() {
            service.endSession("sid");
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/session.sid.events"),
                    (Object) argThat(p ->
                            ((Map<?, ?>) p).get("type").equals("SESSION_ENDED")));
        }
    }

    // ── kickParticipant ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("kickParticipant()")
    class KickParticipant {

        @BeforeEach
        void setUp() {
            // kickParticipant uses setOps (remove participant) only
            when(redisTemplate.opsForSet()).thenReturn(setOps);
        }

        @Test
        @DisplayName("sends personal /queue/kicked message to target user")
        void sendsPersonalKickMessage() {
            service.kickParticipant("sid", 77L);
            verify(messagingTemplate).convertAndSendToUser(
                    eq("77"), eq("/queue/kicked"), any(Map.class));
        }

        @Test
        @DisplayName("removes target user from participant set")
        void removesFromParticipantSet() {
            service.kickParticipant("sid", 77L);
            verify(setOps).remove("session:sid:participants", "77");
        }

        @Test
        @DisplayName("broadcasts PARTICIPANT_KICKED to the session topic")
        void broadcastsKickEvent() {
            service.kickParticipant("sid", 77L);
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/session.sid.events"),
                    (Object) argThat(p ->
                            ((Map<?, ?>) p).get("type").equals("PARTICIPANT_KICKED")));
        }
    }

    // ── updateCursor ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateCursor()")
    class UpdateCursor {

        @BeforeEach
        void setUp() {
            // updateCursor uses hashOps (put cursor) only
            when(redisTemplate.opsForHash()).thenReturn(hashOps);
        }

        @Test
        @DisplayName("stores cursor position as 'line:col' in Redis hash")
        void storesCursorInRedisHash() {
            service.updateCursor("sid", 3L, 10, 5);
            verify(hashOps).put("session:sid:cursors", "3", "10:5");
        }

        @Test
        @DisplayName("broadcasts cursor position over STOMP")
        void broadcastsCursorOverStomp() {
            service.updateCursor("sid", 3L, 10, 5);
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/session.sid.cursor"),
                    (Object) argThat(p -> {
                        Map<?, ?> m = (Map<?, ?>) p;
                        return m.get("line").equals(10) && m.get("col").equals(5);
                    }));
        }
    }

    // ── broadcastEditDelta ────────────────────────────────────────────────────

    @Nested
    @DisplayName("broadcastEditDelta()")
    class BroadcastEditDelta {

        // broadcastEditDelta only calls hasKey + expire — no opsFor* needed

        @Test
        @DisplayName("adds authorId and timestamp to delta before broadcasting")
        void enrichesDeltaWithMetadata() {
            when(redisTemplate.hasKey("session:sid:active")).thenReturn(true);
            Map<String, Object> delta = new HashMap<>(Map.of("op", "insert", "pos", 5));

            service.broadcastEditDelta("sid", 2L, delta);

            assertThat(delta).containsKey("authorId").containsKey("timestamp");
            assertThat(delta.get("authorId")).isEqualTo(2L);
        }

        @Test
        @DisplayName("broadcasts enriched delta to /topic/session.{id}.edit")
        void broadcastsToCorrectTopic() {
            when(redisTemplate.hasKey("session:sid:active")).thenReturn(true);

            service.broadcastEditDelta("sid", 2L, new HashMap<>(Map.of("op", "delete")));

            verify(messagingTemplate).convertAndSend(
                    eq("/topic/session.sid.edit"), (Object) any(Map.class));
        }

        @Test
        @DisplayName("throws when broadcasting to an inactive session")
        void throwsOnInactiveSession() {
            when(redisTemplate.hasKey(anyString())).thenReturn(false);

            assertThatThrownBy(() -> service.broadcastEditDelta("dead", 1L, new HashMap<>()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ── getParticipants ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getParticipants()")
    class GetParticipants {

        @BeforeEach
        void setUp() {
            when(redisTemplate.opsForSet()).thenReturn(setOps);
        }

        @Test
        @DisplayName("returns participant set from Redis")
        void returnsParticipantSet() {
            when(setOps.members("session:sid:participants"))
                    .thenReturn(new HashSet<>(Set.of("1", "2", "3")));

            Set<String> participants = service.getParticipants("sid");

            assertThat(participants).containsExactlyInAnyOrder("1", "2", "3");
        }

        @Test
        @DisplayName("returns empty set when Redis key does not exist")
        void returnsEmptySetWhenKeyMissing() {
            when(setOps.members(anyString())).thenReturn(null);

            assertThat(service.getParticipants("unknown")).isEmpty();
        }
    }

    // ── isSessionActive ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("isSessionActive()")
    class IsSessionActive {

        // isSessionActive only calls hasKey — no opsFor* needed

        @Test
        @DisplayName("returns true when Redis key exists")
        void returnsTrueWhenKeyExists() {
            when(redisTemplate.hasKey("session:sid:active")).thenReturn(true);
            assertThat(service.isSessionActive("sid")).isTrue();
        }

        @Test
        @DisplayName("returns false when Redis key is missing (expired)")
        void returnsFalseWhenKeyMissing() {
            when(redisTemplate.hasKey("session:sid:active")).thenReturn(false);
            assertThat(service.isSessionActive("sid")).isFalse();
        }
    }
}