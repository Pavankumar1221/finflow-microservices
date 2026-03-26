package com.finflow.admin.service;

import com.finflow.admin.config.RabbitMQConfig;
import com.finflow.admin.dto.ApproveRequest;
import com.finflow.admin.dto.RejectRequest;
import com.finflow.admin.entity.AdminAuditLog;
import com.finflow.admin.entity.Decision;
import com.finflow.admin.event.ApplicationDecisionEvent;
import com.finflow.admin.feign.ApplicationServiceClient;
import com.finflow.admin.feign.DocumentServiceClient;
import com.finflow.admin.repository.AdminAuditLogRepository;
import com.finflow.admin.repository.DecisionRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminServiceTest {

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private AdminAuditLogRepository auditLogRepository;

    @Mock
    private ApplicationServiceClient applicationClient;

    @Mock
    private DocumentServiceClient documentClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AdminService adminService;

    private ApproveRequest validApproveRequest;
    private RejectRequest validRejectRequest;
    private final Long VALID_APP_ID = 100L;
    private final Long VALID_ADMIN_ID = 1L;
    private final String ADMIN_ROLE = "ROLE_ADMIN";

    @BeforeEach
    void setUp() {
        validApproveRequest = new ApproveRequest();
        validApproveRequest.setApprovedAmount(new BigDecimal("50000.00"));
        validApproveRequest.setApprovedTenureMonths(24);
        validApproveRequest.setInterestRate(new BigDecimal("8.5"));
        validApproveRequest.setDecisionReason("Looks good");

        validRejectRequest = new RejectRequest();
        validRejectRequest.setDecisionReason("Credit score too low");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Valid Approval Flow
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void approveApplication_ValidData_SavesDecisionAndPublishesEvent() {
        // Arrange
        when(applicationClient.getApplication(eq(VALID_APP_ID), eq(String.valueOf(VALID_ADMIN_ID)), eq(ADMIN_ROLE)))
                .thenReturn(Map.of("id", VALID_APP_ID, "status", "SUBMITTED"));

        when(documentClient.getDocumentsByApplication(eq(VALID_APP_ID), eq(String.valueOf(VALID_ADMIN_ID)), eq(ADMIN_ROLE)))
                .thenReturn(List.of(Map.of("verificationStatus", "VERIFIED")));

        when(decisionRepository.existsByApplicationId(VALID_APP_ID)).thenReturn(false);
        when(decisionRepository.save(any(Decision.class))).thenAnswer(inv -> {
            Decision d = inv.getArgument(0);
            d.setId(999L);
            return d;
        });

        // Act
        Decision result = adminService.approveApplication(VALID_APP_ID, VALID_ADMIN_ID, validApproveRequest, ADMIN_ROLE);

        // Assert - Return validations
        assertNotNull(result);
        assertEquals(Decision.DecisionStatus.APPROVED, result.getDecisionStatus());

        // Deep Event Validation (Requirement #4)
        ArgumentCaptor<ApplicationDecisionEvent> eventCaptor = ArgumentCaptor.forClass(ApplicationDecisionEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.DECISION_EXCHANGE), eq(RabbitMQConfig.DECISION_ROUTING), eventCaptor.capture());
        
        ApplicationDecisionEvent capturedEvent = eventCaptor.getValue();
        assertEquals(VALID_APP_ID, capturedEvent.getApplicationId());
        assertEquals(VALID_ADMIN_ID, capturedEvent.getAdminId());
        assertEquals("APPROVED", capturedEvent.getStatus());
        assertEquals("Looks good", capturedEvent.getRemarks());

        // Audit Log Verification (Requirement #6)
        ArgumentCaptor<AdminAuditLog> auditCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AdminAuditLog capturedAudit = auditCaptor.getValue();
        assertEquals(VALID_ADMIN_ID, capturedAudit.getAdminId());
        assertEquals("APPROVE", capturedAudit.getActionType());
        assertEquals(VALID_APP_ID, capturedAudit.getTargetId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Application Not Found (Feign Failure Handling)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void approveApplication_WhenApplicationNotFound_ThrowsException() {
        // Arrange
        when(applicationClient.getApplication(eq(VALID_APP_ID), anyString(), anyString()))
                .thenThrow(mock(FeignException.NotFound.class));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            adminService.approveApplication(VALID_APP_ID, VALID_ADMIN_ID, validApproveRequest, ADMIN_ROLE);
        });

        assertTrue(exception.getMessage().contains("Failed to fetch application"));
        verify(decisionRepository, never()).save(any(Decision.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Documents Not Verified (Business Rule)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void approveApplication_WhenDocumentsNotVerified_ThrowsException() {
        // Arrange
        when(applicationClient.getApplication(eq(VALID_APP_ID), anyString(), anyString()))
                .thenReturn(Map.of("id", VALID_APP_ID));

        // Mock document as PENDING
        when(documentClient.getDocumentsByApplication(eq(VALID_APP_ID), anyString(), anyString()))
                .thenReturn(List.of(Map.of("verificationStatus", "PENDING")));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            adminService.approveApplication(VALID_APP_ID, VALID_ADMIN_ID, validApproveRequest, ADMIN_ROLE);
        });

        assertEquals("Cannot approve: All documents must be VERIFIED", exception.getMessage());
        verify(decisionRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void approveApplication_WhenDocumentsEmpty_ThrowsException() {
        when(applicationClient.getApplication(eq(VALID_APP_ID), anyString(), anyString()))
                .thenReturn(Map.of("id", VALID_APP_ID));
        when(documentClient.getDocumentsByApplication(eq(VALID_APP_ID), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            adminService.approveApplication(VALID_APP_ID, VALID_ADMIN_ID, validApproveRequest, ADMIN_ROLE);
        });

        assertEquals("Cannot approve: All documents must be VERIFIED", exception.getMessage());
    }

    @Test
    void approveApplication_WhenDecisionExists_ThrowsException() {
        when(applicationClient.getApplication(eq(VALID_APP_ID), anyString(), anyString()))
                .thenReturn(Map.of("id", VALID_APP_ID));
        when(documentClient.getDocumentsByApplication(eq(VALID_APP_ID), anyString(), anyString()))
                .thenReturn(List.of(Map.of("verificationStatus", "VERIFIED")));
        when(decisionRepository.existsByApplicationId(VALID_APP_ID)).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            adminService.approveApplication(VALID_APP_ID, VALID_ADMIN_ID, validApproveRequest, ADMIN_ROLE);
        });

        assertEquals("Decision already exists for application: 100", exception.getMessage());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Role Validation (Security)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void approveApplication_WhenNotAdminRole_ThrowsException() {
        // Arrange
        String invalidRole = "ROLE_USER";

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            adminService.approveApplication(VALID_APP_ID, VALID_ADMIN_ID, validApproveRequest, invalidRole);
        });

        assertEquals("Access Denied: Only ADMIN can perform this action", exception.getMessage());
        verify(applicationClient, never()).getApplication(anyLong(), anyString(), anyString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Null Request Edge Case
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void approveApplication_WhenRequestIsNull_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            adminService.approveApplication(VALID_APP_ID, VALID_ADMIN_ID, null, ADMIN_ROLE);
        });

        assertEquals("Approve request cannot be null", exception.getMessage());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Invalid Input Value (Amount <= 0)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void approveApplication_WhenAmountIsZero_ThrowsException() {
        // Arrange
        validApproveRequest.setApprovedAmount(BigDecimal.ZERO);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            adminService.approveApplication(VALID_APP_ID, VALID_ADMIN_ID, validApproveRequest, ADMIN_ROLE);
        });

        assertEquals("Invalid approval amount", exception.getMessage());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Reject Application - Valid Flow
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void rejectApplication_ValidData_SavesDecisionAndPublishesEvent() {
        // Arrange
        when(applicationClient.getApplication(eq(VALID_APP_ID), anyString(), anyString()))
                .thenReturn(Map.of("id", VALID_APP_ID));

        when(decisionRepository.existsByApplicationId(VALID_APP_ID)).thenReturn(false);
        when(decisionRepository.save(any(Decision.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Decision result = adminService.rejectApplication(VALID_APP_ID, VALID_ADMIN_ID, validRejectRequest, ADMIN_ROLE);

        // Assert
        assertEquals(Decision.DecisionStatus.REJECTED, result.getDecisionStatus());

        // Event Validation
        ArgumentCaptor<ApplicationDecisionEvent> eventCaptor = ArgumentCaptor.forClass(ApplicationDecisionEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.DECISION_EXCHANGE), eq(RabbitMQConfig.DECISION_ROUTING), eventCaptor.capture());
        assertEquals("REJECTED", eventCaptor.getValue().getStatus());

        // Audit Validation
        ArgumentCaptor<AdminAuditLog> auditCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertEquals("REJECT", auditCaptor.getValue().getActionType());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Additional Coverage Tests
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void verifyDocumentViaFeign_Success() {
        Long docId = 20L;
        when(documentClient.updateDocumentStatus(eq(docId), any(), anyString(), anyString()))
                .thenReturn(Map.of("id", docId, "verificationStatus", "VERIFIED"));

        Object result = adminService.verifyDocumentViaFeign(docId, VALID_ADMIN_ID, null);

        assertNotNull(result);
        verify(auditLogRepository).save(any(AdminAuditLog.class));
    }

    @Test
    void rejectDocumentViaFeign_Success() {
        Long docId = 20L;
        when(documentClient.updateDocumentStatus(eq(docId), any(), anyString(), anyString()))
                .thenReturn(Map.of("id", docId, "verificationStatus", "REJECTED"));

        Object result = adminService.rejectDocumentViaFeign(docId, VALID_ADMIN_ID, "Illegible Document");

        assertNotNull(result);
        verify(auditLogRepository).save(any(AdminAuditLog.class));
    }

    @Test
    void getAllApplications_ReturnsList() {
        when(applicationClient.getAllApplications(anyString(), anyString()))
                .thenReturn(List.of(Map.of("id", 1L)));
        List<Object> apps = adminService.getAllApplications(String.valueOf(VALID_ADMIN_ID), ADMIN_ROLE);
        assertFalse(apps.isEmpty());
    }

    @Test
    void getApplicationForReview_ReturnCompositeMap() {
        when(applicationClient.getApplication(VALID_APP_ID, String.valueOf(VALID_ADMIN_ID), ADMIN_ROLE))
                .thenReturn(Map.of("id", VALID_APP_ID));
        when(documentClient.getDocumentsByApplication(VALID_APP_ID, String.valueOf(VALID_ADMIN_ID), ADMIN_ROLE))
                .thenReturn(List.of(Map.of("id", 5L)));

        Map<String, Object> result = adminService.getApplicationForReview(VALID_APP_ID, String.valueOf(VALID_ADMIN_ID), ADMIN_ROLE);
        assertTrue(result.containsKey("application"));
        assertTrue(result.containsKey("documents"));
    }

    @Test
    void getDecisionByApplication_WhenExists_ReturnsDecision() {
        Decision d = new Decision();
        d.setApplicationId(VALID_APP_ID);
        d.setDecisionStatus(Decision.DecisionStatus.APPROVED);
        when(decisionRepository.findByApplicationId(VALID_APP_ID)).thenReturn(Optional.of(d));

        Decision result = adminService.getDecisionByApplication(VALID_APP_ID);
        assertEquals(Decision.DecisionStatus.APPROVED, result.getDecisionStatus());
    }

    @Test
    void getAuditLogs_ReturnsPage() {
        AdminAuditLog log = new AdminAuditLog();
        log.setAdminId(VALID_ADMIN_ID);
        Page<AdminAuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Page<AdminAuditLog> result = adminService.getAuditLogs(PageRequest.of(0, 5));
        assertEquals(1, result.getContent().size());
    }
}
