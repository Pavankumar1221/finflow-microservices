package com.finflow.admin.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "auth-service", path = "/auth")
public interface AuthServiceClient {

    @GetMapping("/internal/users")
    List<Map<String, Object>> getAllUsers(@RequestHeader("X-Internal-Call") String internalCallHeader);

    @GetMapping("/internal/users/{id}")
    Map<String, Object> getUser(@PathVariable Long id,
                                @RequestHeader("X-Internal-Call") String internalCallHeader);

    @PutMapping("/internal/users/{id}")
    Map<String, Object> updateUser(@PathVariable Long id,
                                   @RequestBody Map<String, Object> updateRequest,
                                   @RequestHeader("X-Internal-Call") String internalCallHeader);
}
