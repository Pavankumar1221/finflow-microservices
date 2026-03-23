package com.finflow.document.mapper;

import com.finflow.document.dto.DocumentResponse;
import com.finflow.document.dto.VerificationHistoryResponse;
import com.finflow.document.entity.Document;
import com.finflow.document.entity.DocumentVerificationHistory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DocumentMapper {

    public DocumentResponse toResponse(Document d) {
        if (d == null) return null;
        return DocumentResponse.builder()
                .id(d.getId())
                .applicationId(d.getApplicationId())
                .uploadedBy(d.getUploadedBy())
                .documentType(d.getDocumentType())
                .fileName(d.getFileName())
                .mimeType(d.getMimeType())
                .fileSize(d.getFileSize())
                .verificationStatus(d.getVerificationStatus() != null
                        ? d.getVerificationStatus().name() : null)
                .uploadedAt(d.getUploadedAt())
                .verifiedAt(d.getVerifiedAt())
                // filePath intentionally not exposed
                .build();
    }

    public VerificationHistoryResponse toResponse(DocumentVerificationHistory h) {
        if (h == null) return null;
        return VerificationHistoryResponse.builder()
                .id(h.getId())
                .documentId(h.getDocumentId())
                .verifiedBy(h.getVerifiedBy())
                .status(h.getStatus() != null ? h.getStatus().name() : null)
                .remarks(h.getRemarks())
                .verifiedAt(h.getVerifiedAt())
                .build();
    }

    public List<DocumentResponse> toResponseList(List<Document> docs) {
        return docs.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<VerificationHistoryResponse> toHistoryList(List<DocumentVerificationHistory> list) {
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }
}
