package com.finflow.admin.service;

import com.finflow.admin.feign.ApplicationServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ApplicationServiceClient applicationClient;

    public Map<String, Object> getAggregatedReports() {
        return applicationClient.getReports("admin-service");
    }
}
