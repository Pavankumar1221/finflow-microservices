package com.finflow.admin.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DocumentServiceClientFallback implements DocumentServiceClient {

    @Override
    public List<Object> getDocumentsByApplication(Long applicationId, String userId, String roles) {
        log.warn("Fallback: Document service unavailable for application {}", applicationId);
        return Collections.emptyList();
    }

    @Override
    public Object updateDocumentStatus(Long id, Map<String, String> body, String internalCall, String userId) {
        log.warn("Fallback: Cannot update document {}", id);
        return Map.of("error", "Document service unavailable");
    }
}
