package com.projectservice.codesync.security;

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
 * Extracts the userId claim from a JWT Bearer token.
 *
 * Used as a fallback when the request arrives directly at project-service
 * (e.g. from Swagger UI) instead of through the API Gateway, which normally
 * injects the X-User-Id header after validating the JWT.
 */
@Component
public class JwtUserExtractor {

    private static final Logger log = LoggerFactory.getLogger(JwtUserExtractor.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Returns userId from the X-User-Id header if present, otherwise parses
     * the Authorization: Bearer <token> header and extracts the userId claim.
     *
     * @param xUserId       value of X-User-Id header (may be null)
     * @param authorization value of Authorization header (may be null)
     * @return userId or null if neither header is usable
     */
    public Long resolveUserId(String xUserId, String authorization) {
        // 1. Gateway path: X-User-Id already injected
        if (xUserId != null && !xUserId.isBlank()) {
            try {
                return Long.parseLong(xUserId);
            } catch (NumberFormatException e) {
                log.warn("Invalid X-User-Id header value: {}", xUserId);
            }
        }

        // 2. Direct path: extract from JWT
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(signingKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                Number userId = claims.get("userId", Number.class);
                if (userId != null) {
                    return userId.longValue();
                }
                log.warn("JWT has no userId claim");
            } catch (JwtException e) {
                log.warn("Failed to parse JWT for userId extraction: {}", e.getMessage());
            }
        }

        return null;
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }
}
