package com.finflow.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @Mock
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "mySecretKeyForTestingJwtTokenGenerationAndValidationBecauseItNeedsToBeLongEnough");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600000L); // 1 hour
    }

    @Test
    void generateToken_ShouldReturnValidToken() {
        when(userDetails.getUsername()).thenReturn("test@example.com");

        String token = jwtUtil.generateToken(userDetails, 1L, List.of("ROLE_USER"));

        assertNotNull(token);
        assertEquals("test@example.com", jwtUtil.extractUsername(token));
    }

    @Test
    void validateToken_WithValidToken_ShouldReturnTrue() {
        when(userDetails.getUsername()).thenReturn("test@example.com");
        String token = jwtUtil.generateToken(userDetails, 1L, List.of("ROLE_USER"));

        assertTrue(jwtUtil.validateToken(token, userDetails));
    }

    @Test
    void validateToken_WithInvalidUsername_ShouldReturnFalse() {
        when(userDetails.getUsername()).thenReturn("test@example.com");
        String token = jwtUtil.generateToken(userDetails, 1L, List.of("ROLE_USER"));

        when(userDetails.getUsername()).thenReturn("other@example.com");
        assertFalse(jwtUtil.validateToken(token, userDetails));
    }

    @Test
    void extractAllClaims_ShouldReturnCorrectClaims() {
        when(userDetails.getUsername()).thenReturn("test@example.com");
        String token = jwtUtil.generateToken(userDetails, 1L, List.of("ROLE_USER", "ROLE_ADMIN"));

        Claims claims = jwtUtil.getAllClaims(token);

        assertEquals("test@example.com", claims.getSubject());
        assertEquals("1", claims.get("userId"));
        assertEquals("ROLE_USER,ROLE_ADMIN", claims.get("roles"));
    }

    @Test
    void tokenExpired_ShouldThrowExpiredJwtException() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", -1000L); // Past expiration
        when(userDetails.getUsername()).thenReturn("test@example.com");
        
        String token = jwtUtil.generateToken(userDetails, 1L, List.of("ROLE_USER"));

        assertThrows(ExpiredJwtException.class, () -> jwtUtil.validateToken(token, userDetails));
    }
    @Test
    void validateToken_MalformedToken_ShouldThrowException() {
        assertThrows(io.jsonwebtoken.MalformedJwtException.class, 
                     () -> jwtUtil.validateToken("invalid.token.string", userDetails));
    }

    @Test
    void validateToken_InvalidSignature_ShouldThrowException() {
        String token = jwtUtil.generateToken(userDetails, 1L, java.util.List.of("ROLE_USER"));
        String tamperedToken = token.substring(0, token.length() - 2) + "aa";
        
        assertThrows(io.jsonwebtoken.security.SignatureException.class, 
                     () -> jwtUtil.validateToken(tamperedToken, userDetails));
    }
}
