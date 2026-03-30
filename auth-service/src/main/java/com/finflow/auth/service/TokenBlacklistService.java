package com.finflow.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TokenBlacklistService {

    private final Map<String, Instant> blacklistedTokens = new ConcurrentHashMap<>();

    public void blacklist(String token, Instant expiresAt) {
        cleanupExpiredTokens();
        if (token == null || token.isBlank() || expiresAt == null) {
            return;
        }
        blacklistedTokens.put(token, expiresAt);
        log.info("Token blacklisted until {}", expiresAt);
    }

    public boolean isBlacklisted(String token) {
        cleanupExpiredTokens();
        Instant expiresAt = blacklistedTokens.get(token);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            blacklistedTokens.remove(token);
            return false;
        }
        return true;
    }

    private void cleanupExpiredTokens() {
        Instant now = Instant.now();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isBefore(now));
    }
}
