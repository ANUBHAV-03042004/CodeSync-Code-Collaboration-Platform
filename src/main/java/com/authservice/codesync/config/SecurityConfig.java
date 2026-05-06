package com.authservice.codesync.config;

import com.authservice.codesync.security.JwtAuthenticationFilter;
import com.authservice.codesync.security.OAuth2AuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;

    @Value("${app.frontend-url:https://yourscode.netlify.app}")
    private String frontendUrl;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          UserDetailsService userDetailsService,
                          OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler) {
        this.jwtAuthFilter        = jwtAuthFilter;
        this.userDetailsService   = userDetailsService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // FIX: OAuth2 login requires a server-side session to store the
                // authorization request state between the initial redirect and the
                // callback from Google/GitHub.
                // STATELESS destroys that state → _authorization_request_not_found_.
                // IF_REQUIRED creates a session only for OAuth2 flows; JWT API calls
                // send no cookies so they remain effectively stateless.
                // The session is stored in Redis (spring-session-data-redis) so it
                // survives across multiple pods and gateway round-trips.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/search",
                                "/api/v1/auth/validate",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/reset-password/validate",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/actuator/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/auth/admin/**").hasRole("ADMIN")
                        // GUEST users can access read-only endpoints; DEVELOPER and ADMIN inherit
                        .requestMatchers("/api/v1/auth/profile", "/api/v1/auth/search",
                                         "/api/v1/auth/session/status")
                                .hasAnyRole("GUEST", "DEVELOPER", "ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler((request, response, exception) -> {
                            String errorMsg = exception.getMessage() != null
                                    ? exception.getMessage().replaceAll("[^a-zA-Z0-9_]", "_")
                                    : "oauth_failed";
                            response.sendRedirect(frontendUrl + "/login?error=" + errorMsg);
                        })
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        String allowedOrigins = System.getenv("ALLOWED_ORIGINS");
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            config.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        } else {
            config.setAllowedOriginPatterns(List.of("*"));
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "X-Auth-Error"));
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            config.setAllowCredentials(true);
        }
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
