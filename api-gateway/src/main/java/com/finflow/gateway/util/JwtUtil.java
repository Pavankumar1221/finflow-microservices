package com.finflow.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * Utility for JWT validation at the API Gateway.
 * Uses the SAME secret as auth-service so it can verify tokens
 * without making any network calls.
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    // ── Key ───────────────────────────────────────────────────────────────────

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // ── Claims & Extraction ───────────────────────────────────────────────────

    public Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String getUserId(String token) {
        Object userId = getAllClaimsFromToken(token).get("userId");
        return userId != null ? userId.toString() : null;
    }

    public String getRoles(String token) {
        Object roles = getAllClaimsFromToken(token).get("roles");
        return roles != null ? roles.toString() : null;
    }

    public String getEmail(String token) {
        return getAllClaimsFromToken(token).getSubject();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Returns true if the token is structurally valid, correctly signed,
     * and NOT expired.
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.warn("Malformed JWT: {}", ex.getMessage());
        } catch (SignatureException ex) {
            log.warn("Invalid JWT signature: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.warn("Unsupported JWT: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("Empty JWT claims: {}", ex.getMessage());
        }
        return false;
    }
}
