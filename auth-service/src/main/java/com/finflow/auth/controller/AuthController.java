package com.finflow.auth.controller;

import com.finflow.auth.dto.AuthResponse;
import com.finflow.auth.dto.ChangePasswordRequest;
import com.finflow.auth.dto.LoginRequest;
import com.finflow.auth.dto.RegisterRequest;
import com.finflow.auth.dto.TokenStatusRequest;
import com.finflow.auth.dto.UserResponse;
import com.finflow.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth Controller", description = "User registration, login, and JWT management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and get JWT token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/validate")
    @Operation(summary = "Validate JWT token (used by API Gateway)")
    public ResponseEntity<Map<String, Object>> validate(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(Map.of("valid", true, "message", "Token is valid"));
    }

    @GetMapping("/my")
    @Operation(summary = "Get the profile of the currently authenticated user")
    public ResponseEntity<UserResponse> getMyProfile(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(authService.getMyProfile(userId));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout the current user by invalidating the current JWT")
    public ResponseEntity<Map<String, Object>> logout(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(authService.logout(authHeader));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password for the currently authenticated user")
    public ResponseEntity<Map<String, Object>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(authService.changePassword(userId, request));
    }

    @GetMapping("/internal/users")
    @Operation(summary = "Internal: Get all users", hidden = true)
    public ResponseEntity<List<Map<String, Object>>> getAllUsersInternal(
            @RequestHeader(value = "X-Internal-Call", required = false) String internalCall,
            @RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost) {
        if (forwardedHost != null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (!"admin-service".equals(internalCall)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(authService.getAllUsersInternal());
    }

    @GetMapping("/internal/users/{id}")
    @Operation(summary = "Internal: Get one user", hidden = true)
    public ResponseEntity<Map<String, Object>> getUserInternal(
            @PathVariable Long id,
            @RequestHeader(value = "X-Internal-Call", required = false) String internalCall,
            @RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost) {
        if (forwardedHost != null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (!"admin-service".equals(internalCall) && !"document-service".equals(internalCall)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(authService.getUserInternal(id));
    }

    @PostMapping("/internal/token/blacklisted")
    @Operation(summary = "Internal: Check whether a token is blacklisted", hidden = true)
    public ResponseEntity<Map<String, Object>> isTokenBlacklisted(
            @Valid @RequestBody TokenStatusRequest request,
            @RequestHeader(value = "X-Internal-Call", required = false) String internalCall,
            @RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost) {
        if (forwardedHost != null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (!"api-gateway".equals(internalCall)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(authService.isTokenBlacklisted(request));
    }

    @PutMapping("/internal/users/{id}")
    @Operation(summary = "Internal: Update user role/status", hidden = true)
    public ResponseEntity<Map<String, Object>> updateUserInternal(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updateRequest,
            @RequestHeader(value = "X-Internal-Call", required = false) String internalCall,
            @RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost) {
        if (forwardedHost != null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (!"admin-service".equals(internalCall)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(authService.updateUserInternal(id, updateRequest));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "Auth Service is UP", "service", "auth-service"));
    }
}
