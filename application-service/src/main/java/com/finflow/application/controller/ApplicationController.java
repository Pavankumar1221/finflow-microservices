package com.finflow.application.controller;

import com.finflow.application.dto.*;
import com.finflow.application.entity.ApplicantPersonalDetails;
import com.finflow.application.entity.EmploymentDetails;
import com.finflow.application.entity.LoanDetails;
import com.finflow.application.mapper.ApplicationMapper;
import com.finflow.application.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @Operation(summary = "Save or replace personal details")
    public ResponseEntity<PersonalDetailsResponse> savePersonal(
            @PathVariable Long id,
            @Valid @RequestBody ApplicantPersonalDetails details,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toResponse(
                applicationService.savePersonalDetails(id, userId, roles, details)));
    }

    @PatchMapping("/{id}/personal")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Partially update personal details")
    public ResponseEntity<PersonalDetailsResponse> patchPersonal(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toResponse(
                applicationService.patchPersonalDetails(id, userId, roles, updates)));
    }

    @PutMapping("/{id}/employment")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Save or replace employment details")
    public ResponseEntity<EmploymentDetailsResponse> saveEmployment(
            @PathVariable Long id,
            @Valid @RequestBody EmploymentDetails details,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toResponse(
                applicationService.saveEmploymentDetails(id, userId, roles, details)));
    }

    @PatchMapping("/{id}/employment")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Partially update employment details")
    public ResponseEntity<EmploymentDetailsResponse> patchEmployment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toResponse(
                applicationService.patchEmploymentDetails(id, userId, roles, updates)));
    }

    @PutMapping("/{id}/loan-details")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Save or replace loan details")
    public ResponseEntity<LoanDetailsResponse> saveLoanDetails(
            @PathVariable Long id,
            @Valid @RequestBody LoanDetails details,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toResponse(
                applicationService.saveLoanDetails(id, userId, roles, details)));
    }

    @PatchMapping("/{id}/loan-details")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Partially update loan details")
    public ResponseEntity<LoanDetailsResponse> patchLoanDetails(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toResponse(
                applicationService.patchLoanDetails(id, userId, roles, updates)));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Submit a draft application (triggers RabbitMQ event)")
    public ResponseEntity<LoanApplicationResponse> submit(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toResponse(applicationService.submitApplication(id, userId, roles)));
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
    public ResponseEntity<Page<LoanApplicationResponse>> getMyApplications(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long applicantId,
            Pageable pageable) {
        return ResponseEntity.ok(
                applicationService.getApplicationsByApplicant(applicantId, pageable).map(mapper::toResponse));
    }

    @GetMapping
    @Operation(summary = "Get all applications")
    public ResponseEntity<Page<LoanApplicationResponse>> getAllApplications(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Roles", required = false) String roles,
            Pageable pageable) {
        return ResponseEntity.ok(applicationService.getAllApplications(roles, pageable).map(mapper::toResponse));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Soft delete an application owned by the logged-in applicant")
    public ResponseEntity<Void> deleteApplication(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        applicationService.softDeleteApplication(id, userId, roles);
        return ResponseEntity.noContent().build();
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
