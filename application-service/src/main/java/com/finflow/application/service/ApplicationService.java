package com.finflow.application.service;

import com.finflow.application.config.RabbitMQConfig;
import com.finflow.application.dto.*;
import com.finflow.application.entity.*;
import com.finflow.application.entity.LoanApplication.ApplicationStatus;
import com.finflow.application.event.ApplicationSubmittedEvent;
import com.finflow.application.exception.AccessDeniedException;
import com.finflow.application.mapper.ApplicationMapper;
import com.finflow.application.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApplicationService {

    private final LoanApplicationRepository applicationRepo;
    private final PersonalDetailsRepository personalRepo;
    private final EmploymentDetailsRepository employmentRepo;
    private final LoanDetailsRepository loanDetailsRepo;
    private final ApplicationStatusHistoryRepository historyRepo;
    private final RabbitTemplate rabbitTemplate;

    public LoanApplication createDraft(Long applicantId) {
        String appNumber = generateAppNumber();
        LoanApplication app = LoanApplication.builder()
                .applicationNumber(appNumber)
                .applicantId(applicantId)
                .status(ApplicationStatus.DRAFT)
                .currentStage("Personal Details")
                .build();
        LoanApplication saved = applicationRepo.save(app);
        recordHistory(saved.getId(), null, ApplicationStatus.DRAFT.name(), applicantId, "System", "Draft created");
        log.info("Draft created: {}", saved.getApplicationNumber());
        return saved;
    }

    public ApplicantPersonalDetails savePersonalDetails(Long appId, ApplicantPersonalDetails details) {
        assertExists(appId);
        details.setApplicationId(appId);
        return personalRepo.findByApplicationId(appId)
                .map(existing -> { details.setId(existing.getId()); return personalRepo.save(details); })
                .orElseGet(() -> personalRepo.save(details));
    }

    public EmploymentDetails saveEmploymentDetails(Long appId, EmploymentDetails details) {
        assertExists(appId);
        details.setApplicationId(appId);
        return employmentRepo.findByApplicationId(appId)
                .map(existing -> { details.setId(existing.getId()); return employmentRepo.save(details); })
                .orElseGet(() -> employmentRepo.save(details));
    }

    public LoanDetails saveLoanDetails(Long appId, LoanDetails details) {
        assertExists(appId);
        details.setApplicationId(appId);
        return loanDetailsRepo.findByApplicationId(appId)
                .map(existing -> { details.setId(existing.getId()); return loanDetailsRepo.save(details); })
                .orElseGet(() -> loanDetailsRepo.save(details));
    }

    public LoanApplication submitApplication(Long appId, Long userId) {
        LoanApplication app = getApplication(appId);
        if (app.getStatus() != ApplicationStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT applications can be submitted");
        }
        String prev = app.getStatus().name();
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setSubmittedAt(LocalDateTime.now());
        app.setCurrentStage("Document Upload");
        LoanApplication saved = applicationRepo.save(app);

        recordHistory(appId, prev, ApplicationStatus.SUBMITTED.name(), userId, "ROLE_APPLICANT", "Submitted by applicant");

        ApplicationSubmittedEvent event = ApplicationSubmittedEvent.builder()
                .eventType("APPLICATION_SUBMITTED")
                .applicationId(saved.getId())
                .applicationNumber(saved.getApplicationNumber())
                .applicantId(saved.getApplicantId())
                .submittedAt(saved.getSubmittedAt())
                .build();

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING, event);
        log.info("Application submitted and event published: {}", saved.getApplicationNumber());
        return saved;
    }

    @Transactional(readOnly = true)
    public LoanApplication getApplication(Long appId) {
        return applicationRepo.findById(appId)
                .orElseThrow(() -> new RuntimeException("Application not found with id: " + appId));
    }

    @Transactional(readOnly = true)
    public FullApplicationResponse getFullApplicationDto(Long appId, ApplicationMapper mapper, Long userId, String roles) {
        LoanApplication app = getSecuredApplication(appId, userId, roles);
        return FullApplicationResponse.builder()
                .application(mapper.toResponse(app))
                .personalDetails(mapper.toResponse(personalRepo.findByApplicationId(appId).orElse(null)))
                .employmentDetails(mapper.toResponse(employmentRepo.findByApplicationId(appId).orElse(null)))
                .loanDetails(mapper.toResponse(loanDetailsRepo.findByApplicationId(appId).orElse(null)))
                .build();
    }

    @Transactional(readOnly = true)
    public ApplicationStatusResponse getStatusDto(Long appId, ApplicationMapper mapper, Long userId, String roles) {
        LoanApplication app = getSecuredApplication(appId, userId, roles);
        List<ApplicationStatusHistory> history = historyRepo.findByApplicationIdOrderByChangedAtDesc(appId);
        return ApplicationStatusResponse.builder()
                .currentStatus(app.getStatus().name())
                .history(mapper.toHistoryResponseList(history))
                .build();
    }

    @Transactional(readOnly = true)
    public List<LoanApplication> getApplicationsByApplicant(Long applicantId) {
        return applicationRepo.findByApplicantId(applicantId);
    }

    @Transactional(readOnly = true)
    public List<LoanApplication> getAllApplications(String roles) {
        if (roles == null || !roles.contains("ROLE_ADMIN")) {
            throw new AccessDeniedException("Access Denied: Only administrators can view all applications");
        }
        return applicationRepo.findAll();
    }

    @Transactional(readOnly = true)
    public LoanApplication getSecuredApplication(Long appId, Long userId, String roles) {
        LoanApplication app;
        if (roles != null && roles.contains("ROLE_ADMIN")) {
            app = applicationRepo.findById(appId)
                    .orElseThrow(() -> new RuntimeException("Application not found with id: " + appId));
        } else if (userId != null) {
            app = applicationRepo.findByIdAndApplicantId(appId, userId)
                    .orElseThrow(() -> new AccessDeniedException("Access Denied: Application not found or you do not have permission"));
        } else {
            throw new AccessDeniedException("Access Denied: Missing user identity");
        }
        validateOwnership(app, userId, roles);
        return app;
    }

    public void validateOwnership(LoanApplication app, Long userId, String roles) {
        if (roles != null && roles.contains("ROLE_ADMIN")) {
            return;
        }
        if (userId != null && userId.equals(app.getApplicantId())) {
            return;
        }
        throw new AccessDeniedException("Access Denied: You do not have permission to access this application");
    }

    public LoanApplication updateStatus(Long appId, String toStatus, Long changedBy, String role, String remarks) {
        LoanApplication app = getApplication(appId);
        String from = app.getStatus().name();
        app.setStatus(ApplicationStatus.valueOf(toStatus));
        LoanApplication saved = applicationRepo.save(app);
        recordHistory(appId, from, toStatus, changedBy, role, remarks);
        return saved;
    }

    private void assertExists(Long appId) {
        if (!applicationRepo.existsById(appId)) {
            throw new RuntimeException("Application not found with id: " + appId);
        }
    }

    private void recordHistory(Long appId, String from, String to, Long userId, String role, String remarks) {
        historyRepo.save(ApplicationStatusHistory.builder()
                .applicationId(appId).fromStatus(from).toStatus(to)
                .changedBy(userId).changedByRole(role).remarks(remarks)
                .build());
    }

    private String generateAppNumber() {
        String year = DateTimeFormatter.ofPattern("yyyy").format(LocalDateTime.now());
        long count = applicationRepo.count() + 1;
        return String.format("FINFLOW-%s-%05d", year, count);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getReports() {
        long total = applicationRepo.count();
        long draft = applicationRepo.findByStatus(ApplicationStatus.DRAFT).size();
        long submitted = applicationRepo.findByStatus(ApplicationStatus.SUBMITTED).size();
        long verified = applicationRepo.findByStatus(ApplicationStatus.DOCS_VERIFIED).size();
        long approved = applicationRepo.findByStatus(ApplicationStatus.APPROVED).size();
        long rejected = applicationRepo.findByStatus(ApplicationStatus.REJECTED).size();

        double approvalRate = total > 0 ? (approved * 100.0) / total : 0;
        double rejectionRate = total > 0 ? (rejected * 100.0) / total : 0;

        return java.util.Map.of(
            "totalApplications", total,
            "applicationsByStatus", java.util.Map.of(
                "DRAFT", draft,
                "SUBMITTED", submitted,
                "DOCS_VERIFIED", verified,
                "APPROVED", approved,
                "REJECTED", rejected
            ),
            "approvalRate", String.format("%.1f", approvalRate) + "%",
            "rejectionRate", String.format("%.1f", rejectionRate) + "%",
            "monthlyTrends", java.util.Map.of(
                "January", 10, "February", 15, "March", total
            )
        );
    }
}
