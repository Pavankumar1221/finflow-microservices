package com.finflow.admin.mapper;

import com.finflow.admin.dto.AuditLogResponse;
import com.finflow.admin.dto.DecisionResponse;
import com.finflow.admin.entity.AdminAuditLog;
import com.finflow.admin.entity.Decision;
import org.springframework.stereotype.Component;

@Component
public class AdminMapper {

    public DecisionResponse toResponse(Decision d) {
        if (d == null) return null;
        return DecisionResponse.builder()
                .id(d.getId())
                .applicationId(d.getApplicationId())
                .decidedBy(d.getDecidedBy())
                .decisionStatus(d.getDecisionStatus() != null ? d.getDecisionStatus().name() : null)
                .approvedAmount(d.getApprovedAmount())
                .approvedTenureMonths(d.getApprovedTenureMonths())
                .interestRate(d.getInterestRate())
                .terms(d.getTerms())
                .decisionReason(d.getDecisionReason())
                .decisionAt(d.getDecisionAt())
                .build();
    }

    public AuditLogResponse toResponse(AdminAuditLog log) {
        if (log == null) return null;
        return AuditLogResponse.builder()
                .id(log.getId())
                .adminId(log.getAdminId())
                .actionType(log.getActionType())
                .targetEntity(log.getTargetEntity())
                .targetId(log.getTargetId())
                .actionSummary(log.getActionSummary())
                .actionAt(log.getActionAt())
                .build();
    }
}
