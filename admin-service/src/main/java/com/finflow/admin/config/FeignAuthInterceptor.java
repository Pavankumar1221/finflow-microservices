package com.finflow.admin.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String userId = attrs.getRequest().getHeader("X-User-Id");
            String roles  = attrs.getRequest().getHeader("X-User-Roles");
            String auth   = attrs.getRequest().getHeader("Authorization");
            if (userId != null) template.header("X-User-Id", userId);
            if (roles  != null) template.header("X-User-Roles", roles);
            if (auth   != null) template.header("Authorization", auth);
        }
    }
}
