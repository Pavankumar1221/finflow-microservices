package com.finflow.document.listener;

import org.junit.jupiter.api.Test;

import java.util.Map;

class ApplicationEventListenerTest {

    @Test
    void handleApplicationSubmitted_ExecutesSuccessfully() {
        ApplicationEventListener listener = new ApplicationEventListener();
        listener.handleApplicationSubmitted(Map.of("applicationId", 1L, "applicationNumber", "FIN-1"));
    }
}
