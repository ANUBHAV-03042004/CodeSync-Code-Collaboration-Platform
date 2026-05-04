package com.authservice.codesync.security;

import com.authservice.codesync.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-request JWT filter with three gate checks:
 *
 * Gate 1 — Is the token cryptographically valid and not expired?
 * Gate 2 — Is the token on the blacklist? (i.e. user has logged out)
 * Gate 3 — Is the user still within the inactivity window?
 *
 * If all three pass, the security context is populated and the activity
 * timestamp is refreshed (sliding window).
 *
 * On failure: returns 401 with a specific X-Auth-Reason header so the
 * Angular frontend can distinguish "expired" from "inactive" from "logged out".
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider      jwtTokenProvider;
    private final UserDetailsService    userDetailsService;
    private final TokenBlacklistService blacklistService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   UserDetailsService userDetailsService,
                                   TokenBlacklistService blacklistService) {
        this.jwtTokenProvider  = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.blacklistService  = blacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token)) {

            // ── Gate 1: cryptographic validity + expiry ───────────────────────
            if (!jwtTokenProvider.validateToken(token)) {
                sendUnauthorized(response, "TOKEN_EXPIRED",
                        "Access token is invalid or expired — please login again");
                return;
            }

            // ── Gate 2: explicit logout / blacklist check ─────────────────────
            if (blacklistService.isBlacklisted(token)) {
                log.warn("Rejected blacklisted token for request: {}", request.getRequestURI());
                sendUnauthorized(response, "TOKEN_REVOKED",
                        "Token has been revoked — please login again");
                return;
            }

            String username = jwtTokenProvider.extractUsername(token);
            Long   userId   = jwtTokenProvider.extractUserId(token);

            // ── Gate 3: inactivity window check ──────────────────────────────
            // Only reject if the key EXISTS but has expired (i.e. user was active
            // before but has now gone idle). A missing key means the user just logged
            // in — we seed it below. We detect "was active, now idle" by checking
            // whether the key existed at some point via a separate flag, but the
            // simplest safe approach is: treat a missing key as "just logged in"
            // and let it be seeded by recordActivity(). A missing key after a clear
            // logout is prevented because logout calls clearActivity() AND blacklists
            // the token — Gate 2 catches those tokens first.
            if (userId != null && blacklistService.hasActivityKeyEverBeenSet(userId)
                    && !blacklistService.isUserActive(userId)) {
                log.info("User {} session expired due to inactivity", userId);
                sendUnauthorized(response, "SESSION_INACTIVE",
                        "Session expired due to inactivity — please login again");
                return;
            }

            // ── All gates passed: populate security context ───────────────────
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                // ── Seed / slide the inactivity window forward ────────────────
                if (userId != null) {
                    blacklistService.recordActivity(userId);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    /**
     * Write a structured 401 JSON response with a machine-readable reason header.
     * Angular reads X-Auth-Reason to decide the redirect message.
     */
    private void sendUnauthorized(HttpServletResponse response,
                                   String reason,
                                   String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setHeader("X-Auth-Reason", reason);
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\","
                + "\"reason\":\"" + reason + "\","
                + "\"message\":\"" + message + "\"}"
        );
    }
}
