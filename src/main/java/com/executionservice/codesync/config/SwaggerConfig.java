package com.executionservice.codesync.config;

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
     * SERVER_URL is injected from the OpenShift secret / env var.
     *
     * FIX 1 — Server URL shows "/" in Swagger UI:
     *   springdoc.swagger-ui.servers[0].url=${SERVER_URL} in application.properties
     *   has NO fallback. When SERVER_URL is absent the property resolves to the
     *   literal string "${SERVER_URL}" and SpringDoc collapses it to "/".
     *   Solution: define the full URL exclusively here in SwaggerConfig where
     *   @Value provides a proper default. Remove the servers[] lines from
     *   application.properties so there is only one source of truth.
     *
     * FIX 2 — http→https normalisation:
     *   Swagger UI is served over HTTPS on OpenShift. If serverUrl is http://
     *   the browser blocks try-it-out as mixed content ("Failed to fetch").
     *   normalise() upgrades http:// → https:// for non-localhost hosts.
     */
    @Value("${SERVER_URL:http://localhost:8085}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        String url = normalise(serverUrl);

        List<Server> servers = new ArrayList<>();
        servers.add(new Server().url(url).description("Current deployment"));
        if (!url.contains("localhost")) {
            servers.add(new Server().url("http://localhost:8085").description("Local development"));
        }

        return new OpenAPI()
                .servers(servers)
                .info(new Info()
                        .title("CodeSync Execution Service API")
                        .description("Code execution service for the CodeSync platform. " +
                                "Submits jobs to the Piston sandbox, streams output via WebSocket, " +
                                "and stores results in MySQL.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("CodeSync Team")
                                .email("support@codesync.dev"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                // FIX 3 — Authorize button was missing:
                // addSecurityItem() adds the global lock icon + Authorize button.
                // Without it the bearerAuth scheme is defined in components but
                // never applied to the UI, so the button never appears.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token from auth-service. " +
                                        "Paste token here — X-User-Id is extracted automatically by the gateway.")));
    }

    /**
     * Ensure the server URL:
     *  1. Has no trailing slash (causes double-slash in generated curl commands)
     *  2. Uses https:// for any non-localhost host so Swagger UI try-it-out
     *     doesn't trigger a browser mixed-content block.
     */
    private String normalise(String url) {
        if (url == null || url.isBlank() || url.startsWith("${")) {
            return "http://localhost:8085";   // guard against unresolved placeholder
        }
        String u = url.strip().replaceAll("/+$", "");
        if (u.startsWith("http://") && !u.contains("localhost") && !u.contains("127.0.0.1")) {
            u = "https://" + u.substring(7);
        }
        return u;
    }
}
