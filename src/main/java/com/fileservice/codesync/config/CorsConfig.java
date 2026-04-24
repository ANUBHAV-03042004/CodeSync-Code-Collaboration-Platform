package com.fileservice.codesync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Global CORS configuration for the File Service.
 *
 * Allows all origins by default so the API Gateway, Swagger UI, and the
 * Angular frontend (on any OpenShift route URL) can reach this service.
 *
 * In production, lock down allowedOriginPatterns by setting the
 * ALLOWED_ORIGINS env var in the OpenShift secret:
 *   e.g. https://your-frontend.apps.rm1.0a51.p1.openshiftapps.com
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        String allowedOrigins = System.getenv("ALLOWED_ORIGINS");
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            // Specific origins — can use allowCredentials=true
            for (String origin : allowedOrigins.split(",")) {
                config.addAllowedOriginPattern(origin.trim());
            }
            config.setAllowCredentials(true);
        } else {
            // Wildcard — allowCredentials must be false
            config.addAllowedOriginPattern("*");
            config.setAllowCredentials(false);
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "X-User-Id", "X-Auth-Error"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
