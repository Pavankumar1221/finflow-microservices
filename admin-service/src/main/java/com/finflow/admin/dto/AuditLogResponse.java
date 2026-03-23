package com.finflow.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    private Long id;
    private Long adminId;
    private String actionType;
    private String targetEntity;
    private Long targetId;
    private String actionSummary;
    private LocalDateTime actionAt;
}
