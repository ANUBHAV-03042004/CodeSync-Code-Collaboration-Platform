package com.authservice.codesync.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * Set SERVER_URL in your OpenShift secret to your auth-service route URL.
     * e.g. https://auth-service-mr-aksthegreat030420-dev.apps.rm1.0a51.p1.openshiftapps.com
     * When set, it appears first in the Swagger UI server dropdown so "Try it out" works.
     */
    @Value("${SERVER_URL:}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        List<Server> servers = new ArrayList<>();

        // OpenShift / production URL — listed first so Swagger UI defaults to it
        if (serverUrl != null && !serverUrl.isBlank()) {
            servers.add(new Server().url(serverUrl).description("Current deployment"));
        }

        // Always include localhost for local development
        servers.add(new Server().url("http://localhost:8081").description("Local development"));

        return new OpenAPI()
                .info(new Info()
                        .title("CodeSync Auth Service API")
                        .description("Authentication & User Management service for the CodeSync platform. " +
                                "Provides JWT-based login, registration, OAuth2 (GitHub/Google), " +
                                "profile management, and admin user operations.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("CodeSync Team")
                                .email("dev@codesync.io"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(servers)
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste your JWT access token here (without 'Bearer ' prefix)")));
    }
}
