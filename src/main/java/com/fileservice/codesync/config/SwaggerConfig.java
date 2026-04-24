package com.fileservice.codesync.config;

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

/**
 * OpenAPI / Swagger configuration.
 *
 * Server URL strategy (two layers):
 *  1. server.forward-headers-strategy=NATIVE in application.properties tells
 *     Tomcat to honour X-Forwarded-Proto/Host from OpenShift HAProxy so
 *     SpringDoc auto-detects https:// at runtime.
 *  2. SERVER_URL env var is registered explicitly here as a fallback so the
 *     correct URL appears in the Swagger UI dropdown even when the proxy
 *     header is absent (e.g. local dev or Swagger static export).
 */
@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    // Injected from OpenShift secret via application.properties ${SERVER_URL}
    @Value("${SERVER_URL:http://localhost:8083}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        List<Server> servers = new ArrayList<>();

        // Production / OpenShift URL (from secret)
        servers.add(new Server()
                .url(serverUrl)
                .description("Current deployment"));

        // Always include localhost for local dev convenience
        if (!serverUrl.contains("localhost")) {
            servers.add(new Server()
                    .url("http://localhost:8083")
                    .description("Local development"));
        }

        return new OpenAPI()
                .servers(servers)
                .info(new Info()
                        .title("CodeSync File Service API")
                        .description("File and folder management service for the CodeSync platform. " +
                                "Handles creation, editing, renaming, moving, soft-delete and " +
                                "restore of code files and folders within projects.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("CodeSync Team")
                                .email("dev@codesync.io"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token from auth-service. " +
                                        "Paste token here — userId is extracted automatically.")));
    }
}
