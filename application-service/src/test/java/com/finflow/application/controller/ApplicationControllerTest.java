package com.finflow.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.application.dto.*;
import com.finflow.application.entity.*;
import com.finflow.application.mapper.ApplicationMapper;
import com.finflow.application.service.ApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ApplicationController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
public class ApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApplicationService applicationService;

    @MockBean
    private ApplicationMapper mapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getReports_WhenExternalRequestViaGateway_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/applications/internal/reports")
                .header("X-Forwarded-Host", "api-gateway.finflow.internal"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReports_WhenInternalCallHeaderIsMissingOrInvalid_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/applications/internal/reports")
                .header("X-Internal-Call", "invalid-or-malicious-service"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReports_WhenValidInternalCall_ReturnsOkAndPayload() throws Exception {
        when(applicationService.getReports()).thenReturn(Map.of("totalApplications", 500L));

        mockMvc.perform(get("/applications/internal/reports")
                .header("X-Internal-Call", "admin-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(500L));
    }

    @Test
    void createDraft_ReturnsCreatedStatusAndApplicationPayload() throws Exception {
        LoanApplication mockApp = LoanApplication.builder().id(10L).applicationNumber("FIN-2026-00010").build();
        LoanApplicationResponse mockResponse = LoanApplicationResponse.builder().id(10L).applicationNumber("FIN-2026-00010").build();

        when(applicationService.createDraft(42L)).thenReturn(mockApp);
        when(mapper.toResponse(mockApp)).thenReturn(mockResponse);

        mockMvc.perform(post("/applications/draft")
                .header("X-User-Id", 42L)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void savePersonal_ReturnsOk() throws Exception {
        ApplicantPersonalDetails details = new ApplicantPersonalDetails();
        PersonalDetailsResponse response = PersonalDetailsResponse.builder().id(1L).build();
        when(applicationService.savePersonalDetails(eq(10L), any())).thenReturn(details);
        when(mapper.toResponse(details)).thenReturn(response);

        mockMvc.perform(put("/applications/10/personal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(details)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void saveEmployment_ReturnsOk() throws Exception {
        EmploymentDetails details = new EmploymentDetails();
        EmploymentDetailsResponse response = EmploymentDetailsResponse.builder().id(1L).build();
        when(applicationService.saveEmploymentDetails(eq(10L), any())).thenReturn(details);
        when(mapper.toResponse(details)).thenReturn(response);

        mockMvc.perform(put("/applications/10/employment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(details)))
                .andExpect(status().isOk());
    }

    @Test
    void saveLoanDetails_ReturnsOk() throws Exception {
        LoanDetails details = new LoanDetails();
        LoanDetailsResponse response = LoanDetailsResponse.builder().id(1L).build();
        when(applicationService.saveLoanDetails(eq(10L), any())).thenReturn(details);
        when(mapper.toResponse(details)).thenReturn(response);

        mockMvc.perform(put("/applications/10/loan-details")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(details)))
                .andExpect(status().isOk());
    }

    @Test
    void submit_ReturnsOk() throws Exception {
        LoanApplication mockApp = LoanApplication.builder().build();
        LoanApplicationResponse response = LoanApplicationResponse.builder().id(10L).build();
        when(applicationService.submitApplication(10L, 42L)).thenReturn(mockApp);
        when(mapper.toResponse(mockApp)).thenReturn(response);

        mockMvc.perform(post("/applications/10/submit")
                .header("X-User-Id", 42L))
                .andExpect(status().isOk());
    }

    @Test
    void getApplication_ReturnsOk() throws Exception {
        FullApplicationResponse response = FullApplicationResponse.builder().build();
        when(applicationService.getFullApplicationDto(eq(10L), any(), eq(42L), eq("ROLE_ADMIN"))).thenReturn(response);

        mockMvc.perform(get("/applications/10")
                .header("X-User-Id", 42L)
                .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void getStatus_ReturnsOk() throws Exception {
        ApplicationStatusResponse response = ApplicationStatusResponse.builder().currentStatus("DRAFT").build();
        when(applicationService.getStatusDto(eq(10L), any(), eq(42L), eq("ROLE_ADMIN"))).thenReturn(response);

        mockMvc.perform(get("/applications/10/status")
                .header("X-User-Id", 42L)
                .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void getMyApplications_ReturnsOk() throws Exception {
        when(applicationService.getApplicationsByApplicant(42L)).thenReturn(List.of());
        when(mapper.toResponseList(any())).thenReturn(List.of());

        mockMvc.perform(get("/applications/my")
                .header("X-User-Id", 42L))
                .andExpect(status().isOk());
    }

    @Test
    void getAllApplications_ReturnsOk() throws Exception {
        when(applicationService.getAllApplications("ROLE_ADMIN")).thenReturn(List.of());
        when(mapper.toResponseList(any())).thenReturn(List.of());

        mockMvc.perform(get("/applications")
                .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatus_ReturnsOk() throws Exception {
        LoanApplication mockApp = LoanApplication.builder().build();
        LoanApplicationResponse response = LoanApplicationResponse.builder().id(10L).build();
        when(applicationService.updateStatus(10L, "APPROVED", 42L, "ROLE_ADMIN", "Ok")).thenReturn(mockApp);
        when(mapper.toResponse(mockApp)).thenReturn(response);

        mockMvc.perform(put("/applications/10/status")
                .param("toStatus", "APPROVED")
                .param("remarks", "Ok")
                .header("X-User-Id", 42L)
                .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }
}
