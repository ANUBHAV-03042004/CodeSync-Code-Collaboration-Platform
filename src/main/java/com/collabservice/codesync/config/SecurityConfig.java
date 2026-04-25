package com.collabservice.codesync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Collab-service is behind the API Gateway which validates JWT and injects X-User-Id.
 * This service trusts that header — it does not re-validate JWT itself.
 * CORS is handled by CorsConfig.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                         "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/ws/collab/**").permitAll()   // WebSocket handshake
                        .anyRequest().permitAll()
                )
                .build();
    }
}
