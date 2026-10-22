package com.bili.translator.config;

import com.bili.translator.websocket.ProgressWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ProgressWebSocketHandler progressWebSocketHandler;
    private final WsAuthInterceptor wsAuthInterceptor;

    public WebSocketConfig(ProgressWebSocketHandler progressWebSocketHandler,
                           WsAuthInterceptor wsAuthInterceptor) {
        this.progressWebSocketHandler = progressWebSocketHandler;
        this.wsAuthInterceptor = wsAuthInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 令牌校验由 WsAuthInterceptor 兜底，因此允许跨源与开发代理连接；
        // 若部署在多域名环境，可再配合 setAllowedOriginPatterns 进一步收紧。
        registry.addHandler(progressWebSocketHandler, "/ws/{taskId}")
                .addInterceptors(wsAuthInterceptor)
                .setAllowedOrigins("*");
    }
}
