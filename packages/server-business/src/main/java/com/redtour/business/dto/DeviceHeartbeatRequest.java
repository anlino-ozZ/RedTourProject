package com.redtour.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 设备心跳上报请求（白名单接口，无需登录）。
 */
@Data
public class DeviceHeartbeatRequest {

    @NotBlank(message = "设备ID不能为空")
    private String deviceId;

    @NotNull(message = "景区ID不能为空")
    private Long scenicAreaId;

    /** CPU 温度（℃） */
    private BigDecimal cpuTemp;

    /** 内存占用率（%） */
    private BigDecimal memoryUsage;

    /** 磁盘占用率（%） */
    private BigDecimal diskUsage;

    /** Hailo8L 状态: active / inactive / error */
    private String hailoStatus;

    /** 摄像头连接: 0 否 / 1 是 */
    private Integer cameraConnected;

    /** 音箱连接: 0 否 / 1 是 */
    private Integer speakerConnected;
}
