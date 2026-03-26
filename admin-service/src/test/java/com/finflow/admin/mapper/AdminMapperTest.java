package com.finflow.admin.mapper;

import com.finflow.admin.dto.AuditLogResponse;
import com.finflow.admin.dto.DecisionResponse;
import com.finflow.admin.entity.AdminAuditLog;
import com.finflow.admin.entity.Decision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class AdminMapperTest {

    private final AdminMapper mapper = new AdminMapper();

    @Test
    void toResponse_Decision_Valid() {
        Decision d = new Decision();
        d.setId(1L);
        d.setApplicationId(10L);
        d.setDecidedBy(100L);
        d.setDecisionStatus(Decision.DecisionStatus.APPROVED);
        d.setApprovedAmount(BigDecimal.TEN);
        d.setApprovedTenureMonths(12);
        d.setInterestRate(BigDecimal.ONE);
        d.setTerms("Terms");
        d.setDecisionReason("OK");
        d.setDecisionAt(LocalDateTime.now());

        DecisionResponse resp = mapper.toResponse(d);
        assertNotNull(resp);
        assertEquals(1L, resp.getId());
        assertEquals("APPROVED", resp.getDecisionStatus());
        assertEquals(BigDecimal.TEN, resp.getApprovedAmount());
    }

    @Test
    void toResponse_Decision_Null() {
        assertNull(mapper.toResponse((Decision) null));
    }

    @Test
    void toResponse_AuditLog_Valid() {
        AdminAuditLog log = new AdminAuditLog();
        log.setId(1L);
        log.setAdminId(10L);
        log.setActionType("APPROVE");
        log.setTargetEntity("APP");
        log.setTargetId(100L);
        log.setActionSummary("Summary");
        log.setActionAt(LocalDateTime.now());

        AuditLogResponse resp = mapper.toResponse(log);
        assertNotNull(resp);
        assertEquals(1L, resp.getId());
        assertEquals("APPROVE", resp.getActionType());
    }

    @Test
    void toResponse_AuditLog_Null() {
        assertNull(mapper.toResponse((AdminAuditLog) null));
    }
}
