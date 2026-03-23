package com.finflow.admin.controller;

import com.finflow.admin.dto.*;
import com.finflow.admin.mapper.AdminMapper;
import com.finflow.admin.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Controller", description = "Application review, decisions, document verification and audit")
public class AdminController {

    private final AdminService adminService;
    private final AdminMapper mapper;

    // ─── Applications ─────────────────────────────────────────────────────────

    @GetMapping("/applications")
    @Operation(summary = "Get all loan applications")
    public ResponseEntity<List<Object>> getAllApplications(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(adminService.getAllApplications(userId, roles));
    }

    @GetMapping("/applications/{id}/review")
    @Operation(summary = "Get full application + documents for review")
    public ResponseEntity<Map<String, Object>> reviewApplication(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(adminService.getApplicationForReview(id, userId, roles));
    }

    // ─── Decisions ────────────────────────────────────────────────────────────

    @PostMapping("/applications/{id}/approve")
    @Operation(summary = "Approve a loan application")
    public ResponseEntity<DecisionResponse> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApproveRequest request,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long adminId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toResponse(
                adminService.approveApplication(id, adminId, request, roles)));
    }

    @PostMapping("/applications/{id}/reject")
    @Operation(summary = "Reject a loan application")
    public ResponseEntity<DecisionResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long adminId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toResponse(
                adminService.rejectApplication(id, adminId, request, roles)));
    }

    @GetMapping("/applications/{id}/decision")
    @Operation(summary = "Get decision for a specific application")
    public ResponseEntity<DecisionResponse> getDecision(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(adminService.getDecisionByApplication(id)));
    }

    // ─── Document verification ────────────────────────────────────────────────

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

    // ─── Audit Log ────────────────────────────────────────────────────────────

    @GetMapping("/audit-log")
    @Operation(summary = "Get admin audit log (paginated)")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                adminService.getAuditLogs(PageRequest.of(page, size, Sort.by("actionAt").descending()))
                        .map(mapper::toResponse));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "Admin Service is UP", "service", "admin-service"));
    }
}
