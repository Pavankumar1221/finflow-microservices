package com.finflow.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionRequest {

    @NotBlank(message = "decision is required")
    private String decision;

    private String remarks;

    private BigDecimal approvedAmount;

    private Integer approvedTenureMonths;

    private BigDecimal interestRate;

    private String terms;
}
