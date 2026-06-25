package com.devpilot.sandbox.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Order(1)
public class ApiKeyFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    @Value("${sandbox.api-key:}")
    private String apiKey;

    @Value("${sandbox.auth.enabled:false}")
    private boolean authEnabled;

    @Autowired(required = false)
    private JwtService jwtService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();
        boolean hasApiKey = (apiKey != null && !apiKey.isEmpty());
        boolean hasJwt = (jwtService != null && authEnabled);

        if (!hasApiKey && !hasJwt) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/ui/") || path.equals("/") || path.equals("/ui") ||
            path.equals("/api/v1/guard/health") || path.equals("/api/v1/auth/login") ||
            "OPTIONS".equals(req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            if (hasApiKey) {
                String key = req.getHeader("X-API-Key");
                if (apiKey.equals(key)) { chain.doFilter(request, response); return; }
            }
            if (hasJwt) {
                String auth = req.getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    String subject = jwtService.validateToken(auth.substring(7));
                    if (subject != null) { chain.doFilter(request, response); return; }
                }
            }
            res.setStatus(401);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"allowed\":false,\"reason\":\"Authentication required\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}