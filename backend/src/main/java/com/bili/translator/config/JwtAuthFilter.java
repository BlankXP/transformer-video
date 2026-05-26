package com.bili.translator.config;

import com.bili.translator.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AppProperties appProperties;

    public JwtAuthFilter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isWhitelisted(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token != null && JwtUtil.validateToken(token, appProperties.getJwtSecret())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isPublicAccess(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        sendUnauthorized(response, "未登录或登录已过期");
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        String queryToken = request.getParameter("token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken;
        }

        return null;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private boolean isWhitelisted(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/ws/")
                || path.equals("/error");
    }

    private boolean isPublicAccess(String path) {
        return path.matches("/api/video/[^/]+/status")
                || path.matches("/api/video/process");
    }
}
