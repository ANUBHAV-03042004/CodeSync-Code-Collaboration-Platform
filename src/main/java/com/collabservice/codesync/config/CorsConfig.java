package com.collabservice.codesync.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * SERVER_URL is the public URL of this service on OpenShift.
     * It is used to whitelist the Swagger UI origin so try-it-out
     * calls made FROM the Swagger UI page do not get CORS-blocked.
     *
     * Root cause of "Failed to fetch" in Swagger UI:
     *  - Swagger UI is served at https://collab-service-*.apps.../swagger-ui/index.html
     *  - When SwaggerConfig used an http:// server URL, the browser refused
     *    to make the AJAX call (mixed content block, treated as CORS failure).
     *  - Now SwaggerConfig normalises to https://, but we also need this origin
     *    whitelisted here so the preflight OPTIONS request succeeds.
     */
    @Value("${SERVER_URL:http://localhost:8084}")
    private String serverUrl;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // ── Always-allowed origins ────────────────────────────────────────────

        // All OpenShift routes on this cluster (covers Swagger UI + API gateway)
        config.addAllowedOriginPattern("https://*.apps.rm1.0a51.p1.openshiftapps.com");

        // Local development
        config.addAllowedOriginPattern("http://localhost:[*]");
        config.addAllowedOriginPattern("http://127.0.0.1:[*]");

        // The service's own public URL (covers Swagger UI same-origin try-it-out)
        if (serverUrl != null && !serverUrl.isBlank()) {
            String origin = serverUrl.strip().replaceAll("/+$", "");
            // normalise http → https for non-localhost (mirrors SwaggerConfig)
            if (origin.startsWith("http://")
                    && !origin.contains("localhost")
                    && !origin.contains("127.0.0.1")) {
                origin = "https://" + origin.substring(7);
            }
            config.addAllowedOrigin(origin);
        }

        // ── Additional origins via env var (comma-separated) ──────────────────
        String allowedOrigins = System.getenv("ALLOWED_ORIGINS");
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            for (String o : allowedOrigins.split(",")) {
                String trimmed = o.trim();
                if (!trimmed.isEmpty()) config.addAllowedOriginPattern(trimmed);
            }
        }

        // allowCredentials requires specific origins (no wildcard "*")
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "X-User-Id"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
