package com.apigatewayservice.codesync;

import com.apigatewayservice.codesync.filter.JwtAuthFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for JwtAuthFilter — NO Spring context loaded at all.
 *
 * WHY no @SpringBootTest:
 *   Spring Cloud Gateway's GatewayAutoConfiguration requires a full reactive
 *   web environment (ServerProperties, Netty, etc.).
 *   - webEnvironment=NONE strips those beans → "ServerProperties not found"
 *   - webEnvironment=MOCK/RANDOM_PORT tries to start Netty → needs Eureka
 *
 *   Since JwtAuthFilter has zero dependencies on routing, Netty, or Eureka,
 *   we can instantiate it directly, inject the secret via ReflectionTestUtils,
 *   and test it in complete isolation. Faster, simpler, no Spring overhead.
 */
class ApiGatewayServiceCodeSyncApplicationTests {

    // Same Base64 secret used by JwtAuthFilter in tests — 64 bytes (512-bit)
    private static final String TEST_SECRET =
        "dGVzdFNlY3JldEtleUZvckp3dFRva2VuVmFsaWRhdGlvbkluVW5pdFRlc3RzMTIzNDU2Nzg=";

    private JwtAuthFilter jwtAuthFilter;
    private GatewayFilter filter;
    private GatewayFilterChain mockChain;

    @BeforeEach
    void setUp() {
        // Instantiate the filter directly — no Spring context needed
        jwtAuthFilter = new JwtAuthFilter();

        // Inject @Value("${jwt.secret}") field using ReflectionTestUtils
        // (Spring's test utility for setting private fields without a context)
        ReflectionTestUtils.setField(jwtAuthFilter, "jwtSecret", TEST_SECRET);

        filter = jwtAuthFilter.apply(new JwtAuthFilter.Config());

        // Mock the downstream chain: just completes successfully
        mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    // ── Test 1 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid JWT passes through — response is not 401")
    void validToken_shouldPassThroughAndNotReturn401() {
        String token = buildToken(42L, "ROLE_USER", "alice@example.com",
                System.currentTimeMillis() + 60_000);

        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .as("Valid JWT must not produce a 401")
                .isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Test 2 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Missing Authorization header returns 401")
    void missingAuthHeader_shouldReturn401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/projects").build());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Auth-Error"))
                .contains("Missing or malformed");
    }

    // ── Test 3 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Token without 'Bearer ' prefix returns 401")
    void tokenWithoutBearerPrefix_shouldReturn401() {
        String token = buildToken(1L, "ROLE_USER", "bob@example.com",
                System.currentTimeMillis() + 60_000);

        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/files")
                .header(HttpHeaders.AUTHORIZATION, token) // no "Bearer " prefix
                .build());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Test 4 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Expired JWT returns 401 with 'Token has expired' error header")
    void expiredToken_shouldReturn401WithExpiredMessage() {
        String token = buildToken(7L, "ROLE_ADMIN", "charlie@example.com",
                System.currentTimeMillis() - 10_000); // expired 10 seconds ago

        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/executions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Auth-Error"))
                .isEqualTo("Token has expired");
    }

    // ── Test 5 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Tampered JWT signature returns 401")
    void tamperedToken_shouldReturn401() {
        String validToken = buildToken(99L, "ROLE_USER", "eve@example.com",
                System.currentTimeMillis() + 60_000);

        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/comments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken + "CORRUPTED")
                .build());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Auth-Error"))
                .isIn("Invalid token", "Malformed token");
    }

    // ── Test 6 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("JwtAuthFilter instantiates and Config class is accessible")
    void filterInstantiatesAndConfigIsAccessible() {
        assertThat(jwtAuthFilter).isNotNull();
        assertThat(new JwtAuthFilter.Config()).isNotNull();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private String buildToken(long userId, String role, String subject, long expiryEpoch) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        return Jwts.builder()
                .subject(subject)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(expiryEpoch))
                .signWith(key)
                .compact();
    }
}
