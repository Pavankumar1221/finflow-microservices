package com.finflow.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.application.config.RabbitMQConfig;
import com.finflow.application.dto.*;
import com.finflow.application.entity.*;
import com.finflow.application.entity.LoanApplication.ApplicationStatus;
import com.finflow.application.event.ApplicationSubmittedEvent;
import com.finflow.application.exception.AccessDeniedException;
import com.finflow.application.mapper.ApplicationMapper;
import com.finflow.application.repository.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
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

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
    public ApplicantPersonalDetails patchPersonalDetails(Long appId, Long userId, String roles, Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("At least one personal-details field must be provided");
        }
        LoanApplication app = getSecuredApplication(appId, userId, roles);
        ApplicantPersonalDetails details = personalRepo.findByApplicationId(app.getId())
                .orElseGet(() -> ApplicantPersonalDetails.builder().applicationId(app.getId()).build());

        updates.forEach((field, value) -> applyPersonalField(details, field, value));
        validatePersonalDetails(details);
        return personalRepo.save(details);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
    public ApplicantPersonalDetails savePersonalDetails(Long appId, Long userId, String roles, ApplicantPersonalDetails details) {
        LoanApplication app = getSecuredApplication(appId, userId, roles);

        ApplicantPersonalDetails entity = personalRepo.findByApplicationId(app.getId())
                .orElseGet(() -> ApplicantPersonalDetails.builder().applicationId(app.getId()).build());

        entity.setFirstName(details.getFirstName());
        entity.setLastName(details.getLastName());
        entity.setDob(details.getDob());
        entity.setGender(details.getGender());
        entity.setMaritalStatus(details.getMaritalStatus());
        entity.setAddressLine1(details.getAddressLine1());
        entity.setAddressLine2(details.getAddressLine2());
        entity.setCity(details.getCity());
        entity.setState(details.getState());
        entity.setPincode(details.getPincode());
        entity.setNationality(details.getNationality());
        validatePersonalDetails(entity);
        return personalRepo.save(entity);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
    public ApplicantPersonalDetails savePersonalDetails(Long appId, ApplicantPersonalDetails details) {
        LoanApplication app = getApplication(appId);
        return savePersonalDetails(appId, app.getApplicantId(), "ROLE_APPLICANT", details);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
    public EmploymentDetails patchEmploymentDetails(Long appId, Long userId, String roles, Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("At least one employment-details field must be provided");
        }
        LoanApplication app = getSecuredApplication(appId, userId, roles);
        EmploymentDetails details = employmentRepo.findByApplicationId(app.getId())
                .orElseGet(() -> EmploymentDetails.builder().applicationId(app.getId()).build());

        updates.forEach((field, value) -> applyEmploymentField(details, field, value));
        validateEmploymentDetails(details);
        return employmentRepo.save(details);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
    public EmploymentDetails saveEmploymentDetails(Long appId, Long userId, String roles, EmploymentDetails details) {
        LoanApplication app = getSecuredApplication(appId, userId, roles);

        EmploymentDetails entity = employmentRepo.findByApplicationId(app.getId())
                .orElseGet(() -> EmploymentDetails.builder().applicationId(app.getId()).build());

        entity.setEmploymentType(details.getEmploymentType());
        entity.setCompanyName(details.getCompanyName());
        entity.setDesignation(details.getDesignation());
        entity.setMonthlyIncome(details.getMonthlyIncome());
        entity.setTotalWorkExperience(details.getTotalWorkExperience());
        entity.setOfficeAddress(details.getOfficeAddress());
        entity.setEmploymentStatus(details.getEmploymentStatus());
        validateEmploymentDetails(entity);
        return employmentRepo.save(entity);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
    public EmploymentDetails saveEmploymentDetails(Long appId, EmploymentDetails details) {
        LoanApplication app = getApplication(appId);
        return saveEmploymentDetails(appId, app.getApplicantId(), "ROLE_APPLICANT", details);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
    public LoanDetails patchLoanDetails(Long appId, Long userId, String roles, Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("At least one loan-details field must be provided");
        }
        LoanApplication app = getSecuredApplication(appId, userId, roles);
        LoanDetails details = loanDetailsRepo.findByApplicationId(app.getId())
                .orElseGet(() -> LoanDetails.builder().applicationId(app.getId()).build());

        updates.forEach((field, value) -> applyLoanField(details, field, value));
        validateLoanDetails(details);
        return loanDetailsRepo.save(details);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
    public LoanDetails saveLoanDetails(Long appId, Long userId, String roles, LoanDetails details) {
        LoanApplication app = getSecuredApplication(appId, userId, roles);

        LoanDetails entity = loanDetailsRepo.findByApplicationId(app.getId())
                .orElseGet(() -> LoanDetails.builder().applicationId(app.getId()).build());

        entity.setLoanType(details.getLoanType());
        entity.setLoanAmountRequested(details.getLoanAmountRequested());
        entity.setTenureMonths(details.getTenureMonths());
        entity.setPurpose(details.getPurpose());
        entity.setRepaymentType(details.getRepaymentType());
        validateLoanDetails(entity);
        return loanDetailsRepo.save(entity);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
    public LoanDetails saveLoanDetails(Long appId, LoanDetails details) {
        LoanApplication app = getApplication(appId);
        return saveLoanDetails(appId, app.getApplicantId(), "ROLE_APPLICANT", details);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
    public LoanApplication submitApplication(Long appId, Long userId, String roles) {
        LoanApplication app = getSecuredApplication(appId, userId, roles);
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

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
    public LoanApplication submitApplication(Long appId, Long userId) {
        return submitApplication(appId, userId, "ROLE_APPLICANT");
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "applications-cache", key = "#appId")
    public LoanApplication getApplication(Long appId) {
        return applicationRepo.findByIdAndDeletedFalse(appId)
                .orElseThrow(() -> new NoSuchElementException("Application not found with id: " + appId));
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
    @org.springframework.cache.annotation.Cacheable(value = "applications-cache", key = "'status-' + #appId")
    public ApplicationStatusResponse getStatusDto(Long appId, ApplicationMapper mapper, Long userId, String roles) {
        LoanApplication app = getSecuredApplication(appId, userId, roles);
        List<ApplicationStatusHistory> history = historyRepo.findByApplicationIdOrderByChangedAtDesc(appId);
        return ApplicationStatusResponse.builder()
                .currentStatus(app.getStatus().name())
                .history(mapper.toHistoryResponseList(history))
                .build();
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(
            value = "applications-cache",
            key = "'applicant-' + #applicantId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()")
    public Page<LoanApplication> getApplicationsByApplicant(Long applicantId, Pageable pageable) {
        return applicationRepo.findByApplicantIdAndDeletedFalse(applicantId, pageable);
    }

    @Transactional(readOnly = true)
    public List<LoanApplication> getApplicationsByApplicant(Long applicantId) {
        return getApplicationsByApplicant(applicantId, PageRequest.of(0, 1000)).getContent();
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(
            value = "applications-cache",
            key = "'all-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()")
    public Page<LoanApplication> getAllApplications(String roles, Pageable pageable) {
        if (roles == null || !roles.contains("ROLE_ADMIN")) {
            throw new AccessDeniedException("Access Denied: Only administrators can view all applications");
        }
        return applicationRepo.findByDeletedFalse(pageable);
    }

    @Transactional(readOnly = true)
    public List<LoanApplication> getAllApplications(String roles) {
        return getAllApplications(roles, PageRequest.of(0, 1000)).getContent();
    }

    @Transactional(readOnly = true)
    public LoanApplication getSecuredApplication(Long appId, Long userId, String roles) {
        LoanApplication app = getApplication(appId);
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

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
    public void softDeleteApplication(Long appId, Long userId, String roles) {
        LoanApplication app = getSecuredApplication(appId, userId, roles);
        if (app.isDeleted()) {
            return;
        }

        String from = app.getStatus().name();
        app.setDeleted(true);
        app.setStatus(ApplicationStatus.CANCELLED);
        app.setCurrentStage("Cancelled");
        applicationRepo.save(app);

        recordHistory(appId, from, ApplicationStatus.CANCELLED.name(), userId, "ROLE_APPLICANT",
                "Application cancelled by applicant");
    }

    @org.springframework.cache.annotation.CacheEvict(value = "applications-cache", allEntries = true)
    public LoanApplication updateStatus(Long appId, String toStatus, Long changedBy, String role, String remarks) {
        LoanApplication app = getApplication(appId);
        String from = app.getStatus().name();
        app.setStatus(ApplicationStatus.valueOf(toStatus));
        LoanApplication saved = applicationRepo.save(app);
        recordHistory(appId, from, toStatus, changedBy, role, remarks);
        return saved;
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
        long total = applicationRepo.countByDeletedFalse();
        long draft = applicationRepo.countByStatusAndDeletedFalse(ApplicationStatus.DRAFT);
        long submitted = applicationRepo.countByStatusAndDeletedFalse(ApplicationStatus.SUBMITTED);
        long verified = applicationRepo.countByStatusAndDeletedFalse(ApplicationStatus.DOCS_VERIFIED);
        long approved = applicationRepo.countByStatusAndDeletedFalse(ApplicationStatus.APPROVED);
        long rejected = applicationRepo.countByStatusAndDeletedFalse(ApplicationStatus.REJECTED);

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

    private void applyPersonalField(ApplicantPersonalDetails details, String field, Object value) {
        switch (field) {
            case "firstName" -> details.setFirstName(asString(value));
            case "lastName" -> details.setLastName(asString(value));
            case "dob" -> details.setDob(asLocalDate(value));
            case "gender" -> details.setGender(asEnum(ApplicantPersonalDetails.Gender.class, value));
            case "maritalStatus" -> details.setMaritalStatus(asEnum(ApplicantPersonalDetails.MaritalStatus.class, value));
            case "addressLine1" -> details.setAddressLine1(asString(value));
            case "addressLine2" -> details.setAddressLine2(asString(value));
            case "city" -> details.setCity(asString(value));
            case "state" -> details.setState(asString(value));
            case "pincode" -> details.setPincode(asString(value));
            case "nationality" -> details.setNationality(asString(value));
            default -> throw new IllegalArgumentException("Unsupported personal-details field: " + field);
        }
    }

    private void applyEmploymentField(EmploymentDetails details, String field, Object value) {
        switch (field) {
            case "employmentType" -> details.setEmploymentType(asEnum(EmploymentDetails.EmploymentType.class, value));
            case "companyName" -> details.setCompanyName(asString(value));
            case "designation" -> details.setDesignation(asString(value));
            case "monthlyIncome" -> details.setMonthlyIncome(asBigDecimal(value));
            case "totalWorkExperience" -> details.setTotalWorkExperience(asInteger(value));
            case "officeAddress" -> details.setOfficeAddress(asString(value));
            case "employmentStatus" -> details.setEmploymentStatus(asEnum(EmploymentDetails.EmploymentStatus.class, value));
            default -> throw new IllegalArgumentException("Unsupported employment-details field: " + field);
        }
    }

    private void applyLoanField(LoanDetails details, String field, Object value) {
        switch (field) {
            case "loanType" -> details.setLoanType(asEnum(LoanDetails.LoanType.class, value));
            case "loanAmountRequested" -> details.setLoanAmountRequested(asBigDecimal(value));
            case "tenureMonths" -> details.setTenureMonths(asInteger(value));
            case "purpose" -> details.setPurpose(asString(value));
            case "repaymentType" -> details.setRepaymentType(asEnum(LoanDetails.RepaymentType.class, value));
            default -> throw new IllegalArgumentException("Unsupported loan-details field: " + field);
        }
    }

    private String asString(Object value) {
        return value == null ? null : objectMapper.convertValue(value, String.class);
    }

    private Integer asInteger(Object value) {
        return value == null ? null : objectMapper.convertValue(value, Integer.class);
    }

    private BigDecimal asBigDecimal(Object value) {
        return value == null ? null : objectMapper.convertValue(value, BigDecimal.class);
    }

    private LocalDate asLocalDate(Object value) {
        return value == null ? null : LocalDate.parse(asString(value));
    }

    private <E extends Enum<E>> E asEnum(Class<E> enumType, Object value) {
        if (value == null) {
            return null;
        }
        return Enum.valueOf(enumType, asString(value).toUpperCase());
    }

    private void validatePersonalDetails(ApplicantPersonalDetails details) {
        validateBean(details);
        if (details.getDob() == null) {
            throw new IllegalArgumentException("dob: Date of birth is required");
        }
        if (Period.between(details.getDob(), LocalDate.now()).getYears() < 18) {
            throw new IllegalArgumentException("dob: Applicant must be at least 18 years old");
        }
    }

    private void validateEmploymentDetails(EmploymentDetails details) {
        validateBean(details);
    }

    private void validateLoanDetails(LoanDetails details) {
        validateBean(details);
    }

    private <T> void validateBean(T bean) {
        Set<ConstraintViolation<T>> violations = validator.validate(bean);
        if (violations.isEmpty()) {
            return;
        }
        String message = violations.stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        throw new IllegalArgumentException(message);
    }
}
