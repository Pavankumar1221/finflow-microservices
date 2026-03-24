package com.finflow.admin.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "application-service", path = "/applications",
             fallback = ApplicationServiceClientFallback.class)
public interface ApplicationServiceClient {

    @GetMapping("/{id}")
    Map<String, Object> getApplication(@PathVariable Long id,
                                       @RequestHeader("X-User-Id") String userId,
                                       @RequestHeader("X-User-Roles") String roles);

    @GetMapping("/{id}/status")
    Map<String, Object> getApplicationStatus(@PathVariable Long id,
                                             @RequestHeader("X-User-Id") String userId,
                                             @RequestHeader("X-User-Roles") String roles);

    @GetMapping
    List<Object> getAllApplications(@RequestHeader("X-User-Id") String userId,
                                    @RequestHeader("X-User-Roles") String roles);

    @PutMapping("/{id}/status")
    Object updateApplicationStatus(@PathVariable Long id,
                                   @RequestParam String toStatus,
                                   @RequestParam String remarks,
                                   @RequestHeader("X-User-Id") String userId,
                                   @RequestHeader("X-User-Roles") String roles);

    @GetMapping("/internal/reports")
    Map<String, Object> getReports(@RequestHeader("X-Internal-Call") String internalCallHeader);
}
