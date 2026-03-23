package com.finflow.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * FIRST filter to execute on every request (highest precedence).
 *
 * Strips any X-User-* headers that a malicious client might try to inject
 * BEFORE the AuthenticationFilter runs.  This guarantees that downstream
 * services can only ever see headers that were set by our own gateway.
 */
@Slf4j
@Component
public class HeaderStripGlobalFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -100; // runs before everything else

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest strippedRequest = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Roles");
                    h.remove("X-User-Email");
                })
                .build();

        log.debug("HeaderStripGlobalFilter: stripped spoofable headers from {}",
                exchange.getRequest().getURI());

        return chain.filter(exchange.mutate().request(strippedRequest).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
