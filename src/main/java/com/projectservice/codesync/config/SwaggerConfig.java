package com.projectservice.codesync.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc/Swagger configuration for Project Service.
 *
 * No hardcoded server URL needed — server.forward-headers-strategy=framework
 * in application.properties makes Spring honour the X-Forwarded-Proto header
 * that OpenShift's ingress injects, so SpringDoc auto-detects https:// correctly.
 */
@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeSync Project Service API")
                        .description("Project management service for the CodeSync platform. " +
                                "Handles project creation, forking, starring, member management, " +
                                "and project discovery. All endpoints require X-User-Id header " +
                                "injected by the API Gateway JWT filter.")
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
                                .description("JWT access token from auth-service login/register. " +
                                        "Paste token here — the API Gateway injects X-User-Id automatically.")));
    }
}
