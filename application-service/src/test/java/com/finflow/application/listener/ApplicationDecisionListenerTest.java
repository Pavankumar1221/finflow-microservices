package com.finflow.application.listener;

import com.finflow.application.entity.ApplicationStatusHistory;
import com.finflow.application.entity.LoanApplication;
import com.finflow.application.entity.LoanApplication.ApplicationStatus;
import com.finflow.application.event.ApplicationDecisionEvent;
import com.finflow.application.repository.ApplicationStatusHistoryRepository;
import com.finflow.application.repository.LoanApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApplicationDecisionListenerTest {

    @Mock
    private LoanApplicationRepository applicationRepo;

    @Mock
    private ApplicationStatusHistoryRepository historyRepo;

    @InjectMocks
    private ApplicationDecisionListener listener;

    private ApplicationDecisionEvent validEvent;
    private LoanApplication pendingApplication;
    private final Long VALID_APP_ID = 200L;

    @BeforeEach
    void setUp() {
        validEvent = new ApplicationDecisionEvent();
        validEvent.setApplicationId(VALID_APP_ID);
        validEvent.setAdminId(99L);
        validEvent.setStatus("APPROVED");
        validEvent.setRemarks("Looks good and verified.");

        pendingApplication = LoanApplication.builder()
                .id(VALID_APP_ID)
                .status(ApplicationStatus.DOCS_VERIFIED)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Valid Event Consumption
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void handleDecision_ValidEvent_UpdatesStatusAndHistory() {
        // Arrange
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.of(pendingApplication));
        when(applicationRepo.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        listener.handleDecision(validEvent);

        // Assert - Application Mutated Successfully
        assertEquals(ApplicationStatus.APPROVED, pendingApplication.getStatus());
        verify(applicationRepo).save(pendingApplication);

        // Assert - History captured correctly
        ArgumentCaptor<ApplicationStatusHistory> historyCaptor = ArgumentCaptor.forClass(ApplicationStatusHistory.class);
        verify(historyRepo).save(historyCaptor.capture());

        ApplicationStatusHistory capturedHistory = historyCaptor.getValue();
        assertEquals(VALID_APP_ID, capturedHistory.getApplicationId());
        assertEquals("DOCS_VERIFIED", capturedHistory.getFromStatus());
        assertEquals("APPROVED", capturedHistory.getToStatus());
        assertEquals(99L, capturedHistory.getChangedBy());
        assertEquals("ROLE_ADMIN", capturedHistory.getChangedByRole());
        assertEquals("Looks good and verified.", capturedHistory.getRemarks());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Handle Unknown Applications Gracefully (Silent return)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void handleDecision_WhenApplicationNotFound_DoesNothing() {
        // Arrange
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.empty());

        // Act
        listener.handleDecision(validEvent);

        // Assert
        verify(applicationRepo, never()).save(any());
        verify(historyRepo, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Requirement 1: REJECTED Event Flow
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void handleDecision_WhenRejectedEvent_UpdatesStatusAndHistory() {
        // Arrange
        validEvent.setStatus("REJECTED");
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.of(pendingApplication));
        when(applicationRepo.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        listener.handleDecision(validEvent);

        // Assert
        assertEquals(ApplicationStatus.REJECTED, pendingApplication.getStatus());
        verify(applicationRepo).save(pendingApplication);

        ArgumentCaptor<ApplicationStatusHistory> historyCaptor = ArgumentCaptor.forClass(ApplicationStatusHistory.class);
        verify(historyRepo).save(historyCaptor.capture());
        assertEquals("REJECTED", historyCaptor.getValue().getToStatus());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Requirement 2: Invalid Event Status Handling
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void handleDecision_WhenInvalidStatus_HandlesSafely() {
        // Arrange
        validEvent.setStatus("INVALID_STATUS_MOCK");
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.of(pendingApplication));

        // Act - Should complete safely without exception
        listener.handleDecision(validEvent);

        // Assert
        verify(applicationRepo, never()).save(any());
        verify(historyRepo, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Requirement 3: Null Event Handling
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void handleDecision_WhenEventIsNull_DoesNotCrash() {
        // Act - Should complete safely without NullPointerException
        listener.handleDecision(null);

        // Assert
        verify(applicationRepo, never()).findById(any());
    }

    @Test
    void handleDecision_WhenApplicationIdIsNull_DoesNotCrash() {
        // Arrange
        validEvent.setApplicationId(null);

        // Act
        listener.handleDecision(validEvent);

        // Assert
        verify(applicationRepo, never()).findById(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Requirement 4: Partial Event Data Handling
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void handleDecision_WhenPartialEventData_UpdatesStatusSafely() {
        // Arrange
        validEvent.setAdminId(null);
        validEvent.setRemarks(null);
        // application_id and status are fully present
        
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.of(pendingApplication));
        when(applicationRepo.save(any())).thenReturn(pendingApplication);

        // Act
        listener.handleDecision(validEvent);

        // Assert
        assertEquals(ApplicationStatus.APPROVED, pendingApplication.getStatus());
        
        ArgumentCaptor<ApplicationStatusHistory> historyCaptor = ArgumentCaptor.forClass(ApplicationStatusHistory.class);
        verify(historyRepo).save(historyCaptor.capture());
        
        assertNull(historyCaptor.getValue().getChangedBy()); // Correctly saved null
        assertNull(historyCaptor.getValue().getRemarks()); // Correctly saved null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Requirement 5 & Idempotency: Ignore Replay / Alternate Transitions
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void handleDecision_WhenAlreadyRejectedButReceivesApproveEvent_SkipsUpdate() {
        // Arrange - Pretend application was ALREADY REJECTED fully finalized previously
        pendingApplication.setStatus(ApplicationStatus.REJECTED);
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.of(pendingApplication));

        // Act - Receive an APPROVED late replay
        validEvent.setStatus("APPROVED");
        listener.handleDecision(validEvent);

        // Assert - Guarantee no overwrite
        assertEquals(ApplicationStatus.REJECTED, pendingApplication.getStatus()); // Must stay REJECTED
        verify(applicationRepo, never()).save(any());
        verify(historyRepo, never()).save(any());
    }

    @Test
    void handleDecision_WhenAlreadyApprovedButReceivesApproveEvent_SkipsUpdate() {
        // Arrange
        pendingApplication.setStatus(ApplicationStatus.APPROVED);
        when(applicationRepo.findById(VALID_APP_ID)).thenReturn(Optional.of(pendingApplication));

        // Act
        validEvent.setStatus("APPROVED");
        listener.handleDecision(validEvent);

        // Assert
        verify(applicationRepo, never()).save(any());
        verify(historyRepo, never()).save(any());
    }
}
