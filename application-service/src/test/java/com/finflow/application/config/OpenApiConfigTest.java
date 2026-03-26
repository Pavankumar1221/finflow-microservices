package com.finflow.application.config;

import org.junit.jupiter.api.Test;
import io.swagger.v3.oas.models.OpenAPI;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenApiConfigTest {
    @Test
    void applicationServiceOpenAPI_ShouldReturnOpenAPIConfig() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.applicationServiceOpenAPI();
        assertNotNull(openAPI);
    }
}
