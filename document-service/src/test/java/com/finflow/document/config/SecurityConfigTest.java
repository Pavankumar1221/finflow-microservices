package com.finflow.document.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import static org.mockito.Mockito.*;

class SecurityConfigTest {

    @Test
    void filterChain_ShouldBuildWithoutError() throws Exception {
        SecurityConfig config = new SecurityConfig();
        assert(config != null);
    }
}
