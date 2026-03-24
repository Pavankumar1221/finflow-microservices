package com.finflow.document.service;

import com.finflow.document.config.RabbitMQConfig;
import com.finflow.document.entity.Document;
import com.finflow.document.entity.Document.VerificationStatus;
import com.finflow.document.entity.AllowedDocumentType;
import com.finflow.document.entity.DocumentVerificationHistory;
import com.finflow.document.exception.AccessDeniedException;
import com.finflow.document.feign.ApplicationServiceClient;
import com.finflow.document.repository.DocumentRepository;
import com.finflow.document.repository.DocumentVerificationHistoryRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVerificationHistoryRepository historyRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ApplicationServiceClient applicationClient;

    @Value("${document.upload.path}")
    private String uploadPath;

    public Document uploadDocument(Long applicationId, Long uploadedBy, String roles,
                                   String documentType, MultipartFile file) throws IOException {
        
        // 1. Validate Type
        AllowedDocumentType type;
        try {
            type = AllowedDocumentType.valueOf(documentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid document type");
        }

        // 2. Ownership verification
        if (uploadedBy == null || roles == null) {
            throw new AccessDeniedException("Missing authentication headers");
        }
        if (roles.contains("ROLE_ADMIN")) {
            throw new AccessDeniedException("Admins cannot upload documents");
        }
        try {
            applicationClient.getApplication(applicationId, uploadedBy, roles);
        } catch (FeignException.Forbidden e) {
            throw new AccessDeniedException("You do not own this application");
        } catch (FeignException.NotFound e) {
            throw new IllegalArgumentException("Application not found");
        }

        java.util.Optional<Document> existingOpt = documentRepository.findByApplicationIdAndDocumentType(applicationId, type.name());

        Path uploadDir = Paths.get(uploadPath, String.valueOf(applicationId));
        Files.createDirectories(uploadDir);

        String uniqueFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadDir.resolve(uniqueFileName);
        Files.write(filePath, file.getBytes());

        Document doc;
        if (existingOpt.isPresent()) {
            doc = existingOpt.get();
            historyRepository.save(DocumentVerificationHistory.builder()
                .documentId(doc.getId()).verifiedBy(uploadedBy)
                .status(VerificationStatus.PENDING).remarks("Replaced document file")
                .build());
            
            doc.setFileName(file.getOriginalFilename());
            doc.setFilePath(filePath.toString());
            doc.setMimeType(file.getContentType());
            doc.setFileSize(file.getSize());
            doc.setVerificationStatus(VerificationStatus.PENDING);
            doc.setUploadedAt(LocalDateTime.now());
        } else {
            // 4. Throttle limit
            if (documentRepository.countByApplicationId(applicationId) >= 3) {
                throw new IllegalArgumentException("All required documents already uploaded");
            }
            doc = Document.builder()
                    .applicationId(applicationId)
                    .uploadedBy(uploadedBy)
                    .documentType(type.name())
                    .fileName(file.getOriginalFilename())
                    .filePath(filePath.toString())
                    .mimeType(file.getContentType())
                    .fileSize(file.getSize())
                    .verificationStatus(VerificationStatus.PENDING)
                    .uploadedAt(LocalDateTime.now())
                    .build();
        }

        Document saved = documentRepository.save(doc);
        log.info("Document uploaded: {} for application {}", documentType, applicationId);
        return saved;
    }

    public void validateAccess(Long applicationId, Long userId, String roles) {
        if (userId == null || roles == null) {
            throw new AccessDeniedException("Missing authentication headers");
        }
        if (roles.contains("ROLE_ADMIN")) {
            return;
        }
        try {
            applicationClient.getApplication(applicationId, userId, roles);
        } catch (FeignException.Forbidden e) {
            throw new AccessDeniedException("Access Denied: You do not own this application");
        } catch (FeignException.NotFound e) {
            throw new IllegalArgumentException("Application not found");
        }
    }

    @Transactional(readOnly = true)
    public List<Document> getDocumentsByApplication(Long applicationId, Long userId, String roles) {
        validateAccess(applicationId, userId, roles);
        return documentRepository.findByApplicationId(applicationId);
    }

    @Transactional(readOnly = true)
    public Map<String, List<String>> getRequiredDocumentsStatus(Long applicationId, Long userId, String roles) {
        validateAccess(applicationId, userId, roles);
        List<String> required = Arrays.asList(AllowedDocumentType.AADHAR.name(), AllowedDocumentType.PAN.name(), AllowedDocumentType.INCOME.name());
        List<String> uploaded = documentRepository.findByApplicationId(applicationId)
                .stream().map(Document::getDocumentType).toList();
        List<String> pending = required.stream().filter(t -> !uploaded.contains(t)).toList();
        
        return Map.of(
            "required", required,
            "uploaded", uploaded,
            "pending", pending
        );
    }

    @Transactional(readOnly = true)
    public Document getDocumentById(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
    }

    public void validateDocumentAccess(Long documentId, Long userId, String roles) {
        Document doc = getDocumentById(documentId);
        try {
            validateAccess(doc.getApplicationId(), userId, roles);
        } catch (AccessDeniedException e) {
            if ("Missing authentication headers".equals(e.getMessage())) {
                throw e;
            }
            throw new AccessDeniedException("Unauthorized access to document");
        }
    }

    @Transactional(readOnly = true)
    public Document getDocument(Long documentId, Long userId, String roles) {
        validateDocumentAccess(documentId, userId, roles);
        return getDocumentById(documentId);
    }

    @Transactional(readOnly = true)
    public List<DocumentVerificationHistory> getVerificationHistory(Long documentId, Long userId, String roles) {
        getDocument(documentId, userId, roles);
        return historyRepository.findByDocumentIdOrderByVerifiedAtDesc(documentId);
    }

    /**
     * Loads the physical file from disk for inline viewing / download.
     *
     * @param documentId  the DB id of the document record
     * @return            the file as an InputStreamResource ready to stream to the client
     * @throws IOException        if the file cannot be read from disk
     * @throws RuntimeException   if the document record is not found in the DB
     * @throws IllegalStateException if the file path recorded in the DB no longer exists on disk
     */
    @Transactional(readOnly = true)
    public org.springframework.core.io.Resource getFileForViewing(Long documentId, Long userId, String roles) throws IOException {
        Document doc = getDocument(documentId, userId, roles);   // throws 404 if not in DB

        Path filePath = Paths.get(doc.getFilePath());
        if (!Files.exists(filePath)) {
            log.error("File not found on disk for document {}: {}", documentId, filePath);
            throw new IllegalStateException("File not found on disk: " + filePath);
        }

        log.info("Serving document {} ({}): {}", documentId, doc.getDocumentType(), filePath.getFileName());
        return new org.springframework.core.io.UrlResource(filePath.toUri());
    }

    public Document updateInternalStatus(Long documentId, String statusStr, String remarks, Long verifiedBy) {
        if (documentId == null || statusStr == null) {
            throw new IllegalArgumentException("Document ID and status cannot be null");
        }

        Document doc = getDocumentById(documentId);

        VerificationStatus status;
        try {
            status = VerificationStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + statusStr);
        }

        if (doc.getVerificationStatus() == status) {
            log.info("Document {} is already {}. Skipping duplicate update.", documentId, status);
            return doc;
        }
        
        doc.setVerificationStatus(status);
        doc.setVerifiedAt(LocalDateTime.now());
        Document saved = documentRepository.save(doc);

        historyRepository.save(DocumentVerificationHistory.builder()
                .documentId(documentId).verifiedBy(verifiedBy)
                .status(status).remarks(remarks)
                .build());

        if (status == VerificationStatus.VERIFIED) {
            checkAndPublishAllVerified(doc.getApplicationId());
        }
        log.info("Document {} status updated to {} internally by {}", documentId, status, verifiedBy);
        return saved;
    }

    private void checkAndPublishAllVerified(Long applicationId) {
        List<Document> docs = documentRepository.findByApplicationId(applicationId);
        boolean allVerified = docs.stream()
                .allMatch(d -> d.getVerificationStatus() == VerificationStatus.VERIFIED);

        if (allVerified && !docs.isEmpty()) {
            Map<String, Object> event = Map.of(
                    "eventType", "DOCUMENTS_VERIFIED",
                    "applicationId", applicationId,
                    "verifiedAt", LocalDateTime.now().toString()
            );
            rabbitTemplate.convertAndSend(RabbitMQConfig.DOC_EXCHANGE, RabbitMQConfig.DOC_VERIFIED_ROUTING, event);
            log.info("All documents verified for application {}. Event published.", applicationId);
        }
    }
}
