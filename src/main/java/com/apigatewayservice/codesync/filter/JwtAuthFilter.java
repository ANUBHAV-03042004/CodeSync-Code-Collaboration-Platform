package com.apigatewayservice.codesync.filter;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;

/**
 * Spring Cloud Gateway filter that validates the JWT on every
 * route that declares `- name: JwtAuthFilter`.
 *
 * On success:  forwards the request, injecting X-User-Id, X-User-Role,
 *              and X-User-Email headers so downstream services can trust
 *              the caller identity without re-validating the token.
 * On failure:  returns 401 immediately, never forwarding to downstream.
 *
 * FIXES vs original:
 *  1. Replaced @Slf4j (Lombok) with an explicit SLF4J logger declaration.
 *     Root cause: Lombok annotation processor was not registered in
 *     maven-compiler-plugin annotationProcessorPaths, so @Slf4j was silently
 *     ignored. Explicit logger is IDE-agnostic — no Lombok plugin needed.
 *
 *  2. Fixed claims.get("userId", Long.class) ClassCastException.
 *     JJWT deserialises numeric JSON claims as Integer when the value fits
 *     in 32 bits. Calling .get(..., Long.class) throws at runtime.
 *     Fix: read as Number then call .longValue().
 */
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    // FIX 1: explicit SLF4J logger — resolves "log cannot be resolved" in all IDEs
    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    public JwtAuthFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().toString();

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Missing or malformed Authorization header for path: {}", path);
                return onUnauthorized(exchange, "Missing or malformed Authorization header");
            }

            String token = authHeader.substring(7);

            try {
                Claims claims = parseToken(token);

                // FIX 2: JJWT returns Integer for small numbers, not Long.
                // Read as Number and convert to avoid ClassCastException.
                Number userIdNumber = claims.get("userId", Number.class);
                if (userIdNumber == null) {
                    log.warn("JWT missing 'userId' claim for path: {}", path);
                    return onUnauthorized(exchange, "Token missing required claims");
                }
                String userId    = String.valueOf(userIdNumber.longValue());
                String userRole  = claims.get("role", String.class);
                String userEmail = claims.getSubject();

                ServerHttpRequest enriched = request.mutate()
                        .header("X-User-Id",    userId)
                        .header("X-User-Role",  userRole  != null ? userRole  : "")
                        .header("X-User-Email", userEmail != null ? userEmail : "")
                        .build();

                log.debug("JWT valid — userId={} role={} path={}", userId, userRole, path);
                return chain.filter(exchange.mutate().request(enriched).build());

            } catch (ExpiredJwtException e) {
                log.warn("JWT expired for path: {} — {}", path, e.getMessage());
                return onUnauthorized(exchange, "Token has expired");
            } catch (MalformedJwtException e) {
                log.warn("Malformed JWT for path: {} — {}", path, e.getMessage());
                return onUnauthorized(exchange, "Malformed token");
            } catch (JwtException e) {
                log.warn("Invalid JWT for path: {} — {}", path, e.getMessage());
                return onUnauthorized(exchange, "Invalid token");
            }
        };
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Mono<Void> onUnauthorized(ServerWebExchange exchange, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("X-Auth-Error", reason);
        return response.setComplete();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public static class Config {
        // No per-route config properties needed for this filter
    }
}
