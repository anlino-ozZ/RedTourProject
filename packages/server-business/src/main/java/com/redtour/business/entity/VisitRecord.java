package com.redtour.business.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游览记录实体，对应 visit_record 表。
 */
@Data
public class VisitRecord {

    /** 主键 */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 景区 ID */
    private Long scenicAreaId;

    /** 路线 ID（可空，自由参观无路线） */
    private Long routeId;

    /** 打卡景点数 */
    private Integer spotCount;

    /** 游览时长（分钟） */
    private Integer duration;

    /** 游览时间 */
    private LocalDateTime createdAt;
}