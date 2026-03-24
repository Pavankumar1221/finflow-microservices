package com.finflow.application.controller;

import com.finflow.application.dto.*;
import com.finflow.application.entity.*;
import com.finflow.application.mapper.ApplicationMapper;
import com.finflow.application.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
@Tag(name = "Application Controller", description = "Loan Application lifecycle management")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationMapper mapper;

    @PostMapping("/draft")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Create a draft loan application")
    public ResponseEntity<LoanApplicationResponse> createDraft(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long applicantId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toResponse(applicationService.createDraft(applicantId)));
    }

    @PutMapping("/{id}/personal")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Save personal details")
    public ResponseEntity<PersonalDetailsResponse> savePersonal(
            @PathVariable Long id, @RequestBody ApplicantPersonalDetails details) {
        return ResponseEntity.ok(mapper.toResponse(applicationService.savePersonalDetails(id, details)));
    }

    @PutMapping("/{id}/employment")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Save employment details")
    public ResponseEntity<EmploymentDetailsResponse> saveEmployment(
            @PathVariable Long id, @RequestBody EmploymentDetails details) {
        return ResponseEntity.ok(mapper.toResponse(applicationService.saveEmploymentDetails(id, details)));
    }

    @PutMapping("/{id}/loan-details")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Save loan details")
    public ResponseEntity<LoanDetailsResponse> saveLoanDetails(
            @PathVariable Long id, @RequestBody LoanDetails details) {
        return ResponseEntity.ok(mapper.toResponse(applicationService.saveLoanDetails(id, details)));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Submit a draft application (triggers RabbitMQ event)")
    public ResponseEntity<LoanApplicationResponse> submit(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(mapper.toResponse(applicationService.submitApplication(id, userId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full application details")
    public ResponseEntity<FullApplicationResponse> getApplication(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ResponseEntity.ok(applicationService.getFullApplicationDto(id, mapper, userId, roles));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get application status and history")
    public ResponseEntity<ApplicationStatusResponse> getStatus(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ResponseEntity.ok(applicationService.getStatusDto(id, mapper, userId, roles));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Get all applications for logged-in user")
    public ResponseEntity<List<LoanApplicationResponse>> getMyApplications(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long applicantId) {
        return ResponseEntity.ok(mapper.toResponseList(applicationService.getApplicationsByApplicant(applicantId)));
    }

    @GetMapping
    @Operation(summary = "Get all applications")
    public ResponseEntity<List<LoanApplicationResponse>> getAllApplications(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ResponseEntity.ok(mapper.toResponseList(applicationService.getAllApplications(roles)));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update application status (internal / Admin use)")
    public ResponseEntity<LoanApplicationResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam String toStatus,
            @RequestParam String remarks,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toResponse(
                applicationService.updateStatus(id, toStatus, userId, roles, remarks)));
    }

    @GetMapping("/internal/reports")
    @Operation(summary = "Get aggregated internal reports", hidden = true)
    public ResponseEntity<Map<String, Object>> getReports(
            @RequestHeader(value = "X-Internal-Call", required = false) String internalCall,
            @RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost) {
        
        if (forwardedHost != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // Block external Gateway requests
        }
        if (!"admin-service".equals(internalCall)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(applicationService.getReports());
    }
}
