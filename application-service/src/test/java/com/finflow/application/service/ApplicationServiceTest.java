package com.finflow.application.service;

import com.finflow.application.config.RabbitMQConfig;
import com.finflow.application.entity.ApplicationStatusHistory;
import com.finflow.application.entity.LoanApplication;
import com.finflow.application.entity.LoanApplication.ApplicationStatus;
import com.finflow.application.event.ApplicationSubmittedEvent;
import com.finflow.application.repository.ApplicationStatusHistoryRepository;
import com.finflow.application.repository.EmploymentDetailsRepository;
import com.finflow.application.repository.LoanApplicationRepository;
import com.finflow.application.repository.LoanDetailsRepository;
import com.finflow.application.repository.PersonalDetailsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApplicationServiceTest {

    @Mock
    private LoanApplicationRepository applicationRepo;

    @Mock
    private PersonalDetailsRepository personalRepo;

    @Mock
    private EmploymentDetailsRepository employmentRepo;

    @Mock
    private LoanDetailsRepository loanDetailsRepo;

    @Mock
    private ApplicationStatusHistoryRepository historyRepo;

    @Mock
    private RabbitTemplate rabbitTemplate;

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

    // ─────────────────────────────────────────────────────────────────────────
    // Valid Application Submission
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void submitApplication_WhenValidDraft_UpdatesStatusAndPublishesEvent() {
        // Arrange
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.of(draftApplication));
        when(applicationRepo.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        LoanApplication submitted = applicationService.submitApplication(VALID_APP_ID, VALID_USER_ID);

        // Assert - Entity state changes
        assertNotNull(submitted);
        assertEquals(ApplicationStatus.SUBMITTED, submitted.getStatus());
        assertNotNull(submitted.getSubmittedAt());

        // Assert - Repositories invoked
        verify(applicationRepo).save(draftApplication);
        
        ArgumentCaptor<ApplicationStatusHistory> historyCaptor = ArgumentCaptor.forClass(ApplicationStatusHistory.class);
        verify(historyRepo).save(historyCaptor.capture());
        
        ApplicationStatusHistory history = historyCaptor.getValue();
        assertEquals(VALID_APP_ID, history.getApplicationId());
        assertEquals("DRAFT", history.getFromStatus());
        assertEquals("SUBMITTED", history.getToStatus());

        // Deep Event Validation (Requirement #4 alignment for ApplicationService)
        ArgumentCaptor<ApplicationSubmittedEvent> eventCaptor = ArgumentCaptor.forClass(ApplicationSubmittedEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.ROUTING),
                eventCaptor.capture()
        );

        ApplicationSubmittedEvent capturedEvent = eventCaptor.getValue();
        assertEquals("APPLICATION_SUBMITTED", capturedEvent.getEventType());
        assertEquals(VALID_APP_ID, capturedEvent.getApplicationId());
        assertEquals(VALID_USER_ID, capturedEvent.getApplicantId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State Constraints Validation
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void submitApplication_WhenNotDraft_ThrowsException() {
        // Arrange
        draftApplication.setStatus(ApplicationStatus.SUBMITTED); // Already submitted
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.of(draftApplication));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            applicationService.submitApplication(VALID_APP_ID, VALID_USER_ID);
        });

        assertEquals("Only DRAFT applications can be submitted", exception.getMessage());
        
        // Ensure no mutations or events were fired
        verify(applicationRepo, never()).save(any());
        verify(historyRepo, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edge Case: Application Not Found
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void submitApplication_WhenApplicationDoesNotExist_ThrowsException() {
        // Arrange
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            applicationService.submitApplication(VALID_APP_ID, VALID_USER_ID);
        });

        assertTrue(exception.getMessage().contains("Application not found"));
    }
}
