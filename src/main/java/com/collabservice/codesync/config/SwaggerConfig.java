package com.collabservice.codesync.config;

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

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * SERVER_URL is injected from the OpenShift env var set via:
     *   oc set env deployment/collab-service SERVER_URL=https://collab-service-...
     *
     * FIX: default changed from http:// to https:// so that Swagger UI
     * (loaded over HTTPS) does not trigger a browser mixed-content block
     * when it tries to call the try-it-out endpoint.
     *
     * When running locally the URL will be http://localhost:8084 — override
     * with SERVER_URL env var or just use the local Swagger UI directly.
     */
    @Value("${SERVER_URL:http://localhost:8084}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        // Normalise: strip trailing slash, ensure HTTPS on non-localhost URLs
        String normalised = normalise(serverUrl);

        return new OpenAPI()
                .servers(List.of(
                        new Server().url(normalised).description("Current deployment"),
                        new Server().url("http://localhost:8084").description("Local development")
                ))
                .info(new Info()
                        .title("CodeSync Collab Service API")
                        .description("Real-time collaboration service. " +
                                "Manages sessions, participants, cursor positions via WebSocket/STOMP + Redis.")
                        .version("v1.0.0")
                        .contact(new Contact().name("CodeSync Team").email("dev@codesync.io"))
                        .license(new License().name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste your JWT access token (without 'Bearer ' prefix)")));
    }

    /**
     * Ensure the server URL:
     *  1. Has no trailing slash (causes double-slash in generated curl commands)
     *  2. Uses https:// when deployed on OpenShift (non-localhost)
     *     — avoids browser mixed-content block when Swagger UI is served over HTTPS
     */
    private String normalise(String url) {
        if (url == null || url.isBlank()) return "http://localhost:8084";

        String u = url.strip().replaceAll("/+$", "");   // strip trailing slashes

        // Upgrade http:// → https:// for any non-localhost host
        if (u.startsWith("http://") && !u.contains("localhost") && !u.contains("127.0.0.1")) {
            u = "https://" + u.substring(7);
        }
        return u;
    }
}
