package com.redtour.business.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 姿态 WebSocket 连接、消息和稳定动作门控配置。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "pose.websocket")
public class PoseWebSocketProperties {

    @NotBlank
    private String aiEngineUrl = "ws://localhost:8001/engine/pose/stream";

    @Min(1)
    private int connectTimeoutMs = 3000;

    @Min(1)
    private int maxConnections = 20;

    @Min(1)
    private int maxMessageChars = 14_100_000;

    @Min(1)
    private long idleTimeoutMs = 60_000L;

    @Min(1)
    private long idleScanMs = 10_000L;

    @Min(1)
    private int stableFrames = 3;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double minConfidence = 0.85;
}
