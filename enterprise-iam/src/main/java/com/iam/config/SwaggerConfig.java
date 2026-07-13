package com.iam.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server")
                ))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Provide a valid JWT access token in the Authorization header. "
                                        + "Format: Bearer <token>")
                        )
                );
    }

    private Info apiInfo() {
        return new Info()
                .title("Enterprise IAM Platform API")
                .description("Identity & Access Management REST API — provides authentication, "
                        + "authorization, user management, role-based access control, "
                        + "MFA, and token management capabilities.")
                .version("1.0.0")
                .contact(new Contact()
                        .name("Enterprise IAM Team")
                        .email("admin@enterprise-iam.com")
                        .url("https://enterprise-iam.com")
                );
    }
}
