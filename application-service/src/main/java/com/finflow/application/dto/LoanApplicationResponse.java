package com.finflow.application.dto;

import com.finflow.application.entity.LoanApplication.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplicationResponse {
    private Long id;
    private String applicationNumber;
    private Long applicantId;
    private ApplicationStatus status;
    private String currentStage;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
