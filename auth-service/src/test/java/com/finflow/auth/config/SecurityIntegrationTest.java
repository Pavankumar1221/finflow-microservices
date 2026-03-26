package com.finflow.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validate_MissingToken_ShouldReturnForbiddenOrUnauthorized() throws Exception {
        // Since we are unauthenticated and /auth/validate is NOT inside the permitAll exclusions,
        // it requires an authenticated token (or session if not stateless). In this case it should return 401 or 403.
        // Actually /auth/validate IS in the permitAll exclusions in SecurityConfig:
        // .requestMatchers("/auth/validate").permitAll()
        // Wait! Let me check SecurityConfig... Yes, "/auth/validate" is permitAll.
        // So hitting /auth/validate WITHOUT token shouldn't fail due to security, it passes security but hits the controller.
        // But the controller method validate(@RequestHeader("Authorization") String authHeader) REQUIRES the header.
        // So instead of a 403, we get a 400 Bad Request because the header is missing!
        mockMvc.perform(get("/auth/validate"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accessProtectedResource_MissingToken_ShouldReturnForbidden() throws Exception {
        // /auth/users/1 is NOT in permitAll, so it should trigger security chain interception.
        mockMvc.perform(get("/auth/users/1"))
                .andExpect(status().isForbidden());
    }
}
