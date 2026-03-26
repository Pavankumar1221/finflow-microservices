package com.finflow.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//  This class is used to create swagger UI(API documentation)
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authServiceOpenAPI() {
        // Force Swagger UI to send calls through the API Gateway, not directly
        // to this service. All auth endpoints are at /auth/** on the gateway.
        Server gatewayServer = new Server()
                .url("http://localhost:7003")
                .description("API Gateway (Production Entry Point)");

        return new OpenAPI()
                .addServersItem(gatewayServer)
                .info(new Info()
                        .title("FinFlow - Auth Service API")
                        .version("1.0.0")
                        .description("Authentication, User Registration and JWT Management"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Auth"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Auth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
