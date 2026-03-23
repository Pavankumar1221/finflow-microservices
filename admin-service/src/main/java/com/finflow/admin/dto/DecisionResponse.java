package com.finflow.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionResponse {
    private Long id;
    private Long applicationId;
    private Long decidedBy;
    private String decisionStatus;
    private BigDecimal approvedAmount;
    private Integer approvedTenureMonths;
    private BigDecimal interestRate;
    private String terms;
    private String decisionReason;
    private LocalDateTime decisionAt;
}
