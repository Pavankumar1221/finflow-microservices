package com.finflow.auth.config;

import org.junit.jupiter.api.Test;
import io.swagger.v3.oas.models.OpenAPI;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenApiConfigTest {
    @Test
    void authServiceOpenAPI_ShouldReturnOpenAPIConfig() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.authServiceOpenAPI();
        assertNotNull(openAPI);
    }
}
