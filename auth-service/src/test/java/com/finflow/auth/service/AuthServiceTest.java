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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserDetailsService userDetailsService;

    @InjectMocks
    private AuthService authService;

    private User user;
    private Role role;

    @BeforeEach
    void setUp() {
        role = Role.builder().id(1).roleName("ROLE_APPLICANT").build();
        user = User.builder()
                .id(1L)
                .fullName("Test User")
                .email("test@example.com")
                .mobileNumber("1234567890")
                .passwordHash("hashedpassword")
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        user.getRoles().add(role);
    }

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Test User");
        request.setEmail("test@example.com");
        request.setMobileNumber("1234567890");
        request.setPassword("password");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByMobileNumber(anyString())).thenReturn(false);
        when(roleRepository.findByRoleName("ROLE_APPLICANT")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("password")).thenReturn("hashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("test@example.com", response.getEmail());
        assertEquals("ROLE_APPLICANT", response.getRoles().get(0));
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_EmailAlreadyExists_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");

        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));
        assertEquals("Email already registered: test@example.com", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_MobileAlreadyExists_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setMobileNumber("1234567890");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByMobileNumber(anyString())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));
        assertEquals("Mobile number already registered", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }
    
    @Test
    void register_RoleNotFound_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Test User");
        request.setEmail("test@example.com");
        request.setMobileNumber("1234567890");
        request.setPassword("password");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByMobileNumber(anyString())).thenReturn(false);
        when(roleRepository.findByRoleName("ROLE_APPLICANT")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));
        assertEquals("Default applicant role not found in system schema.", ex.getMessage());
    }

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("test@example.com")
                .password("hashedpassword")
                .authorities("ROLE_APPLICANT")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        when(jwtUtil.generateToken(any(UserDetails.class), eq(1L), anyList())).thenReturn("mockToken");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mockToken", response.getToken());
        assertEquals(1L, response.getUserId());
        assertEquals("test@example.com", response.getEmail());
        assertTrue(response.getRoles().contains("ROLE_APPLICANT"));
    }

    @Test
    void login_UserNotFound_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("notfound@example.com");
        request.setPassword("password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void login_BadCredentials_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrongpassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

        org.springframework.security.authentication.BadCredentialsException ex = 
            assertThrows(org.springframework.security.authentication.BadCredentialsException.class, 
                     () -> authService.login(request));
        assertEquals("Bad credentials", ex.getMessage());
    }

    @Test
    void getUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = authService.getUserById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("test@example.com", response.getEmail());
    }

    @Test
    void getUserById_NotFound_ThrowsException() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.getUserById(2L));
        assertEquals("User not found with id: 2", ex.getMessage());
    }

    @Test
    void getAllUsersInternal_ReturnsList() {
        when(userRepository.findAll()).thenReturn(List.of(user));

        List<Map<String, Object>> users = authService.getAllUsersInternal();

        assertNotNull(users);
        assertEquals(1, users.size());
        assertEquals("test@example.com", users.get(0).get("email"));
        assertEquals("ACTIVE", users.get(0).get("status"));
    }

    @Test
    void updateUserInternal_Success() {
        Role adminRole = Role.builder().id(2).roleName("ROLE_ADMIN").build();
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenReturn(user);

        Map<String, Object> updateRequest = Map.of(
                "status", "LOCKED",
                "role", "ROLE_ADMIN"
        );

        Map<String, Object> response = authService.updateUserInternal(1L, updateRequest);

        assertNotNull(response);
        verify(userRepository).save(any(User.class));
        assertEquals(User.UserStatus.LOCKED, user.getStatus());
        assertTrue(user.getRoles().contains(adminRole));
    }

    @Test
    void updateUserInternal_OnlyStatus_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        Map<String, Object> updateRequest = Map.of(
                "status", "LOCKED"
        );

        Map<String, Object> response = authService.updateUserInternal(1L, updateRequest);

        assertNotNull(response);
        verify(userRepository).save(any(User.class));
        assertEquals(User.UserStatus.LOCKED, user.getStatus());
        assertTrue(user.getRoles().contains(role));
        verify(roleRepository, never()).findByRoleName(anyString());
    }

    @Test
    void updateUserInternal_OnlyRole_Success() {
        Role adminRole = Role.builder().id(2).roleName("ROLE_ADMIN").build();
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenReturn(user);

        Map<String, Object> updateRequest = Map.of(
                "role", "ROLE_ADMIN"
        );

        Map<String, Object> response = authService.updateUserInternal(1L, updateRequest);

        assertNotNull(response);
        verify(userRepository).save(any(User.class));
        assertEquals(User.UserStatus.ACTIVE, user.getStatus());
        assertTrue(user.getRoles().contains(adminRole));
    }

    @Test
    void updateUserInternal_UserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.updateUserInternal(1L, Map.of()));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void updateUserInternal_RoleNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByRoleName("ROLE_UNKNOWN")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.updateUserInternal(1L, Map.of("role", "ROLE_UNKNOWN")));
        assertEquals("Role not found: ROLE_UNKNOWN", ex.getMessage());
    }
}
