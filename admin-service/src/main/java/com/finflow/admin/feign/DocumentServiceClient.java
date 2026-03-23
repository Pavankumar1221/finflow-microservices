package com.finflow.admin.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "document-service", path = "/documents",
             fallback = DocumentServiceClientFallback.class)
public interface DocumentServiceClient {

    @GetMapping("/application/{applicationId}")
    List<Object> getDocumentsByApplication(@PathVariable Long applicationId,
                                           @RequestHeader("X-User-Id") String userId,
                                           @RequestHeader("X-User-Roles") String roles);

    @PutMapping("/internal/{id}/status")
    Object updateDocumentStatus(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Internal-Call") String internalCall,
            @RequestHeader("X-User-Id") String userId
    );
}
