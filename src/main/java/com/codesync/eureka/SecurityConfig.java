package com.codesync.eureka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Permit all access to the Eureka dashboard and /eureka/** endpoints.
     * Without this, Spring Security blocks the dashboard → you get a 404 or 403.
     *
     * For production, replace permitAll() with authenticated() and
     * configure credentials in each client's eureka.client.service-url.defaultZone.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF so Eureka clients (REST) can POST /eureka/apps/**
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/eureka/**")
            )
            .authorizeHttpRequests(auth -> auth
                // Eureka dashboard (the web UI at http://localhost:8761)
                .requestMatchers("/").permitAll()
                // Eureka REST API used by all microservice clients
                .requestMatchers("/eureka/**").permitAll()
                // Actuator health/info for Docker/Replit health checks
                .requestMatchers("/actuator/**").permitAll()
                // Everything else requires login
                .anyRequest().authenticated()
            )
            // Enable HTTP Basic so microservices can authenticate when registering
            .httpBasic(httpBasic -> {});

        return http.build();
    }
}
