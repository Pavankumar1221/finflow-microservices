package com.finflow.document.service;

import com.finflow.document.entity.AllowedDocumentType;
import com.finflow.document.entity.Document;
import com.finflow.document.entity.Document.VerificationStatus;
import com.finflow.document.entity.DocumentVerificationHistory;
import com.finflow.document.exception.AccessDeniedException;
import com.finflow.document.feign.ApplicationServiceClient;
import com.finflow.document.repository.DocumentRepository;
import com.finflow.document.repository.DocumentVerificationHistoryRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentVerificationHistoryRepository historyRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private ApplicationServiceClient applicationClient;

    @InjectMocks
    private DocumentService documentService;

    private MultipartFile mockFile;

    @BeforeEach
    void setUp() throws IOException {
        ReflectionTestUtils.setField(documentService, "uploadPath", "target/test-uploads");
        mockFile = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());
        Files.createDirectories(Paths.get("target/test-uploads/1"));
    }

    @Test
    void uploadDocument_InvalidType_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            documentService.uploadDocument(1L, 1L, "ROLE_APPLICANT", "INVALID", mockFile));
    }

    @Test
    void uploadDocument_MissingHeaders_ThrowsException() {
        assertThrows(AccessDeniedException.class, () ->
            documentService.uploadDocument(1L, null, null, "AADHAR", mockFile));
    }

    @Test
    void uploadDocument_AdminRole_ThrowsException() {
        assertThrows(AccessDeniedException.class, () ->
            documentService.uploadDocument(1L, 1L, "ROLE_ADMIN", "AADHAR", mockFile));
    }

    @Test
    void uploadDocument_FeignForbidden_ThrowsException() {
        when(applicationClient.getApplication(any(), any(), any())).thenThrow(FeignException.Forbidden.class);
        assertThrows(AccessDeniedException.class, () ->
            documentService.uploadDocument(1L, 1L, "ROLE_APPLICANT", "AADHAR", mockFile));
    }

    @Test
    void uploadDocument_FeignNotFound_ThrowsException() {
        when(applicationClient.getApplication(any(), any(), any())).thenThrow(FeignException.NotFound.class);
        assertThrows(IllegalArgumentException.class, () ->
            documentService.uploadDocument(1L, 1L, "ROLE_APPLICANT", "AADHAR", mockFile));
    }

    @Test
    void uploadDocument_ThrottleLimitReached_ThrowsException() {
        when(documentRepository.findByApplicationIdAndDocumentType(1L, "AADHAR")).thenReturn(Optional.empty());
        when(documentRepository.countByApplicationId(1L)).thenReturn(3L);

        assertThrows(IllegalArgumentException.class, () ->
            documentService.uploadDocument(1L, 1L, "ROLE_APPLICANT", "AADHAR", mockFile));
    }

    @Test
    void uploadDocument_Existing_UpdatesAndSaves() throws IOException {
        Document existing = Document.builder().id(99L).build();
        when(documentRepository.findByApplicationIdAndDocumentType(1L, "AADHAR")).thenReturn(Optional.of(existing));
        when(documentRepository.save(any())).thenReturn(existing);

        Document doc = documentService.uploadDocument(1L, 1L, "ROLE_APPLICANT", "AADHAR", mockFile);
        assertNotNull(doc);
        verify(historyRepository, times(1)).save(any());
    }

    @Test
    void uploadDocument_New_Saves() throws IOException {
        when(documentRepository.findByApplicationIdAndDocumentType(1L, "AADHAR")).thenReturn(Optional.empty());
        when(documentRepository.countByApplicationId(1L)).thenReturn(0L);
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Document doc = documentService.uploadDocument(1L, 1L, "ROLE_APPLICANT", "AADHAR", mockFile);
        assertNotNull(doc);
    }

    @Test
    void uploadDocumentFallback_ThrowsException() {
        assertThrows(RuntimeException.class, () ->
            documentService.uploadDocumentFallback(1L, 1L, "ROLE_APPLICANT", "AADHAR", mockFile, new RuntimeException("Test")));
    }

    @Test
    void getDocumentsByApplication_Success() {
        when(documentRepository.findByApplicationId(1L)).thenReturn(List.of(Document.builder().build()));
        assertEquals(1, documentService.getDocumentsByApplication(1L, 1L, "ROLE_ADMIN").size());
    }

    @Test
    void getRequiredDocumentsStatus_Success() {
        when(documentRepository.findByApplicationId(1L)).thenReturn(List.of(
            Document.builder().documentType("AADHAR").build()
        ));
        Map<String, List<String>> status = documentService.getRequiredDocumentsStatus(1L, 1L, "ROLE_ADMIN");
        assertTrue(status.get("uploaded").contains("AADHAR"));
        assertTrue(status.get("pending").contains("PAN"));
    }

    @Test
    void validateAccessFallback_ThrowsException() {
        assertThrows(RuntimeException.class, () ->
            documentService.validateAccessFallback(1L, 1L, "ROLE", new RuntimeException()));
    }

    @Test
    void getDocumentById_NotFound_ThrowsException() {
        assertThrows(RuntimeException.class, () -> documentService.getDocumentById(1L));
    }

    @Test
    void validateDocumentAccess_MissingHeaders_ThrowsException() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(Document.builder().applicationId(1L).build()));
        assertThrows(AccessDeniedException.class, () -> documentService.validateDocumentAccess(1L, null, null));
    }

    @Test
    void validateDocumentAccess_Forbidden_ThrowsException() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(Document.builder().applicationId(1L).build()));
        when(applicationClient.getApplication(1L, 1L, "ROLE_APPLICANT")).thenThrow(FeignException.Forbidden.class);
        assertThrows(AccessDeniedException.class, () -> documentService.validateDocumentAccess(1L, 1L, "ROLE_APPLICANT"));
    }

    @Test
    void getVerificationHistory_Success() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(Document.builder().applicationId(10L).build()));
        when(historyRepository.findByDocumentIdOrderByVerifiedAtDesc(1L)).thenReturn(List.of());
        assertNotNull(documentService.getVerificationHistory(1L, 1L, "ROLE_ADMIN"));
    }

    @Test
    void getFileForViewing_FileNotFound_ThrowsException() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(Document.builder().applicationId(10L).filePath("target/nonexistent.pdf").build()));
        assertThrows(IllegalStateException.class, () -> documentService.getFileForViewing(1L, 1L, "ROLE_ADMIN"));
    }

    @Test
    void getFileForViewing_Found_ReturnsResource() throws IOException {
        Path path = Paths.get("target/test-uploads/1/test-file.pdf");
        Files.createDirectories(path.getParent());
        Files.write(path, "content".getBytes());
        when(documentRepository.findById(1L)).thenReturn(Optional.of(Document.builder().applicationId(10L).filePath(path.toString()).build()));
        
        org.springframework.core.io.Resource resource = documentService.getFileForViewing(1L, 1L, "ROLE_ADMIN");
        assertNotNull(resource);
    }

    @Test
    void updateInternalStatus_NullInput_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> documentService.updateInternalStatus(null, "VERIFIED", "", 1L));
        assertThrows(IllegalArgumentException.class, () -> documentService.updateInternalStatus(1L, null, "", 1L));
    }

    @Test
    void updateInternalStatus_InvalidStatus_ThrowsException() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(Document.builder().build()));
        assertThrows(IllegalArgumentException.class, () -> documentService.updateInternalStatus(1L, "INVALID", "", 1L));
    }

    @Test
    void updateInternalStatus_AlreadyStatus_SkipsUpdate() {
        Document doc = Document.builder().verificationStatus(VerificationStatus.VERIFIED).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        
        Document updated = documentService.updateInternalStatus(1L, "VERIFIED", "ok", 1L);
        assertEquals(doc, updated);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void updateInternalStatus_ChangesStatus_AndPublishesWhenAllVerified() {
        Document doc = Document.builder().applicationId(10L).verificationStatus(VerificationStatus.PENDING).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);
        
        Document d2 = Document.builder().verificationStatus(VerificationStatus.VERIFIED).build();
        when(documentRepository.findByApplicationId(10L)).thenReturn(List.of(doc, d2));

        documentService.updateInternalStatus(1L, "VERIFIED", "ok", 1L);
        
        verify(documentRepository, times(1)).save(any());
        verify(historyRepository, times(1)).save(any());
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(Map.class));
    }
}
