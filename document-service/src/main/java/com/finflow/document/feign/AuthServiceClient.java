package com.finflow.document.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "auth-service", path = "/auth")
public interface AuthServiceClient {

    @GetMapping("/internal/users/{id}")
    Map<String, Object> getUser(@PathVariable("id") Long id,
                                @RequestHeader("X-Internal-Call") String internalCallHeader);
}
