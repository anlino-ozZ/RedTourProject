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
        registry.addHandler(poseWebSocketProxyHandler, "/api/v1/pose/stream");
    }
}
