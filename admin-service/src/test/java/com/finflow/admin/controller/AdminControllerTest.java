package com.finflow.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.admin.dto.ApproveRequest;
import com.finflow.admin.dto.AuditLogResponse;
import com.finflow.admin.dto.DecisionResponse;
import com.finflow.admin.dto.RejectRequest;
import com.finflow.admin.entity.AdminAuditLog;
import com.finflow.admin.entity.Decision;
import com.finflow.admin.feign.AuthServiceClient;
import com.finflow.admin.mapper.AdminMapper;
import com.finflow.admin.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminService adminService;

    @MockBean
    private AdminMapper mapper;

    @MockBean
    private AuthServiceClient authServiceClient;

    @Test
    void getAllApplications_ReturnsOk() throws Exception {
        when(adminService.getAllApplications(anyString(), anyString())).thenReturn(List.of(Map.of("id", 1L)));

        mockMvc.perform(get("/admin/applications")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void reviewApplication_ReturnsOk() throws Exception {
        when(adminService.getApplicationForReview(anyLong(), anyString(), anyString()))
                .thenReturn(Map.of("application", Map.of("id", 1L)));

        mockMvc.perform(get("/admin/applications/1/review")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.id").value(1));
    }

    @Test
    void approve_ReturnsOk() throws Exception {
        ApproveRequest req = new ApproveRequest();
        req.setApprovedAmount(new BigDecimal("1000"));
        req.setApprovedTenureMonths(12);
        req.setInterestRate(new BigDecimal("10"));
        req.setDecisionReason("OK");

        Decision decision = new Decision();
        DecisionResponse resp = new DecisionResponse();
        resp.setId(1L);

        when(adminService.approveApplication(anyLong(), anyLong(), any(), anyString())).thenReturn(decision);
        when(mapper.toResponse(decision)).thenReturn(resp);

        mockMvc.perform(post("/admin/applications/1/approve")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ROLE_ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void reject_ReturnsOk() throws Exception {
        RejectRequest req = new RejectRequest();
        req.setDecisionReason("Not good");

        Decision decision = new Decision();
        DecisionResponse resp = new DecisionResponse();
        resp.setId(1L);

        when(adminService.rejectApplication(anyLong(), anyLong(), any(), anyString())).thenReturn(decision);
        when(mapper.toResponse(decision)).thenReturn(resp);

        mockMvc.perform(post("/admin/applications/1/reject")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ROLE_ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getDecision_ReturnsOk() throws Exception {
        Decision decision = new Decision();
        DecisionResponse resp = new DecisionResponse();
        resp.setId(1L);

        when(adminService.getDecisionByApplication(anyLong())).thenReturn(decision);
        when(mapper.toResponse(decision)).thenReturn(resp);

        mockMvc.perform(get("/admin/applications/1/decision"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void makeDecision_Approve_ReturnsOk() throws Exception {
        Decision decision = new Decision();
        DecisionResponse resp = new DecisionResponse();
        resp.setId(10L);

        when(adminService.approveApplication(anyLong(), any(), any(ApproveRequest.class), anyString())).thenReturn(decision);
        when(mapper.toResponse(decision)).thenReturn(resp);

        mockMvc.perform(post("/admin/applications/1/decision")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ROLE_ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\", \"remarks\":\"OK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void makeDecision_Reject_ReturnsOk() throws Exception {
        Decision decision = new Decision();
        DecisionResponse resp = new DecisionResponse();
        resp.setId(20L);

        when(adminService.rejectApplication(anyLong(), any(), any(RejectRequest.class), anyString())).thenReturn(decision);
        when(mapper.toResponse(decision)).thenReturn(resp);

        mockMvc.perform(post("/admin/applications/1/decision")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ROLE_ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECTED\", \"remarks\":\"Bad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20));
    }
    
    @Test
    void makeDecision_InvalidDecision_ThrowsException() throws Exception {
        mockMvc.perform(post("/admin/applications/1/decision")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ROLE_ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"INVALID\", \"remarks\":\"Bad\"}"))
                .andExpect(status().isBadRequest()); // Depending on global exception handler, could be 400 or 500
    }

    @Test
    void getAllUsers_ReturnsOk() throws Exception {
        when(authServiceClient.getAllUsers("admin-service")).thenReturn(List.of(Map.of("email", "test@test.com")));

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("test@test.com"));
    }

    @Test
    void updateUser_ReturnsOk() throws Exception {
        when(authServiceClient.updateUser(anyLong(), any(), eq("admin-service"))).thenReturn(Map.of("status", "INACTIVE"));

        mockMvc.perform(put("/admin/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void verifyDocument_ReturnsOk() throws Exception {
        when(adminService.verifyDocumentViaFeign(anyLong(), anyLong(), anyString())).thenReturn(Map.of("status", "VERIFIED"));

        mockMvc.perform(put("/admin/documents/1/verify")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"remarks\":\"valid\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectDocument_ReturnsOk() throws Exception {
        when(adminService.rejectDocumentViaFeign(anyLong(), anyLong(), anyString())).thenReturn(Map.of("status", "REJECTED"));

        mockMvc.perform(put("/admin/documents/1/reject")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"remarks\":\"invalid\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void getAuditLog_ReturnsOk() throws Exception {
        Page<AdminAuditLog> page = new PageImpl<>(List.of());
        when(adminService.getAuditLogs(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/admin/audit-log?page=0&size=10"))
                .andExpect(status().isOk());
    }

    @Test
    void health_ReturnsOk() throws Exception {
        mockMvc.perform(get("/admin/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Admin Service is UP"));
    }
}
