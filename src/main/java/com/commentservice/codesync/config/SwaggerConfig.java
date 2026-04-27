package com.commentservice.codesync.config;

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

    @Value("${SERVER_URL:http://localhost:8086}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        String url = normalise(serverUrl);

        List<Server> servers = new ArrayList<>();
        servers.add(new Server().url(url).description("Current deployment"));
        if (!url.contains("localhost")) {
            servers.add(new Server().url("http://localhost:8086").description("Local development"));
        }

        return new OpenAPI()
                .servers(servers)
                .info(new Info()
                        .title("CodeSync Comment Service API")
                        .description("Comment and annotation service for the CodeSync platform. " +
                                "Supports inline code comments, @mentions, threaded replies, and resolution tracking.")
                        .version("v1.0.0")
                        .contact(new Contact().name("CodeSync Team").email("dev@codesync.io"))
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
                                .description("JWT access token from auth-service")));
    }

    private String normalise(String url) {
        if (url == null || url.isBlank() || url.startsWith("${")) {
            return "http://localhost:8086";
        }
        String u = url.strip().replaceAll("/+$", "");
        if (u.startsWith("http://") && !u.contains("localhost") && !u.contains("127.0.0.1")) {
            u = "https://" + u.substring(7);
        }
        return u;
    }
}
