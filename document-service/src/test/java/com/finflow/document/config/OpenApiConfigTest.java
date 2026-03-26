package com.finflow.document.config;

import org.junit.jupiter.api.Test;
import io.swagger.v3.oas.models.OpenAPI;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenApiConfigTest {
    @Test
    void documentServiceOpenAPI_ShouldReturnOpenAPIConfig() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.documentServiceOpenAPI();
        assertNotNull(openAPI);
    }
}
