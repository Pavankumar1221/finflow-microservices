package com.finflow.admin.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRuntimeException() {
        ResponseEntity<Map<String, Object>> res = handler.handleRuntimeException(new RuntimeException("Test error"));
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("Test error", res.getBody().get("message"));
    }

    @Test
    void handleValidation() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        FieldError fe = new FieldError("objectName", "field", "defaultMessage");
        when(bindingResult.getAllErrors()).thenReturn(List.of(fe));

        ResponseEntity<Map<String, Object>> res = handler.handleValidation(ex);
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("Validation failed", res.getBody().get("message"));
    }

    @Test
    void handleException() {
        ResponseEntity<Map<String, Object>> res = handler.handleException(new Exception("Unknown"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
        assertEquals("Internal server error", res.getBody().get("message"));
    }
}
