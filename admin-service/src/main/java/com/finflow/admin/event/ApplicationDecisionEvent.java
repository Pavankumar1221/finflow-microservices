package com.finflow.admin.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDecisionEvent {
    private Long applicationId;
    private String status;
    private Long adminId;
    private String remarks;
    private String timestamp;
}
