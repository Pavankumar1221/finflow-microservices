package com.finflow.application.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "loan_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, unique = true)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type")
    private LoanType loanType;

    @Column(name = "loan_amount_requested", nullable = false, precision = 15, scale = 2)
    @Positive(message = "Loan amount requested must be greater than 0")
    private BigDecimal loanAmountRequested;

    @Column(name = "tenure_months", nullable = false)
    @Positive(message = "Tenure months must be greater than 0")
    private Integer tenureMonths;

    @Column(columnDefinition = "TEXT")
    @NotBlank(message = "Loan purpose is required")
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_type")
    private RepaymentType repaymentType;

    public enum LoanType { PERSONAL, HOME, VEHICLE, EDUCATION, BUSINESS }
    public enum RepaymentType { EMI, BULLET, FLEXIBLE }
}
