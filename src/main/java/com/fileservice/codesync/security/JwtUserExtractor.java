package com.fileservice.codesync.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * Extracts userId from:
 *  1. X-User-Id header      — injected by API Gateway after JWT validation
 *  2. Authorization: Bearer  — fallback for direct Swagger / dev access
 *
 * FIX: JJWT on Java 21 deserializes JSON numeric claims as Integer when the
 * value fits in 32 bits (userId=1 → Integer, not Long). Using Number.class
 * and calling .longValue() handles both Integer and Long safely.
 */
@Component
public class JwtUserExtractor {

    private static final Logger log = LoggerFactory.getLogger(JwtUserExtractor.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    public Long resolveUserId(String xUserId, String authorization) {

        // Path 1: X-User-Id injected by Gateway (fastest path)
        if (xUserId != null && !xUserId.isBlank()) {
            try {
                return Long.parseLong(xUserId.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid X-User-Id header value: '{}'", xUserId);
            }
        }

        // Path 2: parse Bearer token directly (Swagger UI / direct calls)
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7).trim();
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(signingKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // FIX: get as Number to handle both Integer and Long
                // JJWT deserializes small numbers as Integer on Java 21
                Object raw = claims.get("userId");
                if (raw instanceof Number n) {
                    return n.longValue();
                }

                log.warn("JWT 'userId' claim is missing or not a number — actual type: {}",
                        raw != null ? raw.getClass().getSimpleName() : "null");

            } catch (JwtException e) {
                log.warn("Failed to parse JWT in file-service: {}", e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error parsing JWT", e);
            }
        }

        return null;
    }

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
