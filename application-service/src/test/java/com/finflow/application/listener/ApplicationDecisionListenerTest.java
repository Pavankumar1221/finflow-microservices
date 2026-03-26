package com.finflow.application.listener;

import com.finflow.application.entity.ApplicationStatusHistory;
import com.finflow.application.entity.LoanApplication;
import com.finflow.application.entity.LoanApplication.ApplicationStatus;
import com.finflow.application.event.ApplicationDecisionEvent;
import com.finflow.application.repository.ApplicationStatusHistoryRepository;
import com.finflow.application.repository.LoanApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationDecisionListenerTest {

    @Mock
    private LoanApplicationRepository applicationRepo;

    @Mock
    private ApplicationStatusHistoryRepository historyRepo;

    @InjectMocks
    private ApplicationDecisionListener listener;

    @Test
    void handleDecision_NullEvent_DoesNothing() {
        listener.handleDecision(null);
        verify(applicationRepo, never()).findById(any());
    }

    @Test
    void handleDecision_NullApplicationId_DoesNothing() {
        listener.handleDecision(ApplicationDecisionEvent.builder().build());
        verify(applicationRepo, never()).findById(any());
    }

    @Test
    void handleDecision_AppNotFound_DoesNothing() {
        ApplicationDecisionEvent event = ApplicationDecisionEvent.builder().applicationId(1L).build();
        when(applicationRepo.findById(1L)).thenReturn(Optional.empty());

        listener.handleDecision(event);

        verify(applicationRepo, never()).save(any());
    }

    @Test
    void handleDecision_AppAlreadyApproved_DoesNothing() {
        ApplicationDecisionEvent event = ApplicationDecisionEvent.builder().applicationId(1L).build();
        LoanApplication app = LoanApplication.builder().status(ApplicationStatus.APPROVED).build();
        when(applicationRepo.findById(1L)).thenReturn(Optional.of(app));

        listener.handleDecision(event);

        verify(applicationRepo, never()).save(any());
    }

    @Test
    void handleDecision_AppAlreadyRejected_DoesNothing() {
        ApplicationDecisionEvent event = ApplicationDecisionEvent.builder().applicationId(1L).build();
        LoanApplication app = LoanApplication.builder().status(ApplicationStatus.REJECTED).build();
        when(applicationRepo.findById(1L)).thenReturn(Optional.of(app));

        listener.handleDecision(event);

        verify(applicationRepo, never()).save(any());
    }

    @Test
    void handleDecision_InvalidStatus_DoesNothing() {
        ApplicationDecisionEvent event = ApplicationDecisionEvent.builder().applicationId(1L).status("INVALID_STATUS").build();
        LoanApplication app = LoanApplication.builder().status(ApplicationStatus.SUBMITTED).build();
        when(applicationRepo.findById(1L)).thenReturn(Optional.of(app));

        listener.handleDecision(event);

        verify(applicationRepo, never()).save(any());
    }

    @Test
    void handleDecision_ValidStatus_UpdatesAndSaves() {
        ApplicationDecisionEvent event = ApplicationDecisionEvent.builder()
                .applicationId(1L)
                .status("APPROVED")
                .adminId(99L)
                .remarks("Looks good")
                .build();
        LoanApplication app = LoanApplication.builder().status(ApplicationStatus.SUBMITTED).build();
        when(applicationRepo.findById(1L)).thenReturn(Optional.of(app));

        listener.handleDecision(event);

        verify(applicationRepo, times(1)).save(app);
        verify(historyRepo, times(1)).save(any(ApplicationStatusHistory.class));
    }
}
