package com.finflow.admin.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentEventListener {

    @RabbitListener(queues = "documents.verified.queue")
    public void handleDocumentsVerified(Map<String, Object> event) {
        log.info("Received DOCUMENTS_VERIFIED event: {}", event);
        Object applicationId = event.get("applicationId");
        log.info("All documents verified for application {}. Admin review phase started.", applicationId);
        // Here you could trigger notifications to admins, update review queue, etc.
    }
}
