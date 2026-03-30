package com.finflow.admin.controller;

import com.finflow.admin.dto.AuditLogResponse;
import com.finflow.admin.dto.DecisionRequest;
import com.finflow.admin.dto.DecisionResponse;
import com.finflow.admin.mapper.AdminMapper;
import com.finflow.admin.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Controller", description = "Application review, decisions, document verification and audit")
public class AdminController {

    private final AdminService adminService;
    private final AdminMapper mapper;

    @GetMapping("/applications")
    @Operation(summary = "Get all loan applications")
    public ResponseEntity<Page<Object>> getAllApplications(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles,
            Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllApplications(userId, roles, pageable));
    }

    @GetMapping("/applications/{id}/review")
    @Operation(summary = "Get full application + documents for review")
    public ResponseEntity<Map<String, Object>> reviewApplication(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(adminService.getApplicationForReview(id, userId, roles));
    }

    @GetMapping("/applications/{id}/decision")
    @Operation(summary = "Get decision for a specific application")
    public ResponseEntity<DecisionResponse> getDecision(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(adminService.getDecisionByApplication(id)));
    }

    @PostMapping("/applications/{id}/decision")
    @Operation(summary = "Make a unified decision (APPROVED or REJECTED)")
    public ResponseEntity<DecisionResponse> makeDecision(
            @PathVariable Long id,
            @Valid @RequestBody DecisionRequest request,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long adminId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toResponse(
                adminService.makeDecision(id, adminId, request, roles)));
    }

    @GetMapping("/users")
    @Operation(summary = "Get all users with roles and details")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PutMapping("/users/{id}")
    @Operation(summary = "Update user role or status")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updateRequest) {
        return ResponseEntity.ok(adminService.updateUser(id, updateRequest));
    }

    @PutMapping("/documents/{documentId}/verify")
    @Operation(summary = "Verify a document via Admin")
    public ResponseEntity<Object> verifyDocument(
            @PathVariable Long documentId,
            @RequestBody Map<String, String> body,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long adminId) {
        return ResponseEntity.ok(adminService.verifyDocumentViaFeign(documentId, adminId, body.get("remarks")));
    }

    @PutMapping("/documents/{documentId}/reject")
    @Operation(summary = "Reject a document via Admin")
    public ResponseEntity<Object> rejectDocument(
            @PathVariable Long documentId,
            @RequestBody Map<String, String> body,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long adminId) {
        return ResponseEntity.ok(adminService.rejectDocumentViaFeign(documentId, adminId, body.get("remarks")));
    }

    @GetMapping("/audit-log")
    @Operation(summary = "Get admin audit log (paginated)")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLog(Pageable pageable) {
        return ResponseEntity.ok(adminService.getAuditLogs(pageable).map(mapper::toResponse));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "Admin Service is UP", "service", "admin-service"));
    }
}
