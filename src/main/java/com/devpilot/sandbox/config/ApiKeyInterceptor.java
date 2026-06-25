package com.devpilot.sandbox.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyInterceptor.class);

    @Value("${sandbox.api-key:}")
    private String apiKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (apiKey.isEmpty()) {
            return true;
        }

        String provided = request.getHeader("X-API-Key");
        if (apiKey.equals(provided)) {
            return true;
        }

        log.warn("API key rejected: {}", request.getRequestURI());
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(401);
        response.getWriter().write("{\"error\":\"Unauthorized: missing or invalid X-API-Key header\"}");
        return false;
    }
}
