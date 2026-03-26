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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminService {

    private final DecisionRepository decisionRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final ApplicationServiceClient applicationClient;
    private final DocumentServiceClient documentClient;
    private final RabbitTemplate rabbitTemplate;

    // ─── Applications ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @CircuitBreaker(name = "applicationServiceCB", fallbackMethod = "getAllApplicationsFallback")
    @Retry(name = "applicationServiceCB")
    public List<Object> getAllApplications(String userId, String roles) {
        return applicationClient.getAllApplications(userId, roles);
    }

    public List<Object> getAllApplicationsFallback(String userId, String roles, Throwable t) {
        log.error("Fallback: application service unavailable for getAllApplications", t);
        return java.util.Collections.emptyList();
    }

    @Transactional(readOnly = true)
    @CircuitBreaker(name = "applicationServiceCB", fallbackMethod = "getApplicationForReviewFallback")
    @Retry(name = "applicationServiceCB")
    public Map<String, Object> getApplicationForReview(Long applicationId, String userId, String roles) {
        Map<String, Object> application = applicationClient.getApplication(applicationId, userId, roles);
        List<Object> documents = documentClient.getDocumentsByApplication(applicationId, userId, roles);

        return Map.of(
                "application", application,
                "documents",  documents
        );
    }

    public Map<String, Object> getApplicationForReviewFallback(Long applicationId, String userId, String roles, Throwable t) {
        log.error("Fallback: service unavailable for getApplicationForReview {}", applicationId, t);
        return Map.of("error", "Service unavailable");
    }

    // ─── Decisions ────────────────────────────────────────────────────────────

    @CircuitBreaker(name = "applicationServiceCB", fallbackMethod = "approveApplicationFallback")
    @Retry(name = "applicationServiceCB")
    public Decision approveApplication(Long applicationId, Long adminId, ApproveRequest request, String roles) {
        if (roles == null || !roles.contains("ROLE_ADMIN")) {
            throw new RuntimeException("Access Denied: Only ADMIN can perform this action");
        }
        if (request == null) {
            throw new IllegalArgumentException("Approve request cannot be null");
        }
        if (request.getApprovedAmount() == null || request.getApprovedAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid approval amount");
        }

        try {
            applicationClient.getApplication(applicationId, String.valueOf(adminId), roles);
        } catch (feign.FeignException e) {
            throw new RuntimeException("Failed to fetch application or application not found", e);
        }

        try {
            List<Object> docs = documentClient.getDocumentsByApplication(applicationId, String.valueOf(adminId), roles);
            boolean allVerified = docs != null && !docs.isEmpty();
            if (docs != null) {
                for (Object docObj : docs) {
                    Map<String, Object> doc = (Map<String, Object>) docObj;
                    if (!"VERIFIED".equals(doc.get("verificationStatus"))) {
                        allVerified = false;
                        break;
                    }
                }
            }
            if (!allVerified) {
                throw new RuntimeException("Cannot approve: All documents must be VERIFIED");
            }
        } catch (feign.FeignException e) {
            throw new RuntimeException("Failed to fetch documents for application", e);
        }

        if (decisionRepository.existsByApplicationId(applicationId)) {
            throw new RuntimeException("Decision already exists for application: " + applicationId);
        }

        Decision decision = Decision.builder()
                .applicationId(applicationId)
                .decidedBy(adminId)
                .decisionStatus(Decision.DecisionStatus.APPROVED)
                .approvedAmount(request.getApprovedAmount())
                .approvedTenureMonths(request.getApprovedTenureMonths())
                .interestRate(request.getInterestRate())
                .terms(request.getTerms())
                .decisionReason(request.getDecisionReason())
                .build();

        Decision saved = decisionRepository.save(decision);

        // Publish event
        publishDecisionEvent(applicationId, adminId, "APPROVED", request.getDecisionReason());

        // Audit
        audit(adminId, "APPROVE", "APPLICATION", applicationId,
              "Approved application " + applicationId + ". Amount: " + request.getApprovedAmount());

        log.info("Application {} approved by admin {}", applicationId, adminId);
        return saved;
    }

    public Decision approveApplicationFallback(Long applicationId, Long adminId, ApproveRequest request, String roles, Throwable t) {
        log.error("Fallback: service unavailable during approveApplication {}", applicationId, t);
        throw new RuntimeException("Service unavailable during approval", t);
    }

    @CircuitBreaker(name = "applicationServiceCB", fallbackMethod = "rejectApplicationFallback")
    @Retry(name = "applicationServiceCB")
    public Decision rejectApplication(Long applicationId, Long adminId, RejectRequest request, String roles) {
        if (roles == null || !roles.contains("ROLE_ADMIN")) {
            throw new RuntimeException("Access Denied: Only ADMIN can perform this action");
        }
        if (request == null) {
            throw new IllegalArgumentException("Reject request cannot be null");
        }

        try {
            applicationClient.getApplication(applicationId, String.valueOf(adminId), roles);
        } catch (feign.FeignException e) {
            throw new RuntimeException("Failed to fetch application or application not found", e);
        }

        if (decisionRepository.existsByApplicationId(applicationId)) {
            throw new RuntimeException("Decision already exists for application: " + applicationId);
        }

        Decision decision = Decision.builder()
                .applicationId(applicationId)
                .decidedBy(adminId)
                .decisionStatus(Decision.DecisionStatus.REJECTED)
                .decisionReason(request.getDecisionReason())
                .build();

        Decision saved = decisionRepository.save(decision);

        // Publish event
        publishDecisionEvent(applicationId, adminId, "REJECTED", request.getDecisionReason());

        // Audit
        audit(adminId, "REJECT", "APPLICATION", applicationId,
              "Rejected application " + applicationId + ". Reason: " + request.getDecisionReason());

        log.info("Application {} rejected by admin {}", applicationId, adminId);
        return saved;
    }

    public Decision rejectApplicationFallback(Long applicationId, Long adminId, RejectRequest request, String roles, Throwable t) {
        log.error("Fallback: service unavailable during rejectApplication {}", applicationId, t);
        throw new RuntimeException("Service unavailable during rejection", t);
    }

    @Transactional(readOnly = true)
    public Decision getDecisionByApplication(Long applicationId) {
        return decisionRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new RuntimeException("No decision found for application: " + applicationId));
    }

    // ─── Document verification via Feign ─────────────────────────────────────

    @CircuitBreaker(name = "documentServiceCB", fallbackMethod = "verifyDocumentFallback")
    @Retry(name = "documentServiceCB")
    public Object verifyDocumentViaFeign(Long documentId, Long adminId, String remarks) {
        if (remarks == null) {
            remarks = "Verified by admin";
        }

        Object result = documentClient.updateDocumentStatus(
                documentId,
                Map.of("status", "VERIFIED", "remarks", remarks),
                "admin-service",
                String.valueOf(adminId)
        );

        audit(adminId, "VERIFY_DOC", "DOCUMENT", documentId,
              "Verified document " + documentId + ". Remarks: " + remarks);

        return result;
    }

    public Object verifyDocumentFallback(Long documentId, Long adminId, String remarks, Throwable t) {
        log.error("Fallback: service unavailable during verifyDocument {}", documentId, t);
        throw new RuntimeException("Service unavailable during document verification", t);
    }

    @CircuitBreaker(name = "documentServiceCB", fallbackMethod = "rejectDocumentFallback")
    @Retry(name = "documentServiceCB")
    public Object rejectDocumentViaFeign(Long documentId, Long adminId, String remarks) {
        if (remarks == null) {
            remarks = "Rejected by admin";
        }

        Object result = documentClient.updateDocumentStatus(
                documentId,
                Map.of("status", "REJECTED", "remarks", remarks),
                "admin-service",
                String.valueOf(adminId)
        );

        audit(adminId, "REJECT_DOC", "DOCUMENT", documentId,
              "Rejected document " + documentId + ". Remarks: " + remarks);

        return result;
    }

    public Object rejectDocumentFallback(Long documentId, Long adminId, String remarks, Throwable t) {
        log.error("Fallback: service unavailable during rejectDocument {}", documentId, t);
        throw new RuntimeException("Service unavailable during document rejection", t);
    }

    // ─── Audit Log ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminAuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    private void audit(Long adminId, String action, String entity, Long targetId, String summary) {
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(adminId).actionType(action)
                .targetEntity(entity).targetId(targetId)
                .actionSummary(summary)
                .build());
    }

    private void publishDecisionEvent(Long applicationId, Long adminId, String status, String remarks) {
        ApplicationDecisionEvent event = ApplicationDecisionEvent.builder()
                .applicationId(applicationId)
                .status(status)
                .adminId(adminId)
                .remarks(remarks)
                .timestamp(LocalDateTime.now().toString())
                .build();
        rabbitTemplate.convertAndSend(RabbitMQConfig.DECISION_EXCHANGE, RabbitMQConfig.DECISION_ROUTING, event);
        log.info("Decision event published for application {}: {}", applicationId, status);
    }
}
