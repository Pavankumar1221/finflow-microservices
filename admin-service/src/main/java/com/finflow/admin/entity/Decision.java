package com.finflow.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "decisions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, unique = true)
    private Long applicationId;

    @Column(name = "decided_by", nullable = false)
    private Long decidedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false)
    private DecisionStatus decisionStatus;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "approved_tenure_months")
    private Integer approvedTenureMonths;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(columnDefinition = "TEXT")
    private String terms;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @CreationTimestamp
    @Column(name = "decision_at", updatable = false)
    private LocalDateTime decisionAt;

    public enum DecisionStatus { APPROVED, REJECTED, CONDITIONAL }
}
