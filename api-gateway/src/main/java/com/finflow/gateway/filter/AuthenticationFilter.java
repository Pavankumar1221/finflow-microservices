package com.finflow.gateway.filter;

import com.finflow.gateway.config.RoleBasedAccessConfig;
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

/**
 * JWT Authentication + RBAC Filter for Spring Cloud Gateway.
 *
 * Full request lifecycle:
 *  1. Request arrives at gateway.
 *  2. RouteValidator checks if the path is PUBLIC → skip all checks.
 *  3. Verify "Authorization: Bearer <token>" header exists and is well-formed.
 *  4. Validate the JWT (signature, expiry) — returns 401 on failure.
 *  5. Extract userId, roles, email from the verified JWT claims.
 *  6. [RBAC] Check the user's roles against RoleBasedAccessConfig — returns 403 if denied.
 *  7. Mutate the request: strip client-supplied X-User-* headers, inject trusted ones from JWT.
 *  8. Forward the secured, enriched request to the downstream service.
 *
 *  401 Unauthorized → no valid JWT (authentication failed)
 *  403 Forbidden    → valid JWT but insufficient role (authorization failed)
 */
@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final JwtUtil jwtUtil;
    private final RouteValidator routeValidator;
    private final RoleBasedAccessConfig rbacConfig;

    @Autowired
    public AuthenticationFilter(JwtUtil jwtUtil,
                                RouteValidator routeValidator,
                                RoleBasedAccessConfig rbacConfig) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
        this.routeValidator = routeValidator;
        this.rbacConfig = rbacConfig;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            // ── 1. Skip public / open endpoints ───────────────────────────────
            if (!routeValidator.isSecured.test(request)) {
                log.debug("Public endpoint — skipping auth: {}", path);
                return chain.filter(exchange);
            }

            // ── 2. Authorization header must exist and be well-formed ──────────
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                log.warn("[401] No Authorization header → {}", path);
                return onError(exchange, "Authorization header is missing", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("[401] Malformed Authorization header → {}", path);
                return onError(exchange, "Authorization header must be 'Bearer <token>'", HttpStatus.UNAUTHORIZED);
            }

            // ── 3. Validate the JWT ────────────────────────────────────────────
            String token = authHeader.substring(7).trim();

            if (!jwtUtil.validateToken(token)) {
                log.warn("[401] Invalid or expired JWT → {}", path);
                return onError(exchange, "Invalid or expired JWT token", HttpStatus.UNAUTHORIZED);
            }

            // ── 4. Extract claims ──────────────────────────────────────────────
            String userId = jwtUtil.getUserId(token);
            String roles  = jwtUtil.getRoles(token);
            String email  = jwtUtil.getEmail(token);

            // ── 5. RBAC — check the user's role against the required role ──────
            if (!rbacConfig.isAuthorized(path, roles)) {
                log.warn("[403] Access denied — userId={}, roles={}, path={}", userId, roles, path);
                return onError(exchange,
                        "Access denied: your role (" + roles + ") is not permitted to access " + path,
                        HttpStatus.FORBIDDEN);
            }

            log.debug("[OK] userId={}, roles={}, path={}", userId, roles, path);

            // ── 6. Inject trusted headers, strip any client-supplied spoofed ones
            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(h -> {
                        h.remove("X-User-Id");
                        h.remove("X-User-Roles");
                        h.remove("X-User-Email");
                    })
                    .header("X-User-Id",    userId != null ? userId : "")
                    .header("X-User-Roles", roles  != null ? roles  : "")
                    .header("X-User-Email", email  != null ? email  : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        log.warn("Gateway security error [{}]: {}", status.value(), message);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("X-Auth-Error", message);
        return response.setComplete();
    }

    /** Required by Spring Cloud Gateway's filter factory mechanism. */
    public static class Config {}
}
