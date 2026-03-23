package com.finflow.gateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

/**
 * Determines which routes are OPEN (no JWT required).
 * All other paths are automatically SECURED by AuthenticationFilter.
 */
@Component
public class RouteValidator {

    /** Paths that do NOT require a JWT. Checked using String.contains(). */
    public static final List<String> OPEN_API_ENDPOINTS = List.of(
            // Auth endpoints
            "/auth/register",
            "/auth/login",

            // Per-service Swagger UIs routed through gateway
            "/auth-service/swagger-ui",
            "/auth-service/v3/api-docs",
            "/application-service/swagger-ui",
            "/application-service/v3/api-docs",
            "/document-service/swagger-ui",
            "/document-service/v3/api-docs",
            "/admin-service/swagger-ui",
            "/admin-service/v3/api-docs",

            // Gateway's own aggregated Swagger
            "/swagger-ui",
            "/v3/api-docs",
            "/webjars",                  // Swagger static resources

            // Actuator health checks
            "/actuator"
    );

    /** Returns true if the request path requires JWT validation. */
    public Predicate<ServerHttpRequest> isSecured =
            request -> OPEN_API_ENDPOINTS.stream()
                    .noneMatch(uri -> request.getURI().getPath().contains(uri));
}
