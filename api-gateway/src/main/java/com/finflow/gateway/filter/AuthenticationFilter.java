package com.finflow.gateway.filter;

import com.finflow.gateway.config.RoleBasedAccessConfig;
import com.finflow.gateway.service.AuthTokenStatusService;
import com.finflow.gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final JwtUtil jwtUtil;
    private final RouteValidator routeValidator;
    private final RoleBasedAccessConfig rbacConfig;
    private final AuthTokenStatusService authTokenStatusService;

    @Autowired
    public AuthenticationFilter(JwtUtil jwtUtil,
                                RouteValidator routeValidator,
                                RoleBasedAccessConfig rbacConfig,
                                AuthTokenStatusService authTokenStatusService) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
        this.routeValidator = routeValidator;
        this.rbacConfig = rbacConfig;
        this.authTokenStatusService = authTokenStatusService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            if (!routeValidator.isSecured.test(request)) {
                log.debug("Public endpoint, skipping auth: {}", path);
                return chain.filter(exchange);
            }

            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                log.warn("[401] No Authorization header -> {}", path);
                return onError(exchange, "Authorization header is missing", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("[401] Malformed Authorization header -> {}", path);
                return onError(exchange, "Authorization header must be 'Bearer <token>'", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7).trim();

            if (!jwtUtil.validateToken(token)) {
                log.warn("[401] Invalid or expired JWT -> {}", path);
                return onError(exchange, "Invalid or expired JWT token", HttpStatus.UNAUTHORIZED);
            }

            return authTokenStatusService.isBlacklisted(token)
                    .flatMap(blacklisted -> {
                        if (blacklisted) {
                            log.warn("[401] Blacklisted JWT -> {}", path);
                            return onError(exchange, "JWT token has been logged out", HttpStatus.UNAUTHORIZED);
                        }

                        String userId = jwtUtil.getUserId(token);
                        String roles = jwtUtil.getRoles(token);
                        String email = jwtUtil.getEmail(token);

                        if (!rbacConfig.isAuthorized(path, roles)) {
                            log.warn("[403] Access denied - userId={}, roles={}, path={}", userId, roles, path);
                            return onError(exchange,
                                    "Access denied: your role (" + roles + ") is not permitted to access " + path,
                                    HttpStatus.FORBIDDEN);
                        }

                        log.debug("[OK] userId={}, roles={}, path={}", userId, roles, path);

                        ServerHttpRequest mutatedRequest = request.mutate()
                                .headers(h -> {
                                    h.remove("X-User-Id");
                                    h.remove("X-User-Roles");
                                    h.remove("X-User-Email");
                                })
                                .header("X-User-Id", userId != null ? userId : "")
                                .header("X-User-Roles", roles != null ? roles : "")
                                .header("X-User-Email", email != null ? email : "")
                                .build();

                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    });
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        log.warn("Gateway security error [{}]: {}", status.value(), message);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("X-Auth-Error", message);
        return response.setComplete();
    }

    public static class Config {}
}
