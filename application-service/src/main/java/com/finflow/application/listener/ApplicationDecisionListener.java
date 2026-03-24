package com.finflow.application.listener;

import com.finflow.application.entity.ApplicationStatusHistory;
import com.finflow.application.entity.LoanApplication;
import com.finflow.application.entity.LoanApplication.ApplicationStatus;
import com.finflow.application.event.ApplicationDecisionEvent;
import com.finflow.application.repository.ApplicationStatusHistoryRepository;
import com.finflow.application.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationDecisionListener {

    private final LoanApplicationRepository applicationRepo;
    private final ApplicationStatusHistoryRepository historyRepo;

    @RabbitListener(queues = "application.decision.queue")
    @Transactional
    public void handleDecision(ApplicationDecisionEvent event) {
        if (event == null || event.getApplicationId() == null) {
            log.warn("Received null or malformed ApplicationDecisionEvent");
            return;
        }

        Long appId = event.getApplicationId();
        LoanApplication app = applicationRepo.findById(appId).orElse(null);
        if (app == null) {
            log.warn("Received decision event for unknown application: {}", appId);
            return;
        }

        // Idempotency: skip if already in a finalized state
        ApplicationStatus current = app.getStatus();
        if (current == ApplicationStatus.APPROVED || current == ApplicationStatus.REJECTED) {
            log.warn("Application {} already finalized as {}. Skipping duplicate event.", appId, current);
            return;
        }

        ApplicationStatus targetStatus;
        try {
            targetStatus = ApplicationStatus.valueOf(event.getStatus());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Received invalid status '{}' for application: {}", event.getStatus(), appId);
            return;
        }

        String from = current.name();
        app.setStatus(targetStatus);
        applicationRepo.save(app);

        historyRepo.save(ApplicationStatusHistory.builder()
                .applicationId(appId)
                .fromStatus(from)
                .toStatus(event.getStatus())
                .changedBy(event.getAdminId())
                .changedByRole("ROLE_ADMIN")
                .remarks(event.getRemarks())
                .build());

        log.info("Application {} status updated to {} via RabbitMQ event from admin {}", appId, event.getStatus(), event.getAdminId());
    }
}
