package com.finflow.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApproveRequest {

    @NotNull(message = "Approved amount is required")
    @DecimalMin(value = "1000.00", message = "Approved amount must be at least 1000")
    private BigDecimal approvedAmount;

    @NotNull(message = "Approved tenure is required")
    @Min(value = 1, message = "Tenure must be at least 1 month")
    private Integer approvedTenureMonths;

    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "0.01", message = "Interest rate must be positive")
    private BigDecimal interestRate;

    private String terms;

    private String decisionReason;
}
