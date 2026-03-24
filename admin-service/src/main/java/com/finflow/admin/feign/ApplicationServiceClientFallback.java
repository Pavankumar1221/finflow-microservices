package com.finflow.admin.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ApplicationServiceClientFallback implements ApplicationServiceClient {

    @Override
    public Map<String, Object> getApplication(Long id, String userId, String roles) {
        log.warn("Fallback: Could not fetch application {}", id);
        return Map.of("error", "Application service unavailable", "applicationId", id);
    }

    @Override
    public Map<String, Object> getApplicationStatus(Long id, String userId, String roles) {
        log.warn("Fallback: Could not fetch application status {}", id);
        return Map.of("error", "Application service unavailable");
    }

    @Override
    public List<Object> getAllApplications(String userId, String roles) {
        log.warn("Fallback: Could not fetch all applications");
        return Collections.emptyList();
    }

    @Override
    public Object updateApplicationStatus(Long id, String toStatus, String remarks, String userId, String roles) {
        log.warn("Fallback: Could not update application status {}", id);
        return Map.of("error", "Application service unavailable");
    }

    @Override
    public Map<String, Object> getReports(String internalCallHeader) {
        log.warn("Fallback: Could not fetch reports");
        return Map.of("error", "Application service unavailable");
    }
}
