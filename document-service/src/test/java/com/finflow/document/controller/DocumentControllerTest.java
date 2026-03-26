package com.finflow.document.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.document.dto.DocumentResponse;
import com.finflow.document.entity.Document;
import com.finflow.document.mapper.DocumentMapper;
import com.finflow.document.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DocumentController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
public class DocumentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DocumentService documentService;
    @MockBean private DocumentMapper mapper;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void uploadDocument_ReturnsCreated() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());
        Document doc = Document.builder().build();
        DocumentResponse res = DocumentResponse.builder().id(10L).build();

        when(documentService.uploadDocument(eq(1L), eq(2L), eq("ROLE_APPLICANT"), eq("AADHAR"), any())).thenReturn(doc);
        when(mapper.toResponse(doc)).thenReturn(res);

        mockMvc.perform(multipart("/documents/upload")
                .file(file)
                .param("applicationId", "1")
                .param("documentType", "AADHAR")
                .header("X-User-Id", "2")
                .header("X-User-Roles", "ROLE_APPLICANT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void getDocumentsByApplication_ReturnsOk() throws Exception {
        when(documentService.getDocumentsByApplication(1L, 2L, "ROLE_ADMIN")).thenReturn(List.of());
        mockMvc.perform(get("/documents/application/1")
                .header("X-User-Id", "2")
                .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void getRequiredDocuments_ReturnsOk() throws Exception {
        when(documentService.getRequiredDocumentsStatus(1L, 2L, "ROLE_ADMIN")).thenReturn(Map.of());
        mockMvc.perform(get("/documents/required/1")
                .header("X-User-Id", "2")
                .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void getDocument_ReturnsOk() throws Exception {
        when(documentService.getDocument(1L, 2L, "ROLE_ADMIN")).thenReturn(Document.builder().build());
        mockMvc.perform(get("/documents/1")
                .header("X-User-Id", "2")
                .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void viewDocument_ReturnsFile() throws Exception {
        Document doc = Document.builder().mimeType("text/plain").fileName("test.txt").build();
        when(documentService.getDocument(1L, 2L, "ROLE_ADMIN")).thenReturn(doc);
        when(documentService.getFileForViewing(1L, 2L, "ROLE_ADMIN")).thenReturn(new ByteArrayResource("test".getBytes()));

        mockMvc.perform(get("/documents/1/view")
                .header("X-User-Id", "2")
                .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"test.txt\""));
    }

    @Test
    void viewDocument_NotFoundInDisk_ReturnsInternalError() throws Exception {
        when(documentService.getDocument(1L, 2L, "ROLE_ADMIN")).thenThrow(new IllegalStateException("Disk issue"));
        mockMvc.perform(get("/documents/1/view")
                .header("X-User-Id", "2")
                .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void viewDocument_NotFoundInDB_ReturnsNotFound() throws Exception {
        when(documentService.getDocument(1L, 2L, "ROLE_ADMIN")).thenThrow(new RuntimeException("DB issue"));
        mockMvc.perform(get("/documents/1/view")
                .header("X-User-Id", "2")
                .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateDocumentStatus_InternalCallValid_ReturnsOk() throws Exception {
        DocumentResponse res = DocumentResponse.builder().id(1L).build();
        when(documentService.updateInternalStatus(1L, "VERIFIED", "ok", 2L)).thenReturn(Document.builder().build());
        when(mapper.toResponse(any(Document.class))).thenReturn(res);

        mockMvc.perform(put("/documents/internal/1/status")
                .header("X-Internal-Call", "admin-service")
                .header("X-User-Id", "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"VERIFIED\",\"remarks\":\"ok\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateDocumentStatus_InternalCallInvalid_ReturnsForbidden() throws Exception {
        mockMvc.perform(put("/documents/internal/1/status")
                .header("X-Internal-Call", "external")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getVerificationHistory_ReturnsOk() throws Exception {
        when(documentService.getVerificationHistory(1L, 2L, "ROLE_ADMIN")).thenReturn(List.of());
        mockMvc.perform(get("/documents/1/history")
                .header("X-User-Id", "2")
                .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }
}
