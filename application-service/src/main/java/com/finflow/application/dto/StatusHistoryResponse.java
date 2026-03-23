package com.finflow.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistoryResponse {
    private Long id;
    private Long applicationId;
    private String fromStatus;
    private String toStatus;
    private Long changedBy;
    private String changedByRole;
    private String remarks;
    private LocalDateTime changedAt;
}
