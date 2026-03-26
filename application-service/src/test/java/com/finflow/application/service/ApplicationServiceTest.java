package com.finflow.application.service;

import com.finflow.application.config.RabbitMQConfig;
import com.finflow.application.dto.ApplicationStatusResponse;
import com.finflow.application.dto.FullApplicationResponse;
import com.finflow.application.entity.*;
import com.finflow.application.entity.LoanApplication.ApplicationStatus;
import com.finflow.application.event.ApplicationSubmittedEvent;
import com.finflow.application.exception.AccessDeniedException;
import com.finflow.application.mapper.ApplicationMapper;
import com.finflow.application.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApplicationServiceTest {

    @Mock private LoanApplicationRepository applicationRepo;
    @Mock private PersonalDetailsRepository personalRepo;
    @Mock private EmploymentDetailsRepository employmentRepo;
    @Mock private LoanDetailsRepository loanDetailsRepo;
    @Mock private ApplicationStatusHistoryRepository historyRepo;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private ApplicationMapper mapper;

    @InjectMocks
    private ApplicationService applicationService;

    private LoanApplication draftApplication;
    private final Long VALID_APP_ID = 500L;
    private final Long VALID_USER_ID = 42L;

    @BeforeEach
    void setUp() {
        draftApplication = LoanApplication.builder()
                .id(VALID_APP_ID)
                .applicantId(VALID_USER_ID)
                .applicationNumber("FINFLOW-2026-00001")
                .status(ApplicationStatus.DRAFT)
                .currentStage("Document Upload")
                .build();
    }

    @Test
    void createDraft_ReturnsApp() {
        when(applicationRepo.count()).thenReturn(0L);
        when(applicationRepo.save(any(LoanApplication.class))).thenAnswer(i -> {
            LoanApplication app = i.getArgument(0);
            app.setId(1L);
            return app;
        });

        LoanApplication draft = applicationService.createDraft(VALID_USER_ID);

        assertNotNull(draft);
        assertEquals(ApplicationStatus.DRAFT, draft.getStatus());
        verify(historyRepo, times(1)).save(any(ApplicationStatusHistory.class));
    }

    @Test
    void savePersonalDetails_WhenEmpty_SavesNew() {
        ApplicantPersonalDetails details = new ApplicantPersonalDetails();
        when(applicationRepo.existsById(VALID_APP_ID)).thenReturn(true);
        when(personalRepo.findByApplicationId(VALID_APP_ID)).thenReturn(Optional.empty());
        when(personalRepo.save(any())).thenReturn(details);

        ApplicantPersonalDetails saved = applicationService.savePersonalDetails(VALID_APP_ID, details);

        assertNotNull(saved);
        assertEquals(VALID_APP_ID, details.getApplicationId());
        verify(personalRepo, times(1)).save(details);
    }

    @Test
    void savePersonalDetails_WhenPresent_UpdatesExisting() {
        ApplicantPersonalDetails details = new ApplicantPersonalDetails();
        ApplicantPersonalDetails existing = ApplicantPersonalDetails.builder().id(99L).build();
        when(applicationRepo.existsById(VALID_APP_ID)).thenReturn(true);
        when(personalRepo.findByApplicationId(VALID_APP_ID)).thenReturn(Optional.of(existing));
        when(personalRepo.save(any())).thenReturn(details);

        ApplicantPersonalDetails saved = applicationService.savePersonalDetails(VALID_APP_ID, details);

        assertEquals(99L, details.getId());
        verify(personalRepo, times(1)).save(details);
    }

    @Test
    void savePersonalDetails_WhenAppNotFound_ThrowsException() {
        when(applicationRepo.existsById(VALID_APP_ID)).thenReturn(false);
        assertThrows(RuntimeException.class, () -> applicationService.savePersonalDetails(VALID_APP_ID, new ApplicantPersonalDetails()));
    }

    @Test
    void saveEmploymentDetails_WhenEmpty_SavesNew() {
        EmploymentDetails details = new EmploymentDetails();
        when(applicationRepo.existsById(VALID_APP_ID)).thenReturn(true);
        when(employmentRepo.findByApplicationId(VALID_APP_ID)).thenReturn(Optional.empty());
        when(employmentRepo.save(any())).thenReturn(details);

        EmploymentDetails saved = applicationService.saveEmploymentDetails(VALID_APP_ID, details);

        assertEquals(VALID_APP_ID, details.getApplicationId());
        verify(employmentRepo, times(1)).save(details);
    }

    @Test
    void saveEmploymentDetails_WhenPresent_UpdatesExisting() {
        EmploymentDetails details = new EmploymentDetails();
        EmploymentDetails existing = EmploymentDetails.builder().id(99L).build();
        when(applicationRepo.existsById(VALID_APP_ID)).thenReturn(true);
        when(employmentRepo.findByApplicationId(VALID_APP_ID)).thenReturn(Optional.of(existing));
        when(employmentRepo.save(any())).thenReturn(details);

        applicationService.saveEmploymentDetails(VALID_APP_ID, details);
        assertEquals(99L, details.getId());
    }

    @Test
    void saveLoanDetails_WhenEmpty_SavesNew() {
        LoanDetails details = new LoanDetails();
        when(applicationRepo.existsById(VALID_APP_ID)).thenReturn(true);
        when(loanDetailsRepo.findByApplicationId(VALID_APP_ID)).thenReturn(Optional.empty());
        when(loanDetailsRepo.save(any())).thenReturn(details);

        LoanDetails saved = applicationService.saveLoanDetails(VALID_APP_ID, details);

        assertEquals(VALID_APP_ID, details.getApplicationId());
        verify(loanDetailsRepo, times(1)).save(details);
    }

    @Test
    void saveLoanDetails_WhenPresent_UpdatesExisting() {
        LoanDetails details = new LoanDetails();
        LoanDetails existing = LoanDetails.builder().id(99L).build();
        when(applicationRepo.existsById(VALID_APP_ID)).thenReturn(true);
        when(loanDetailsRepo.findByApplicationId(VALID_APP_ID)).thenReturn(Optional.of(existing));
        when(loanDetailsRepo.save(any())).thenReturn(details);

        applicationService.saveLoanDetails(VALID_APP_ID, details);
        assertEquals(99L, details.getId());
    }

    @Test
    void submitApplication_Valid_UpdatesAndPublishes() {
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.of(draftApplication));
        when(applicationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        LoanApplication submitted = applicationService.submitApplication(VALID_APP_ID, VALID_USER_ID);

        assertEquals(ApplicationStatus.SUBMITTED, submitted.getStatus());
        verify(historyRepo).save(any());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(ApplicationSubmittedEvent.class));
    }

    @Test
    void submitApplication_NotDraft_ThrowsException() {
        draftApplication.setStatus(ApplicationStatus.SUBMITTED);
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.of(draftApplication));

        assertThrows(RuntimeException.class, () -> applicationService.submitApplication(VALID_APP_ID, VALID_USER_ID));
    }

    @Test
    void getFullApplicationDto_AdminRole_Success() {
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.of(draftApplication));
        when(personalRepo.findByApplicationId(VALID_APP_ID)).thenReturn(Optional.empty());
        when(employmentRepo.findByApplicationId(VALID_APP_ID)).thenReturn(Optional.empty());
        when(loanDetailsRepo.findByApplicationId(VALID_APP_ID)).thenReturn(Optional.empty());

        FullApplicationResponse response = applicationService.getFullApplicationDto(VALID_APP_ID, mapper, null, "ROLE_ADMIN");
        assertNotNull(response);
    }

    @Test
    void getFullApplicationDto_ApplicantUser_Success() {
        when(applicationRepo.findByIdAndApplicantId(VALID_APP_ID, VALID_USER_ID)).thenReturn(Optional.of(draftApplication));
        
        FullApplicationResponse response = applicationService.getFullApplicationDto(VALID_APP_ID, mapper, VALID_USER_ID, "ROLE_APPLICANT");
        assertNotNull(response);
    }

    @Test
    void getSecuredApplication_MissingIdentity_ThrowsException() {
        assertThrows(AccessDeniedException.class, () -> applicationService.getSecuredApplication(VALID_APP_ID, null, null));
    }

    @Test
    void getStatusDto_Success() {
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.of(draftApplication));
        when(historyRepo.findByApplicationIdOrderByChangedAtDesc(VALID_APP_ID)).thenReturn(List.of());
        
        ApplicationStatusResponse response = applicationService.getStatusDto(VALID_APP_ID, mapper, null, "ROLE_ADMIN");
        assertNotNull(response);
    }

    @Test
    void getApplicationsByApplicant_Success() {
        when(applicationRepo.findByApplicantId(VALID_USER_ID)).thenReturn(List.of(draftApplication));
        assertEquals(1, applicationService.getApplicationsByApplicant(VALID_USER_ID).size());
    }

    @Test
    void getAllApplications_Admin_ReturnsList() {
        when(applicationRepo.findAll()).thenReturn(List.of(draftApplication));
        assertEquals(1, applicationService.getAllApplications("ROLE_ADMIN,ROLE_USER").size());
    }

    @Test
    void getAllApplications_NotAdmin_ThrowsException() {
        assertThrows(AccessDeniedException.class, () -> applicationService.getAllApplications("ROLE_APPLICANT"));
    }

    @Test
    void getAllApplications_NullRoles_ThrowsException() {
        assertThrows(AccessDeniedException.class, () -> applicationService.getAllApplications(null));
    }

    @Test
    void updateStatus_Success() {
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.of(draftApplication));
        when(applicationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        LoanApplication updated = applicationService.updateStatus(VALID_APP_ID, "APPROVED", 100L, "ROLE_ADMIN", "Approved it");

        assertEquals(ApplicationStatus.APPROVED, updated.getStatus());
        verify(historyRepo, times(1)).save(any(ApplicationStatusHistory.class));
    }

    @Test
    void getReports_Success() {
        when(applicationRepo.count()).thenReturn(10L);
        when(applicationRepo.findByStatus(ApplicationStatus.DRAFT)).thenReturn(List.of());
        when(applicationRepo.findByStatus(ApplicationStatus.SUBMITTED)).thenReturn(List.of());
        when(applicationRepo.findByStatus(ApplicationStatus.DOCS_VERIFIED)).thenReturn(List.of());
        when(applicationRepo.findByStatus(ApplicationStatus.APPROVED)).thenReturn(List.of(draftApplication)); // 1 approved
        when(applicationRepo.findByStatus(ApplicationStatus.REJECTED)).thenReturn(List.of()); // 0 rejected

        java.util.Map<String, Object> reports = applicationService.getReports();
        
        assertEquals(10L, reports.get("totalApplications"));
        assertEquals("10.0%", reports.get("approvalRate"));
        assertEquals("0.0%", reports.get("rejectionRate"));
    }

    @Test
    void validateOwnership_NotAdminNotOwner_ThrowsException() {
        assertThrows(AccessDeniedException.class, () -> applicationService.validateOwnership(draftApplication, 999L, "ROLE_APPLICANT"));
    }
}
