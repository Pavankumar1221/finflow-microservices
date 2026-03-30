package com.finflow.document.controller;

import com.finflow.document.dto.DocumentResponse;
import com.finflow.document.dto.VerificationHistoryResponse;
import com.finflow.document.entity.Document;
import com.finflow.document.mapper.DocumentMapper;
import com.finflow.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Tag(name = "Document Controller", description = "Document upload, verification, and history management")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentMapper mapper;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Upload a document for a loan application")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam Long applicationId,
            @RequestParam String documentType,
            @RequestParam("file") MultipartFile file,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Roles", required = false) String roles) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toResponse(
                        documentService.uploadDocument(applicationId, userId, roles, documentType, file)));
    }

    @GetMapping("/application/{applicationId}")
    @PreAuthorize("hasAnyRole('APPLICANT', 'ADMIN')")
    @Operation(summary = "Get all documents for an application")
    public ResponseEntity<List<DocumentResponse>> getDocumentsByApplication(
            @PathVariable Long applicationId,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toResponseList(
                documentService.getDocumentsByApplication(applicationId, userId, roles)));
    }

    @GetMapping("/required/{applicationId}")
    @PreAuthorize("hasAnyRole('APPLICANT', 'ADMIN')")
    @Operation(summary = "Get required, uploaded, and pending document types")
    public ResponseEntity<Map<String, List<String>>> getRequiredDocuments(
            @PathVariable Long applicationId,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(documentService.getRequiredDocumentsStatus(applicationId, userId, roles));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('APPLICANT', 'ADMIN')")
    @Operation(summary = "Get a specific document's metadata by ID")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toResponse(documentService.getDocument(id, userId, roles)));
    }

    @GetMapping("/{id}/view")
    @PreAuthorize("hasAnyRole('APPLICANT', 'ADMIN')")
    @Operation(summary = "View the actual document file inline in the browser")
    public ResponseEntity<Resource> viewDocument(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) throws IOException {
        return buildFileResponse(id, userId, roles, true);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('APPLICANT', 'ADMIN')")
    @Operation(summary = "Download the actual document file as an attachment")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) throws IOException {
        return buildFileResponse(id, userId, roles, false);
    }

    @PutMapping("/internal/{id}/status")
    @Operation(summary = "Internal: Update document status", hidden = true)
    public ResponseEntity<DocumentResponse> updateDocumentStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @Parameter(hidden = true) @RequestHeader(value = "X-Internal-Call", required = false) String internalCallHeader,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        if (!"admin-service".equals(internalCallHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(mapper.toResponse(
                documentService.updateInternalStatus(id, body.get("status"), body.get("remarks"), userId)));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('APPLICANT', 'ADMIN')")
    @Operation(summary = "Get verification history for a document")
    public ResponseEntity<List<VerificationHistoryResponse>> getVerificationHistory(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) {
        return ResponseEntity.ok(mapper.toHistoryList(
                documentService.getVerificationHistory(id, userId, roles)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('APPLICANT')")
    @Operation(summary = "Delete a specific document owned by the logged-in applicant")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Roles") String roles) throws IOException {
        documentService.deleteDocument(id, userId, roles);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Resource> buildFileResponse(Long id, Long userId, String roles, boolean inline)
            throws IOException {
        Document doc = documentService.getDocument(id, userId, roles);
        Resource resource = documentService.getFileForViewing(id, userId, roles);

        String mimeType = (doc.getMimeType() != null && !doc.getMimeType().isBlank())
                ? doc.getMimeType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(mimeType);
        } catch (Exception ex) {
            log.warn("Unrecognised MIME type '{}' for doc {}. Falling back to octet-stream.", mimeType, id);
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition disposition = inline
                ? ContentDisposition.inline().filename(doc.getFileName() != null ? doc.getFileName() : "document").build()
                : ContentDisposition.attachment().filename(doc.getFileName() != null ? doc.getFileName() : "document").build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }
}
