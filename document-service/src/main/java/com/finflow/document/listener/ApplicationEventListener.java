package com.finflow.document.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationEventListener {

    @RabbitListener(queues = "application.submitted.queue")
    public void handleApplicationSubmitted(Map<String, Object> event) {
        log.info("Received APPLICATION_SUBMITTED event: {}", event);
        Object applicationId = event.get("applicationId");
        Object applicationNumber = event.get("applicationNumber");
        log.info("Document checklist initialized for application: {} ({})",
                applicationNumber, applicationId);
        // Initialize document checklist logic can be added here
        // e.g., create placeholder entries for required document types
    }
}
