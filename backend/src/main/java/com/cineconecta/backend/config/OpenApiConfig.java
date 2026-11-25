package com.cineconecta.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger UI at http://localhost:8080/swagger-ui.html. Log in with /api/auth/login or
 * /api/admin/auth/login, press "Authorize" and paste the token (without "Bearer").
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    OpenAPI cineConectaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CineConecta API")
                        .version("v1")
                        .description("Customer routes need a CLIENT token and, when they act on a "
                                + "profile, the X-Profile-Id header. /api/admin/** needs an ADMIN token."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
