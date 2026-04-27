package com.versionservice.codesync.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Value("${SERVER_URL:https://version-service-mr-aksthegreat030420-dev.apps.rm1.0a51.p1.openshiftapps.com}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeSync Version Service API")
                        .description("Snapshot-based versioning service — create, diff, restore, branch and tag file snapshots.")
                        .version("v1.0.0")
                        .contact(new Contact().name("CodeSync Team").email("dev@codesync.io"))
                        .license(new License().name("MIT License").url("https://opensource.org/licenses/MIT")))
                .addServersItem(new Server().url(serverUrl).description("Current deployment"))
                .addServersItem(new Server().url("http://localhost:8086").description("Local development"))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
