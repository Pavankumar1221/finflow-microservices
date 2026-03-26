package com.finflow.admin.service;

import com.finflow.admin.feign.ApplicationServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReportServiceTest {

    @Mock
    private ApplicationServiceClient applicationClient;

    @InjectMocks
    private ReportService reportService;

    @Test
    void getAggregatedReports_Success() {
        Map<String, Object> mockResponse = Map.of("totalLoans", 100);
        when(applicationClient.getReports("admin-service")).thenReturn(mockResponse);

        Map<String, Object> result = reportService.getAggregatedReports();

        assertNotNull(result);
        assertEquals(100, result.get("totalLoans"));
        verify(applicationClient, times(1)).getReports("admin-service");
    }

    @Test
    void getAggregatedReportsFallback_ReturnsErrorMap() {
        Map<String, Object> result = reportService.getAggregatedReportsFallback(new RuntimeException("Service down"));
        
        assertNotNull(result);
        assertEquals("Application service unavailable", result.get("error"));
    }
}
