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
public class DocumentResponse {
    private Long id;
    private Long applicationId;
    private Long uploadedBy;
    private String documentType;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String verificationStatus;
    private LocalDateTime uploadedAt;
    private LocalDateTime verifiedAt;
    // Note: filePath is intentionally excluded from response for security
}
