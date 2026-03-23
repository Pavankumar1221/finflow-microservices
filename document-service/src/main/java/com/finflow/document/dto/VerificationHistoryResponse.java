package com.finflow.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationHistoryResponse {
    private Long id;
    private Long documentId;
    private Long verifiedBy;
    private String status;
    private String remarks;
    private LocalDateTime verifiedAt;
}
