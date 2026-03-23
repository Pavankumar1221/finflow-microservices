package com.finflow.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmploymentDetailsResponse {
    private Long id;
    private Long applicationId;
    private String employmentType;
    private String companyName;
    private String designation;
    private BigDecimal monthlyIncome;
    private Integer totalWorkExperience;
    private String officeAddress;
    private String employmentStatus;
}
