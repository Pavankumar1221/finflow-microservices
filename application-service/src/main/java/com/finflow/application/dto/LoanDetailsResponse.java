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
public class LoanDetailsResponse {
    private Long id;
    private Long applicationId;
    private String loanType;
    private BigDecimal loanAmountRequested;
    private Integer tenureMonths;
    private String purpose;
    private String repaymentType;
}
