package com.finflow.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.application.dto.LoanApplicationResponse;
import com.finflow.application.entity.LoanApplication;
import com.finflow.application.mapper.ApplicationMapper;
import com.finflow.application.service.ApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Validates the Web/Controller layer of the Application Service.
 * Leverages @WebMvcTest to slice the application contexts exclusively for Web components (fast!).
 * Native Spring Security is excluded to isolate precise Controller routing and JSON mapping logic.
 */
@WebMvcTest(controllers = ApplicationController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
public class ApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApplicationService applicationService;

    @MockBean
    private ApplicationMapper mapper;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Internal API Security Checks (Header based shielding)
    // ─────────────────────────────────────────────────────────────────────────
    
    @Test
    void getReports_WhenExternalRequestViaGateway_ReturnsForbidden() throws Exception {
        // Simulating a request bypassing authorization but carrying Gateway's X-Forwarded-Host header
        mockMvc.perform(get("/applications/internal/reports")
                .header("X-Forwarded-Host", "api-gateway.finflow.internal"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReports_WhenInternalCallHeaderIsMissingOrInvalid_ReturnsForbidden() throws Exception {
        // Call directly to Microservice without gateway, but without the correct shared-secret header
        mockMvc.perform(get("/applications/internal/reports")
                .header("X-Internal-Call", "invalid-or-malicious-service"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReports_WhenValidInternalCall_ReturnsOkAndPayload() throws Exception {
        // Valid Admin Service Server-to-Server call
        when(applicationService.getReports()).thenReturn(Map.of("totalApplications", 500L));

        mockMvc.perform(get("/applications/internal/reports")
                .header("X-Internal-Call", "admin-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(500L));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Draft Creation Logic (HTTP 201 Created Validation)
    // ─────────────────────────────────────────────────────────────────────────
    
    @Test
    void createDraft_ReturnsCreatedStatusAndApplicationPayload() throws Exception {
        // Arrange Mock Models
        LoanApplication mockApp = LoanApplication.builder()
                .id(10L)
                .applicationNumber("FIN-2026-00010")
                .build();
                
        LoanApplicationResponse mockResponse = LoanApplicationResponse.builder()
                .id(10L)
                .applicationNumber("FIN-2026-00010")
                .status(LoanApplication.ApplicationStatus.DRAFT)
                .build();

        when(applicationService.createDraft(42L)).thenReturn(mockApp);
        when(mapper.toResponse(mockApp)).thenReturn(mockResponse);

        // Act & Assert JSON Path
        mockMvc.perform(post("/applications/draft")
                .header("X-User-Id", 42L)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()) // Validates 201 Status
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.applicationNumber").value("FIN-2026-00010"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }
}
