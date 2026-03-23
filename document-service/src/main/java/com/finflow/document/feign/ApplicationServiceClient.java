package com.finflow.document.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "application-service", path = "/applications")
public interface ApplicationServiceClient {

    @GetMapping("/{id}")
    Map<String, Object> getApplication(
            @PathVariable("id") Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Roles") String roles);
}
