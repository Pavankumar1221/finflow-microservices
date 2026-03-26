package com.finflow.admin.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

public class FeignAuthInterceptorTest {

    @Test
    void apply_WithAttributes() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "1");
        req.addHeader("X-User-Roles", "ROLE_ADMIN");
        req.addHeader("Authorization", "Bearer token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        FeignAuthInterceptor interceptor = new FeignAuthInterceptor();
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertTrue(template.headers().containsKey("X-User-Id"));
        assertTrue(template.headers().containsKey("X-User-Roles"));
        assertTrue(template.headers().containsKey("Authorization"));
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void apply_WithoutAttributes() {
        RequestContextHolder.resetRequestAttributes();
        FeignAuthInterceptor interceptor = new FeignAuthInterceptor();
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        assertTrue(template.headers().isEmpty());
    }
}
