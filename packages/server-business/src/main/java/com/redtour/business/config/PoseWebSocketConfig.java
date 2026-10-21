package com.redtour.business.config;

import com.redtour.business.websocket.PoseWebSocketProxyHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册触摸屏姿态 WebSocket 入口。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@EnableConfigurationProperties(PoseWebSocketProperties.class)
public class PoseWebSocketConfig implements WebSocketConfigurer {

    private final PoseWebSocketProxyHandler poseWebSocketProxyHandler;
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 必须显式放开跨域：WebSocket 握手不走 CorsConfig（那是 HTTP CORS），
        // Spring 默认只放行同源握手，前端跑在 Vite(5173/5174) 而后端在 8000，
        // 未放开时握手会被 403 拒绝，前端表现为"连接断开 · 重连中"。
        registry.addHandler(poseWebSocketProxyHandler, "/api/v1/pose/stream")
                .setAllowedOriginPatterns("*");
    }
}
