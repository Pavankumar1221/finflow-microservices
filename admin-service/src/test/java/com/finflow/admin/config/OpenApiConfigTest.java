package com.finflow.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OpenApiConfigTest {

    @Test
    void adminServiceOpenAPI() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI api = config.adminServiceOpenAPI();
        assertNotNull(api);
        assertNotNull(api.getInfo());
    }
}
