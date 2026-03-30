package com.finflow.admin.service;

import com.finflow.admin.config.RabbitMQConfig;
import com.finflow.admin.dto.ApproveRequest;
import com.finflow.admin.dto.DecisionRequest;
import com.finflow.admin.dto.RejectRequest;
import com.finflow.admin.entity.AdminAuditLog;
import com.finflow.admin.entity.Decision;
import com.finflow.admin.event.ApplicationDecisionEvent;
import com.finflow.admin.feign.ApplicationServiceClient;
import com.finflow.admin.feign.AuthServiceClient;
import com.finflow.admin.feign.DocumentServiceClient;
import com.finflow.admin.repository.AdminAuditLogRepository;
import com.finflow.admin.repository.DecisionRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminService {

    private final DecisionRepository decisionRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final ApplicationServiceClient applicationClient;
    private final DocumentServiceClient documentClient;
    private final AuthServiceClient authServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final NotificationEmailService notificationEmailService;

    @Transactional(readOnly = true)
    @CircuitBreaker(name = "applicationServiceCB", fallbackMethod = "getAllApplicationsFallback")
    @Retry(name = "applicationServiceCB")
    public Page<Object> getAllApplications(String userId, String roles, Pageable pageable) {
        if (roles == null || !roles.contains("ROLE_ADMIN")) {
            throw new RuntimeException("Access Denied: Only ADMIN can view applications");
        }

        Map<String, Object> response = applicationClient.getAllApplications(
                userId,
                roles,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                buildSortParams(pageable));

        @SuppressWarnings("unchecked")
        List<Object> content = (List<Object>) response.getOrDefault("content", Collections.emptyList());
        long totalElements = ((Number) response.getOrDefault("totalElements", content.size())).longValue();
        return new PageImpl<>(content, pageable, totalElements);
    }

    @Transactional(readOnly = true)
    public List<Object> getAllApplications(String userId, String roles) {
        return getAllApplications(userId, roles, PageRequest.of(0, 1000)).getContent();
    }

    public Page<Object> getAllApplicationsFallback(String userId, String roles, Pageable pageable, Throwable t) {
        log.error("Fallback: application service unavailable for getAllApplications", t);
        return Page.empty(pageable);
    }

    @Transactional(readOnly = true)
    @CircuitBreaker(name = "applicationServiceCB", fallbackMethod = "getApplicationForReviewFallback")
    @Retry(name = "applicationServiceCB")
    public Map<String, Object> getApplicationForReview(Long applicationId, String userId, String roles) {
        Map<String, Object> application = applicationClient.getApplication(applicationId, userId, roles);
        List<Object> documents = documentClient.getDocumentsByApplication(applicationId, userId, roles);

        return Map.of(
                "application", application,
                "documents", documents
        );
    }

    public Map<String, Object> getApplicationForReviewFallback(Long applicationId, String userId, String roles, Throwable t) {
        log.error("Fallback: service unavailable for getApplicationForReview {}", applicationId, t);
        return Map.of("error", "Service unavailable");
    }

    @CircuitBreaker(name = "applicationServiceCB", fallbackMethod = "makeDecisionFallback")
    @Retry(name = "applicationServiceCB")
    @org.springframework.cache.annotation.Caching(evict = {
        @org.springframework.cache.annotation.CacheEvict(value = "decisions-cache", allEntries = true),
        @org.springframework.cache.annotation.CacheEvict(value = "reports-cache", allEntries = true)
    })
    public Decision makeDecision(Long applicationId, Long adminId, DecisionRequest request, String roles) {
        if (roles == null || !roles.contains("ROLE_ADMIN")) {
            throw new RuntimeException("Access Denied: Only ADMIN can perform this action");
        }
        if (request == null) {
            throw new IllegalArgumentException("Decision request cannot be null");
        }

        Map<String, Object> application;
        try {
            application = applicationClient.getApplication(applicationId, String.valueOf(adminId), roles);
        } catch (feign.FeignException e) {
            throw new RuntimeException("Failed to fetch application or application not found", e);
        }

        if (decisionRepository.existsByApplicationId(applicationId)) {
            throw new RuntimeException("Decision already exists for application: " + applicationId);
        }

        Decision.DecisionStatus decisionStatus = parseDecisionStatus(request.getDecision());
        String remarks = request.getRemarks();

        if (decisionStatus == Decision.DecisionStatus.APPROVED) {
            verifyDocumentsApproved(applicationId, adminId, roles);
            validateApprovalTerms(request);
        }

        Decision decision = Decision.builder()
                .applicationId(applicationId)
                .decidedBy(adminId)
                .decisionStatus(decisionStatus)
                .approvedAmount(decisionStatus == Decision.DecisionStatus.APPROVED ? request.getApprovedAmount() : null)
                .approvedTenureMonths(decisionStatus == Decision.DecisionStatus.APPROVED ? request.getApprovedTenureMonths() : null)
                .interestRate(decisionStatus == Decision.DecisionStatus.APPROVED ? request.getInterestRate() : null)
                .terms(decisionStatus == Decision.DecisionStatus.APPROVED ? request.getTerms() : null)
                .decisionReason(remarks)
                .build();

        Decision saved = decisionRepository.save(decision);
        publishDecisionEvent(applicationId, adminId, decisionStatus.name(), remarks);

        String actionType = decisionStatus == Decision.DecisionStatus.APPROVED ? "APPROVE" : "REJECT";
        audit(adminId, actionType, "APPLICATION", applicationId,
                actionType.equals("APPROVE")
                        ? "Approved application " + applicationId + ". Remarks: " + remarks
                        : "Rejected application " + applicationId + ". Remarks: " + remarks);

        sendLoanDecisionEmail(application, saved, remarks);

        log.info("Application {} {} by admin {}", applicationId, decisionStatus.name().toLowerCase(), adminId);
        return saved;
    }

    public Decision approveApplication(Long applicationId, Long adminId, ApproveRequest request, String roles) {
        Decision decision = makeDecision(
                applicationId,
                adminId,
                DecisionRequest.builder()
                        .decision("APPROVED")
                        .remarks(request != null ? request.getDecisionReason() : null)
                        .approvedAmount(request != null ? request.getApprovedAmount() : null)
                        .approvedTenureMonths(request != null ? request.getApprovedTenureMonths() : null)
                        .interestRate(request != null ? request.getInterestRate() : null)
                        .terms(request != null ? request.getTerms() : null)
                        .build(),
                roles);

        if (request != null) {
            decision.setApprovedAmount(request.getApprovedAmount());
            decision.setApprovedTenureMonths(request.getApprovedTenureMonths());
            decision.setInterestRate(request.getInterestRate());
            decision.setTerms(request.getTerms());
            decision = decisionRepository.save(decision);
        }
        return decision;
    }

    public Decision rejectApplication(Long applicationId, Long adminId, RejectRequest request, String roles) {
        return makeDecision(
                applicationId,
                adminId,
                DecisionRequest.builder()
                        .decision("REJECTED")
                        .remarks(request != null ? request.getDecisionReason() : null)
                        .build(),
                roles);
    }

    public Decision makeDecisionFallback(Long applicationId, Long adminId, DecisionRequest request, String roles, Throwable t) {
        log.error("Fallback: service unavailable during makeDecision {}", applicationId, t);
        throw new RuntimeException("Service unavailable during decision processing", t);
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "decisions-cache", key = "#applicationId")
    public Decision getDecisionByApplication(Long applicationId) {
        return decisionRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new NoSuchElementException("No decision found for application: " + applicationId));
    }

    @CircuitBreaker(name = "documentServiceCB", fallbackMethod = "verifyDocumentFallback")
    @Retry(name = "documentServiceCB")
    public Object verifyDocumentViaFeign(Long documentId, Long adminId, String remarks) {
        String finalRemarks = remarks == null ? "Verified by admin" : remarks;

        Object result = documentClient.updateDocumentStatus(
                documentId,
                Map.of("status", "VERIFIED", "remarks", finalRemarks),
                "admin-service",
                String.valueOf(adminId)
        );

        audit(adminId, "VERIFY_DOC", "DOCUMENT", documentId,
              "Verified document " + documentId + ". Remarks: " + finalRemarks);

        return result;
    }

    public Object verifyDocumentFallback(Long documentId, Long adminId, String remarks, Throwable t) {
        log.error("Fallback: service unavailable during verifyDocument {}", documentId, t);
        throw new RuntimeException("Service unavailable during document verification", t);
    }

    @CircuitBreaker(name = "documentServiceCB", fallbackMethod = "rejectDocumentFallback")
    @Retry(name = "documentServiceCB")
    public Object rejectDocumentViaFeign(Long documentId, Long adminId, String remarks) {
        String finalRemarks = remarks == null ? "Rejected by admin" : remarks;

        Object result = documentClient.updateDocumentStatus(
                documentId,
                Map.of("status", "REJECTED", "remarks", finalRemarks),
                "admin-service",
                String.valueOf(adminId)
        );

        audit(adminId, "REJECT_DOC", "DOCUMENT", documentId,
              "Rejected document " + documentId + ". Remarks: " + finalRemarks);

        return result;
    }

    public Object rejectDocumentFallback(Long documentId, Long adminId, String remarks, Throwable t) {
        log.error("Fallback: service unavailable during rejectDocument {}", documentId, t);
        throw new RuntimeException("Service unavailable during document rejection", t);
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    public List<Map<String, Object>> getAllUsers() {
        return authServiceClient.getAllUsers("admin-service");
    }

    public Map<String, Object> updateUser(Long id, Map<String, Object> updateRequest) {
        return authServiceClient.updateUser(id, updateRequest, "admin-service");
    }

    private void audit(Long adminId, String action, String entity, Long targetId, String summary) {
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .actionType(action)
                .targetEntity(entity)
                .targetId(targetId)
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

    private void verifyDocumentsApproved(Long applicationId, Long adminId, String roles) {
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
    }

    private Decision.DecisionStatus parseDecisionStatus(String decision) {
        if (decision == null) {
            throw new IllegalArgumentException("decision is required");
        }

        try {
            Decision.DecisionStatus parsed = Decision.DecisionStatus.valueOf(decision.toUpperCase());
            if (parsed != Decision.DecisionStatus.APPROVED && parsed != Decision.DecisionStatus.REJECTED) {
                throw new IllegalArgumentException("Only APPROVED or REJECTED decisions are supported");
            }
            return parsed;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid decision: " + decision);
        }
    }

    private void validateApprovalTerms(DecisionRequest request) {
        if (request.getApprovedAmount() == null) {
            throw new IllegalArgumentException("approvedAmount is required for APPROVED decisions");
        }
        if (request.getApprovedTenureMonths() == null) {
            throw new IllegalArgumentException("approvedTenureMonths is required for APPROVED decisions");
        }
        if (request.getInterestRate() == null) {
            throw new IllegalArgumentException("interestRate is required for APPROVED decisions");
        }
    }

    private void sendLoanDecisionEmail(Map<String, Object> application, Decision decision, String remarks) {
        Long applicantId = toLong(application.get("applicantId"));
        if (applicantId == null) {
            log.warn("Applicant ID not found for application {}. Skipping loan decision email.",
                    decision.getApplicationId());
            return;
        }

        try {
            Map<String, Object> user = authServiceClient.getUser(applicantId, "admin-service");
            notificationEmailService.sendLoanDecisionEmailQuietly(user, application, decision, remarks);
        } catch (Exception ex) {
            log.warn("Could not prepare loan decision email for application {}: {}",
                    decision.getApplicationId(), ex.getMessage());
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private List<String> buildSortParams(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return List.of("createdAt,desc");
        }
        return pageable.getSort().stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                .toList();
    }
}
