package com.finflow.document.service;

import com.finflow.document.config.RabbitMQConfig;
import com.finflow.document.entity.AllowedDocumentType;
import com.finflow.document.entity.Document;
import com.finflow.document.entity.Document.VerificationStatus;
import com.finflow.document.entity.DocumentVerificationHistory;
import com.finflow.document.exception.AccessDeniedException;
import com.finflow.document.feign.ApplicationServiceClient;
import com.finflow.document.feign.AuthServiceClient;
import com.finflow.document.repository.DocumentRepository;
import com.finflow.document.repository.DocumentVerificationHistoryRepository;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
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
import java.util.NoSuchElementException;
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
    private final AuthServiceClient authServiceClient;
    private final NotificationEmailService notificationEmailService;

    @Value("${document.upload.path}")
    private String uploadPath;

    @CircuitBreaker(name = "applicationServiceCB", fallbackMethod = "uploadDocumentFallback")
    @Retry(name = "applicationServiceCB")
    @org.springframework.cache.annotation.CacheEvict(value = "documents-cache", allEntries = true)
    public Document uploadDocument(Long applicationId, Long uploadedBy, String roles,
                                   String documentType, MultipartFile file) throws IOException {
        AllowedDocumentType type;
        try {
            type = AllowedDocumentType.valueOf(documentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid document type");
        }

        if (uploadedBy == null || roles == null) {
            throw new AccessDeniedException("Missing authentication headers");
        }
        if (roles.contains("ROLE_ADMIN")) {
            throw new AccessDeniedException("Admins cannot upload documents");
        }

        validateAccess(applicationId, uploadedBy, roles);

        java.util.Optional<Document> existingOpt =
                documentRepository.findByApplicationIdAndDocumentType(applicationId, type.name());

        Path uploadDir = Paths.get(uploadPath, String.valueOf(applicationId));
        Files.createDirectories(uploadDir);

        String uniqueFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadDir.resolve(uniqueFileName);
        Files.write(filePath, file.getBytes());

        Document doc;
        if (existingOpt.isPresent()) {
            doc = existingOpt.get();
            deleteFileIfExists(doc.getFilePath());

            historyRepository.save(DocumentVerificationHistory.builder()
                    .documentId(doc.getId())
                    .verifiedBy(uploadedBy)
                    .status(VerificationStatus.PENDING)
                    .remarks("Replaced document file")
                    .build());

            doc.setFileName(file.getOriginalFilename());
            doc.setFilePath(filePath.toString());
            doc.setMimeType(file.getContentType());
            doc.setFileSize(file.getSize());
            doc.setVerificationStatus(VerificationStatus.PENDING);
            doc.setUploadedAt(LocalDateTime.now());
        } else {
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

    public Document uploadDocumentFallback(Long applicationId, Long uploadedBy, String roles,
                                           String documentType, MultipartFile file, Throwable t) throws IOException {
        log.error("Fallback: service unavailable during uploadDocument {}", applicationId, t);
        throw new RuntimeException("Service unavailable during document upload", t);
    }

    @CircuitBreaker(name = "applicationServiceCB", fallbackMethod = "validateAccessFallback")
    @Retry(name = "applicationServiceCB")
    public void validateAccess(Long applicationId, Long userId, String roles) {
        if (userId == null || roles == null) {
            throw new AccessDeniedException("Missing authentication headers");
        }
        if (roles.contains("ROLE_ADMIN")) {
            return;
        }

        try {
            applicationClient.getApplication(applicationId, userId, roles);
        } catch (FeignException.Forbidden | FeignException.NotFound e) {
            throw new AccessDeniedException("Access Denied: You do not own this application");
        }
    }

    public void validateAccessFallback(Long applicationId, Long userId, String roles, Throwable t) {
        log.error("Fallback: service unavailable during validateAccess {}", applicationId, t);
        throw new RuntimeException("Service unavailable during document access validation", t);
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "documents-cache", key = "'app-' + #applicationId")
    public List<Document> getDocumentsByApplication(Long applicationId, Long userId, String roles) {
        validateAccess(applicationId, userId, roles);
        return documentRepository.findByApplicationId(applicationId);
    }

    @Transactional(readOnly = true)
    public Map<String, List<String>> getRequiredDocumentsStatus(Long applicationId, Long userId, String roles) {
        validateAccess(applicationId, userId, roles);
        List<String> required = Arrays.asList(
                AllowedDocumentType.AADHAR.name(),
                AllowedDocumentType.PAN.name(),
                AllowedDocumentType.INCOME.name());
        List<String> uploaded = documentRepository.findByApplicationId(applicationId)
                .stream()
                .map(Document::getDocumentType)
                .toList();
        List<String> pending = required.stream().filter(type -> !uploaded.contains(type)).toList();

        return Map.of(
                "required", required,
                "uploaded", uploaded,
                "pending", pending
        );
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "documents-cache", key = "#documentId")
    public Document getDocumentById(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));
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

    @Transactional(readOnly = true)
    public Resource getFileForViewing(Long documentId, Long userId, String roles) throws IOException {
        Document doc = getDocument(documentId, userId, roles);

        Path filePath = Paths.get(doc.getFilePath());
        if (!Files.exists(filePath)) {
            log.error("File not found on disk for document {}: {}", documentId, filePath);
            throw new IllegalStateException("File not found on disk: " + filePath);
        }

        log.info("Serving document {} ({}): {}", documentId, doc.getDocumentType(), filePath.getFileName());
        return new UrlResource(filePath.toUri());
    }

    @org.springframework.cache.annotation.CacheEvict(value = "documents-cache", allEntries = true)
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
                .documentId(documentId)
                .verifiedBy(verifiedBy)
                .status(status)
                .remarks(remarks)
                .build());

        if (status == VerificationStatus.VERIFIED || status == VerificationStatus.REJECTED) {
            sendDocumentNotification(doc, remarks);
        }

        if (status == VerificationStatus.VERIFIED) {
            checkAndPublishAllVerified(doc.getApplicationId());
        }
        log.info("Document {} status updated to {} internally by {}", documentId, status, verifiedBy);
        return saved;
    }

    @org.springframework.cache.annotation.CacheEvict(value = "documents-cache", allEntries = true)
    public void deleteDocument(Long documentId, Long userId, String roles) throws IOException {
        requireApplicantRole(roles);

        Document doc = getDocument(documentId, userId, roles);
        deleteFileIfExists(doc.getFilePath());
        historyRepository.deleteByDocumentId(documentId);
        documentRepository.delete(doc);
        log.info("Document {} deleted by user {}", documentId, userId);
    }

    private void checkAndPublishAllVerified(Long applicationId) {
        List<Document> docs = documentRepository.findByApplicationId(applicationId);
        boolean allVerified = docs.stream()
                .allMatch(document -> document.getVerificationStatus() == VerificationStatus.VERIFIED);

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

    private void requireApplicantRole(String roles) {
        if (roles == null || !roles.contains("ROLE_APPLICANT")) {
            throw new AccessDeniedException("Only applicants can delete documents");
        }
    }

    private void deleteFileIfExists(String filePath) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }

    private void sendDocumentNotification(Document doc, String remarks) {
        try {
            Map<String, Object> user = authServiceClient.getUser(doc.getUploadedBy(), "document-service");
            notificationEmailService.sendDocumentStatusEmailQuietly(user, doc, remarks);
        } catch (Exception ex) {
            log.warn("Could not prepare document notification email for document {}: {}",
                    doc.getId(), ex.getMessage());
        }
    }
}
