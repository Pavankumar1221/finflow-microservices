package com.finflow.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.auth.dto.AuthResponse;
import com.finflow.auth.dto.LoginRequest;
import com.finflow.auth.dto.RegisterRequest;
import com.finflow.auth.dto.UserResponse;
import com.finflow.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Ignore security filters for unit tests of controller
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        userResponse = UserResponse.builder()
                .id(1L)
                .email("test@example.com")
                .fullName("Test User")
                .status("ACTIVE")
                .roles(List.of("ROLE_APPLICANT"))
                .build();
    }

    @Test
    void register_ShouldReturnCreated() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setFullName("Test User");
        request.setMobileNumber("9876543210");

        when(authService.register(any(RegisterRequest.class))).thenReturn(userResponse);

        mockMvc.perform(post("/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void register_InvalidMobileNumber_ShouldReturnBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setFullName("Test User");
        request.setMobileNumber("1234567890");

        mockMvc.perform(post("/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_InvalidEmail_ShouldReturnBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("invalidemail");
        request.setPassword("password123");
        request.setFullName("Test User");
        request.setMobileNumber("9876543210");

        mockMvc.perform(post("/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_MissingRequiredFields_ShouldReturnBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();

        mockMvc.perform(post("/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_ShouldReturnAuthResponse() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        AuthResponse authResponse = AuthResponse.builder()
                .token("mockToken")
                .userId(1L)
                .email("test@example.com")
                .roles(List.of("ROLE_APPLICANT"))
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mockToken"));
    }

    @Test
    void login_MissingRequiredFields_ShouldReturnBadRequest() throws Exception {
        LoginRequest request = new LoginRequest();

        mockMvc.perform(post("/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validate_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/auth/validate")
                .header("Authorization", "Bearer mockToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserById_ShouldReturnUser() throws Exception {
        when(authService.getUserById(1L)).thenReturn(userResponse);

        mockMvc.perform(get("/auth/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void getAllUsersInternal_ValidInternalCall_ShouldReturnUsers() throws Exception {
        when(authService.getAllUsersInternal()).thenReturn(List.of(Map.of("email", "test@example.com")));

        mockMvc.perform(get("/auth/internal/users")
                .header("X-Internal-Call", "admin-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("test@example.com"));
    }

    @Test
    void getAllUsersInternal_InvalidInternalCall_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/auth/internal/users")
                .header("X-Internal-Call", "external-service"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllUsersInternal_ExposedViaGateway_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/auth/internal/users")
                .header("X-Internal-Call", "admin-service")
                .header("X-Forwarded-Host", "api-gateway"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUserInternal_InvalidInternalCall_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(put("/auth/internal/users/1")
                .with(csrf())
                .header("X-Internal-Call", "external-service")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUserInternal_ValidInternalCall_ShouldUpdateUser() throws Exception {
        Map<String, Object> updateReq = Map.of("status", "LOCKED");
        Map<String, Object> response = Map.of("id", 1, "status", "LOCKED");

        when(authService.updateUserInternal(eq(1L), any())).thenReturn(response);

        mockMvc.perform(put("/auth/internal/users/1")
                .with(csrf())
                .header("X-Internal-Call", "admin-service")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }
    
    @Test
    void updateUserInternal_ForwardedHost_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(put("/auth/internal/users/1")
                .with(csrf())
                .header("X-Internal-Call", "admin-service")
                .header("X-Forwarded-Host", "api-gateway")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }
    
    @Test
    void health_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/auth/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Auth Service is UP"));
    }
}
