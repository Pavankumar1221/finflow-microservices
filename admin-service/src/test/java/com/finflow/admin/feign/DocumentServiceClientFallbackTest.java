package com.finflow.admin.feign;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentServiceClientFallbackTest {

    private final DocumentServiceClientFallback fallback = new DocumentServiceClientFallback();

    @Test
    void getDocumentsByApplication() {
        List<Object> list = fallback.getDocumentsByApplication(1L, "1", "ROLE");
        assertTrue(list.isEmpty());
    }

    @Test
    void updateDocumentStatus() {
        Object obj = fallback.updateDocumentStatus(1L, Map.of(), "internal", "1");
        assertNotNull(obj);
    }
}
