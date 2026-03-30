package com.finflow.application.listener;

import com.finflow.application.entity.ApplicationStatusHistory;
import com.finflow.application.entity.LoanApplication;
import com.finflow.application.entity.LoanApplication.ApplicationStatus;
import com.finflow.application.repository.ApplicationStatusHistoryRepository;
import com.finflow.application.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentVerifiedListener {

    private final LoanApplicationRepository applicationRepo;
    private final ApplicationStatusHistoryRepository historyRepo;
    private final CacheManager cacheManager;

    @RabbitListener(queues = "documents.verified.queue")
    @Transactional
    public void handleDocumentsVerified(Map<String, Object> event) {
        log.info("Received DOCUMENTS_VERIFIED event: {}", event);

        if (event == null || !event.containsKey("applicationId")) {
            log.warn("Received invalid event format. Missing applicationId.");
            return;
        }

        Object appIdObj = event.get("applicationId");
        Long appId;
        if (appIdObj instanceof Number) {
            appId = ((Number) appIdObj).longValue();
        } else {
            try {
                appId = Long.parseLong(appIdObj.toString());
            } catch (NumberFormatException e) {
                log.warn("Invalid applicationId format: {}", appIdObj);
                return;
            }
        }

        LoanApplication app = applicationRepo.findById(appId).orElse(null);
        if (app == null) {
            log.warn("Received document verified event for unknown application: {}", appId);
            return;
        }
        if (app.isDeleted()) {
            log.warn("Application {} is soft-deleted. Skipping document-verified event.", appId);
            return;
        }

        ApplicationStatus current = app.getStatus();
        // Idempotency: do not overwrite if already DOCS_VERIFIED, APPROVED, or REJECTED
        if (current == ApplicationStatus.DOCS_VERIFIED || current == ApplicationStatus.APPROVED
                || current == ApplicationStatus.REJECTED || current == ApplicationStatus.CANCELLED) {
            log.warn("Application {} already processed (status: {}). Skipping event.", appId, current);
            return;
        }

        String from = current.name();
        app.setStatus(ApplicationStatus.DOCS_VERIFIED);
        app.setCurrentStage("Under Review");
        applicationRepo.save(app);

        historyRepo.save(ApplicationStatusHistory.builder()
                .applicationId(appId)
                .fromStatus(from)
                .toStatus(ApplicationStatus.DOCS_VERIFIED.name())
                .changedBy(null)
                .changedByRole("SYSTEM")
                .remarks("All documents verified")
                .build());

        evictApplicationCache();

        log.info("Application {} status updated to DOCS_VERIFIED via RabbitMQ event", appId);
    }

    private void evictApplicationCache() {
        Cache cache = cacheManager.getCache("applications-cache");
        if (cache != null) {
            cache.clear();
        }
    }
}
