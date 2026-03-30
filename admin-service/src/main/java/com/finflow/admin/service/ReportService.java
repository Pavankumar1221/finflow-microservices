package com.finflow.admin.service;

import com.finflow.admin.feign.ApplicationServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ApplicationServiceClient applicationClient;

    @CircuitBreaker(name = "applicationServiceCB", fallbackMethod = "getAggregatedReportsFallback")
    @Retry(name = "applicationServiceCB")
    @org.springframework.cache.annotation.Cacheable(value = "reports-cache")
    public Map<String, Object> getAggregatedReports() {
        return applicationClient.getReports("admin-service");
    }

    public Map<String, Object> getAggregatedReportsFallback(Throwable t) {
        return java.util.Collections.singletonMap("error", "Application service unavailable");
    }
}
