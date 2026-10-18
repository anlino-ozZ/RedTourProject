package com.redtour.business.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备状态实体，对应 device_status 表。
 * 主键为 device_id（树莓派设备标识），不是自增 id。
 */
@Data
public class DeviceStatus {

    /** 设备标识（主键，如 rpi-001） */
    private String deviceId;

    /** 绑定景区 ID */
    private Long scenicAreaId;

    /** 在线状态: 0 离线 / 1 在线 */
    private Integer online;

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

    /** 最后心跳时间 */
    private LocalDateTime lastHeartbeat;
}