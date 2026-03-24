package com.finflow.document.service;

import com.finflow.document.config.RabbitMQConfig;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVerificationHistoryRepository historyRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ApplicationServiceClient applicationClient;

    @InjectMocks
    private DocumentService documentService;

    private Document draftDocument;
    private final Long VALID_DOC_ID = 500L;
    private final Long VALID_APP_ID = 100L;
    private final Long VALID_USER_ID = 42L;
    private final Long VALID_ADMIN_ID = 99L;

    @BeforeEach
    void setUp() {
        draftDocument = Document.builder()
                .id(VALID_DOC_ID)
                .applicationId(VALID_APP_ID)
                .documentType("AADHAR")
                .verificationStatus(VerificationStatus.PENDING)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Ownership Validation via Feign
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void validateAccess_WhenOwner_DoesNotThrowException() {
        // Arrange
        when(applicationClient.getApplication(eq(VALID_APP_ID), eq(VALID_USER_ID), eq("ROLE_APPLICANT")))
                .thenReturn(Map.of("id", VALID_APP_ID));

        // Act & Assert
        assertDoesNotThrow(() -> {
            documentService.validateAccess(VALID_APP_ID, VALID_USER_ID, "ROLE_APPLICANT");
        });
        
        verify(applicationClient, times(1)).getApplication(VALID_APP_ID, VALID_USER_ID, "ROLE_APPLICANT");
    }

    @Test
    void validateAccess_WhenNotOwner_ThrowsAccessDeniedException() {
        // Arrange
        when(applicationClient.getApplication(eq(VALID_APP_ID), eq(VALID_USER_ID), eq("ROLE_USER")))
                .thenThrow(mock(FeignException.Forbidden.class));

        // Act & Assert
        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> {
            documentService.validateAccess(VALID_APP_ID, VALID_USER_ID, "ROLE_USER");
        });

        assertEquals("Access Denied: You do not own this application", exception.getMessage());
    }

    @Test
    void validateAccess_WhenAdmin_BypassesFeignValidation() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            documentService.validateAccess(VALID_APP_ID, VALID_ADMIN_ID, "ROLE_ADMIN");
        });

        // Ensure no network calls are made for internal Admins
        verify(applicationClient, never()).getApplication(anyLong(), anyLong(), anyString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Successful Verification (No Event if others are pending)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void updateInternalStatus_WhenVerifiedButOthersPending_SavesWithoutEvent() {
        // Arrange
        Document pendingDoc2 = Document.builder().id(501L).applicationId(VALID_APP_ID).verificationStatus(VerificationStatus.PENDING).build();
        
        when(documentRepository.findById(VALID_DOC_ID)).thenReturn(Optional.of(draftDocument));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // Return a list containing the newly verified doc + another still pending doc
        when(documentRepository.findByApplicationId(VALID_APP_ID)).thenReturn(List.of(draftDocument, pendingDoc2));

        // Act
        Document result = documentService.updateInternalStatus(VALID_DOC_ID, "VERIFIED", "Looks authentic", VALID_ADMIN_ID);

        // Assert
        assertEquals(VerificationStatus.VERIFIED, result.getVerificationStatus());
        assertNotNull(result.getVerifiedAt());

        // Validate History Creation
        ArgumentCaptor<DocumentVerificationHistory> historyCaptor = ArgumentCaptor.forClass(DocumentVerificationHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertEquals(VerificationStatus.VERIFIED, historyCaptor.getValue().getStatus());
        assertEquals("Looks authentic", historyCaptor.getValue().getRemarks());

        // Ensure Event is NEVER Sent because pendingDoc2 is pending
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Event Publishing (When all documents in the batch become VERIFIED)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void updateInternalStatus_WhenVerifiedAndAllOthersVerified_PublishesEvent() {
        // Arrange
        when(documentRepository.findById(VALID_DOC_ID)).thenReturn(Optional.of(draftDocument));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // Return a list where ALL documents associated with application are VERIFIED
        when(documentRepository.findByApplicationId(VALID_APP_ID)).thenReturn(List.of(draftDocument));

        // Act
        documentService.updateInternalStatus(VALID_DOC_ID, "VERIFIED", "Perfect", VALID_ADMIN_ID);

        // Assert - Deep Validation of Event
        ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.DOC_EXCHANGE),
                eq(RabbitMQConfig.DOC_VERIFIED_ROUTING),
                eventCaptor.capture()
        );

        Map<String, Object> capturedEvent = eventCaptor.getValue();
        assertEquals("DOCUMENTS_VERIFIED", capturedEvent.get("eventType"));
        assertEquals(VALID_APP_ID, capturedEvent.get("applicationId"));
        assertNotNull(capturedEvent.get("verifiedAt"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Document Not Found
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void getDocumentById_WhenNotFound_ThrowsException() {
        when(documentRepository.findById(VALID_DOC_ID)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            documentService.getDocumentById(VALID_DOC_ID);
        });
        assertEquals("Document not found: " + VALID_DOC_ID, exception.getMessage());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Invalid Status Input
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void updateInternalStatus_WhenStatusIsInvalid_ThrowsIllegalArgumentException() {
        when(documentRepository.findById(VALID_DOC_ID)).thenReturn(Optional.of(draftDocument));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            documentService.updateInternalStatus(VALID_DOC_ID, "INVALID_STATUS", "remarks", VALID_ADMIN_ID);
        });
        assertEquals("Invalid status: INVALID_STATUS", exception.getMessage());
        verify(documentRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Idempotency Case
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void updateInternalStatus_WhenAlreadyVerified_DoesNothing() {
        draftDocument.setVerificationStatus(VerificationStatus.VERIFIED);
        when(documentRepository.findById(VALID_DOC_ID)).thenReturn(Optional.of(draftDocument));

        Document result = documentService.updateInternalStatus(VALID_DOC_ID, "VERIFIED", "perfect", VALID_ADMIN_ID);

        assertEquals(VerificationStatus.VERIFIED, result.getVerificationStatus());
        verify(documentRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Reject Flow
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void updateInternalStatus_WhenRejected_SavesWithoutEvent() {
        when(documentRepository.findById(VALID_DOC_ID)).thenReturn(Optional.of(draftDocument));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        Document result = documentService.updateInternalStatus(VALID_DOC_ID, "REJECTED", "Blurry image", VALID_ADMIN_ID);

        assertEquals(VerificationStatus.REJECTED, result.getVerificationStatus());
        
        ArgumentCaptor<DocumentVerificationHistory> historyCaptor = ArgumentCaptor.forClass(DocumentVerificationHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertEquals(VerificationStatus.REJECTED, historyCaptor.getValue().getStatus());

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Null Input Handling
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void updateInternalStatus_WhenDocumentIdIsNull_ThrowsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            documentService.updateInternalStatus(null, "VERIFIED", "remarks", VALID_ADMIN_ID);
        });
        assertEquals("Document ID and status cannot be null", exception.getMessage());
    }

    @Test
    void updateInternalStatus_WhenStatusIsNull_ThrowsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            documentService.updateInternalStatus(VALID_DOC_ID, null, "remarks", VALID_ADMIN_ID);
        });
        assertEquals("Document ID and status cannot be null", exception.getMessage());
    }
}
