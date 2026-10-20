package com.redtour.business.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备状态响应（管理端查询）。
 */
@Data
public class DeviceStatusResult {

    private String deviceId;
    private Long scenicAreaId;
    private Integer online;
    private BigDecimal cpuTemp;
    private BigDecimal memoryUsage;
    private BigDecimal diskUsage;
    private String hailoStatus;
    private Integer cameraConnected;
    private Integer speakerConnected;
    private LocalDateTime lastHeartbeat;
}
