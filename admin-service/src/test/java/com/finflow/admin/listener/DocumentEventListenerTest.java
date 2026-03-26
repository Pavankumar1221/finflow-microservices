package com.finflow.admin.listener;

import org.junit.jupiter.api.Test;

import java.util.Map;

public class DocumentEventListenerTest {

    private final DocumentEventListener listener = new DocumentEventListener();

    @Test
    void handleDocumentsVerified() {
        listener.handleDocumentsVerified(Map.of("applicationId", 1L));
    }
}
