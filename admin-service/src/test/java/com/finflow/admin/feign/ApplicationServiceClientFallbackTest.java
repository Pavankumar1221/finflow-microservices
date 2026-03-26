package com.finflow.admin.feign;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ApplicationServiceClientFallbackTest {

    private final ApplicationServiceClientFallback fallback = new ApplicationServiceClientFallback();

    @Test
    void getApplication() {
        Map<String, Object> map = fallback.getApplication(1L, "1", "ROLE");
        assertNotNull(map);
        assertTrue(map.containsKey("error"));
    }

    @Test
    void getApplicationStatus() {
        Map<String, Object> map = fallback.getApplicationStatus(1L, "1", "ROLE");
        assertNotNull(map);
        assertTrue(map.containsKey("error"));
    }

    @Test
    void getAllApplications() {
        List<Object> list = fallback.getAllApplications("1", "ROLE");
        assertTrue(list.isEmpty());
    }

    @Test
    void updateApplicationStatus() {
        Object obj = fallback.updateApplicationStatus(1L, "S", "R", "1", "ROLE");
        assertNotNull(obj);
    }

    @Test
    void getReports() {
        Map<String, Object> map = fallback.getReports("internal");
        assertNotNull(map);
        assertTrue(map.containsKey("error"));
    }
}
