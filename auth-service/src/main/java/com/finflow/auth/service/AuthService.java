package com.finflow.auth.service;

import com.finflow.auth.dto.AuthResponse;
import com.finflow.auth.dto.ChangePasswordRequest;
import com.finflow.auth.dto.LoginRequest;
import com.finflow.auth.dto.RegisterRequest;
import com.finflow.auth.dto.TokenStatusRequest;
import com.finflow.auth.dto.UserResponse;
import com.finflow.auth.entity.Role;
import com.finflow.auth.entity.User;
import com.finflow.auth.repository.RoleRepository;
import com.finflow.auth.repository.UserRepository;
import com.finflow.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByMobileNumber(request.getMobileNumber())) {
            throw new RuntimeException("Mobile number already registered");
        }

        // Force default role for all external registrations organically to ROLE_APPLICANT
        Role role = roleRepository.findByRoleName("ROLE_APPLICANT")
                .orElseThrow(() -> new RuntimeException("Default applicant role not found in system schema."));

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .mobileNumber(request.getMobileNumber())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(User.UserStatus.ACTIVE)
                .build();
        user.getRoles().add(role);

        User saved = userRepository.save(user);
        log.info("User registered: {}", saved.getEmail());

        return toUserResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<String> roles = user.getRoles().stream()
                .map(Role::getRoleName)
                .collect(Collectors.toList());

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtUtil.generateToken(userDetails, user.getId(), roles);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .userId(user.getId())
                .email(user.getEmail())
                .roles(roles)
                .build();
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "users-cache", key = "#userId")
    public UserResponse getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        return getMyProfile(userId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllUsersInternal() {
        return userRepository.findAll().stream().map(user -> Map.<String, Object>of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "status", user.getStatus().name(),
                "roles", user.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList())
        )).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUserInternal(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));

        return Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "mobileNumber", user.getMobileNumber(),
                "status", user.getStatus().name(),
                "roles", user.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList())
        );
    }

    @org.springframework.cache.annotation.CacheEvict(value = "users-cache", key = "#id")
    public Map<String, Object> updateUserInternal(Long id, Map<String, Object> req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        if (req.containsKey("status")) {
            user.setStatus(User.UserStatus.valueOf((String) req.get("status")));
        }
        
        if (req.containsKey("role")) {
            String roleName = (String) req.get("role");
            Role role = roleRepository.findByRoleName(roleName)
                    .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
            user.getRoles().clear();
            user.getRoles().add(role);
        }
        
        User saved = userRepository.save(user);
        return Map.of(
                "id", saved.getId(),
                "status", saved.getStatus().name(),
                "roles", saved.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList())
        );
    }

    @org.springframework.cache.annotation.CacheEvict(value = "users-cache", key = "#userId")
    public Map<String, Object> changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Old password is incorrect");
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new RuntimeException("New password must be different from old password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for user {}", user.getEmail());
        return Map.of("message", "Password changed successfully");
    }

    public Map<String, Object> logout(String authHeader) {
        String token = extractBearerToken(authHeader);
        tokenBlacklistService.blacklist(token, jwtUtil.extractExpiration(token));
        return Map.of("message", "Logged out successfully");
    }

    public Map<String, Object> isTokenBlacklisted(TokenStatusRequest request) {
        boolean blacklisted = tokenBlacklistService.isBlacklisted(request.getToken());
        return Map.of("blacklisted", blacklisted);
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization header must be 'Bearer <token>'");
        }
        return authHeader.substring(7).trim();
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .status(user.getStatus().name())
                .roles(user.getRoles().stream()
                        .map(Role::getRoleName)
                        .collect(Collectors.toList()))
                .createdAt(user.getCreatedAt())
                .build();
    }
}
