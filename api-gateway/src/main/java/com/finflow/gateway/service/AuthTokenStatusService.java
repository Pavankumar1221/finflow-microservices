package com.finflow.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthTokenStatusService {

    private final WebClient.Builder webClientBuilder;

    public Mono<Boolean> isBlacklisted(String token) {
        return webClientBuilder.build()
                .post()
                .uri("lb://auth-service/auth/internal/token/blacklisted")
                .header("X-Internal-Call", "api-gateway")
                .bodyValue(Map.of("token", token))
                .retrieve()
                .bodyToMono(TokenStatusResponse.class)
                .map(TokenStatusResponse::isBlacklisted)
                .onErrorResume(ex -> {
                    log.warn("Could not check token blacklist status via auth-service: {}", ex.getMessage());
                    return Mono.just(false);
                });
    }

    private record TokenStatusResponse(boolean blacklisted) {
        boolean isBlacklisted() {
            return blacklisted;
        }
    }
}
