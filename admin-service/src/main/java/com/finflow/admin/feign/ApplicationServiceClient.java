package com.finflow.admin.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

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
    Map<String, Object> getAllApplications(@RequestHeader("X-User-Id") String userId,
                                           @RequestHeader("X-User-Roles") String roles,
                                           @RequestParam("page") int page,
                                           @RequestParam("size") int size,
                                           @RequestParam(value = "sort", required = false) List<String> sort);

    @SuppressWarnings("unchecked")
    default List<Object> getAllApplications(String userId, String roles) {
        Object content = getAllApplications(userId, roles, 0, Integer.MAX_VALUE, null).get("content");
        return content instanceof List<?> list ? (List<Object>) list : List.of();
    }

    @PutMapping("/{id}/status")
    Object updateApplicationStatus(@PathVariable Long id,
                                   @RequestParam String toStatus,
                                   @RequestParam String remarks,
                                   @RequestHeader("X-User-Id") String userId,
                                   @RequestHeader("X-User-Roles") String roles);

    @GetMapping("/internal/reports")
    Map<String, Object> getReports(@RequestHeader("X-Internal-Call") String internalCallHeader);
}
