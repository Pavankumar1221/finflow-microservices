package com.finflow.auth.service;

import com.finflow.auth.dto.AuthResponse;
import com.finflow.auth.dto.LoginRequest;
import com.finflow.auth.dto.RegisterRequest;
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
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        return toUserResponse(user);
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
