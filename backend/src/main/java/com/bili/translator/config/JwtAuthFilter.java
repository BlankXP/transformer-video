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

        // 前端已合并进后端：非 /api 路径是 SPA 静态资源、前端路由与 /error，必须放行；
        // /ws 握手也走这里，其令牌由 WsAuthInterceptor 单独校验。
        // 约定：所有业务接口都挂在 /api 下，新增接口时不要绕过这个前缀。
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/auth/")) {
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

    /**
     * 免登录路径。
     *
     * <p>只有两处：{@code <video>} 预览（标签无法附加请求头），
     * 以及凭一次性票据下载（浏览器直链同样带不了头，票据本身即凭证，短时且一次性）。
     * 任务状态、查重、提交、票据申请都必须登录。
     */
    private boolean isPublicAccess(String path) {
        return path.matches("/api/video/[^/]+/stream")
                || path.matches("/api/download/[^/]+");
    }
}
