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
        // 不再接受 ?token= ：下载已改为带 Authorization 头的 blob 请求，
        // WebSocket 的令牌由 WsAuthInterceptor 单独校验
        return null;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        // CORS 响应头统一由 WebConfig 中的 CorsFilter 注入，此处重复设置会产生重复头导致浏览器拒绝响应
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private boolean isWhitelisted(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/ws/")
                || path.equals("/error");
    }

    /**
     * 免登录路径。
     *
     * <p>仅保留视频预览：浏览器 {@code <video>} 标签无法附加 Authorization 头，
     * 而任务状态、查重、任务提交、字幕下载等接口都必须登录。
     */
    private boolean isPublicAccess(String path) {
        return path.matches("/api/video/[^/]+/stream");
    }
}
