package com.commentservice.codesync.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${SERVER_URL:http://localhost:8086}")
    private String serverUrl;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // OpenShift cluster routes
        config.addAllowedOriginPattern("https://*.apps.rm1.0a51.p1.openshiftapps.com");

        // Local development
        config.addAllowedOriginPattern("http://localhost:[*]");
        config.addAllowedOriginPattern("http://127.0.0.1:[*]");

        // Service's own public URL (for Swagger UI try-it-out)
        if (serverUrl != null && !serverUrl.isBlank() && !serverUrl.startsWith("${")) {
            String origin = serverUrl.strip().replaceAll("/+$", "");
            if (origin.startsWith("http://")
                    && !origin.contains("localhost")
                    && !origin.contains("127.0.0.1")) {
                origin = "https://" + origin.substring(7);
            }
            config.addAllowedOrigin(origin);
        }

        // Extra origins from env var (comma-separated)
        String extra = System.getenv("ALLOWED_ORIGINS");
        if (extra != null && !extra.isBlank()) {
            for (String o : extra.split(",")) {
                String t = o.trim();
                if (!t.isEmpty()) config.addAllowedOriginPattern(t);
            }
        }

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
