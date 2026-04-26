package com.executionservice.codesync.config;

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
     * FIX — CORS wildcard "*" is incompatible with allowCredentials(true).
     *
     * The original code had:
     *   config.setAllowedOriginPatterns(List.of("*"));
     *   config.setAllowCredentials(true);
     *
     * RFC 6454 + the Fetch spec forbid credentials on wildcard origins.
     * Browsers reject such responses with:
     *   "The value of the 'Access-Control-Allow-Origin' header must not be
     *    the wildcard '*' when the request's credentials mode is 'include'"
     *
     * Fix: replace the single "*" with specific allowed origin patterns:
     *  - All OpenShift routes on this cluster  (covers Swagger UI + API gateway)
     *  - localhost (all ports) for local dev
     *  - The service's own public URL (so Swagger try-it-out same-origin calls pass)
     *  - Any extra origins from the ALLOWED_ORIGINS env var (comma-separated)
     */
    @Value("${SERVER_URL:http://localhost:8085}")
    private String serverUrl;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // All OpenShift routes on this cluster
        config.addAllowedOriginPattern("https://*.apps.rm1.0a51.p1.openshiftapps.com");

        // Local development
        config.addAllowedOriginPattern("http://localhost:[*]");
        config.addAllowedOriginPattern("http://127.0.0.1:[*]");

        // Service's own public URL (normalised to https for non-localhost)
        if (serverUrl != null && !serverUrl.isBlank() && !serverUrl.startsWith("${")) {
            String origin = serverUrl.strip().replaceAll("/+$", "");
            if (origin.startsWith("http://")
                    && !origin.contains("localhost")
                    && !origin.contains("127.0.0.1")) {
                origin = "https://" + origin.substring(7);
            }
            config.addAllowedOrigin(origin);
        }

        // Additional origins via env var (comma-separated)
        String extra = System.getenv("ALLOWED_ORIGINS");
        if (extra != null && !extra.isBlank()) {
            for (String o : extra.split(",")) {
                String t = o.trim();
                if (!t.isEmpty()) config.addAllowedOriginPattern(t);
            }
        }

        // allowCredentials(true) requires specific origins — wildcard "*" is illegal here
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
