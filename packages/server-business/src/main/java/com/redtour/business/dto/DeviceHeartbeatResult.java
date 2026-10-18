package com.redtour.business.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备心跳上报响应。
 */
@Data
public class DeviceHeartbeatResult {

    private Boolean online;
    private LocalDateTime lastHeartbeat;
}
