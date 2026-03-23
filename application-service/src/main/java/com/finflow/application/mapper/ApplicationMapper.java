package com.finflow.application.mapper;

import com.finflow.application.dto.*;
import com.finflow.application.entity.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ApplicationMapper {

    public LoanApplicationResponse toResponse(LoanApplication app) {
        if (app == null) return null;
        return LoanApplicationResponse.builder()
                .id(app.getId())
                .applicationNumber(app.getApplicationNumber())
                .applicantId(app.getApplicantId())
                .status(app.getStatus())
                .currentStage(app.getCurrentStage())
                .submittedAt(app.getSubmittedAt())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }

    public PersonalDetailsResponse toResponse(ApplicantPersonalDetails d) {
        if (d == null) return null;
        return PersonalDetailsResponse.builder()
                .id(d.getId())
                .applicationId(d.getApplicationId())
                .firstName(d.getFirstName())
                .lastName(d.getLastName())
                .dob(d.getDob())
                .gender(d.getGender() != null ? d.getGender().name() : null)
                .maritalStatus(d.getMaritalStatus() != null ? d.getMaritalStatus().name() : null)
                .addressLine1(d.getAddressLine1())
                .addressLine2(d.getAddressLine2())
                .city(d.getCity())
                .state(d.getState())
                .pincode(d.getPincode())
                .nationality(d.getNationality())
                .build();
    }

    public EmploymentDetailsResponse toResponse(EmploymentDetails e) {
        if (e == null) return null;
        return EmploymentDetailsResponse.builder()
                .id(e.getId())
                .applicationId(e.getApplicationId())
                .employmentType(e.getEmploymentType() != null ? e.getEmploymentType().name() : null)
                .companyName(e.getCompanyName())
                .designation(e.getDesignation())
                .monthlyIncome(e.getMonthlyIncome())
                .totalWorkExperience(e.getTotalWorkExperience())
                .officeAddress(e.getOfficeAddress())
                .employmentStatus(e.getEmploymentStatus() != null ? e.getEmploymentStatus().name() : null)
                .build();
    }

    public LoanDetailsResponse toResponse(LoanDetails l) {
        if (l == null) return null;
        return LoanDetailsResponse.builder()
                .id(l.getId())
                .applicationId(l.getApplicationId())
                .loanType(l.getLoanType() != null ? l.getLoanType().name() : null)
                .loanAmountRequested(l.getLoanAmountRequested())
                .tenureMonths(l.getTenureMonths())
                .purpose(l.getPurpose())
                .repaymentType(l.getRepaymentType() != null ? l.getRepaymentType().name() : null)
                .build();
    }

    public StatusHistoryResponse toResponse(ApplicationStatusHistory h) {
        if (h == null) return null;
        return StatusHistoryResponse.builder()
                .id(h.getId())
                .applicationId(h.getApplicationId())
                .fromStatus(h.getFromStatus())
                .toStatus(h.getToStatus())
                .changedBy(h.getChangedBy())
                .changedByRole(h.getChangedByRole())
                .remarks(h.getRemarks())
                .changedAt(h.getChangedAt())
                .build();
    }

    public List<LoanApplicationResponse> toResponseList(List<LoanApplication> apps) {
        return apps.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<StatusHistoryResponse> toHistoryResponseList(List<ApplicationStatusHistory> list) {
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }
}
