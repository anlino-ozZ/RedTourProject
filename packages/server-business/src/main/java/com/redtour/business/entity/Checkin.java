package com.redtour.business.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 打卡记录实体，对应 checkin 表。
 * 字段对齐 schema：id, device_id, user_id, spot_id, scenic_area_id, client_id, created_at。
 * client_id 为离线同步去重键（唯一键）。
 */
@Data
public class Checkin {

    private Long id;

    /** 来源设备标识（触摸屏端可为 null） */
    private String deviceId;

    /** 用户 ID（游客端为当前登录用户，触摸屏端可为 null） */
    private Long userId;

    /** 景点 ID */
    private Long spotId;

    /** 打卡点 ID（细化到打卡点级别；NULL 表示未关联具体打卡点，如旧数据/离线无坐标） */
    private Long checkpointId;

    /** 归属景区 ID */
    private Long scenicAreaId;

    /** 客户端生成的去重 ID（离线同步使用） */
    private String clientId;

    /** 打卡时间 */
    private LocalDateTime createdAt;
}
