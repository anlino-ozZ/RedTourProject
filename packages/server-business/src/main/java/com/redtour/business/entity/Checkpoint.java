package com.redtour.business.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打卡点实体，对应 checkpoint 表。
 * 一景点对应一打卡点（uk_spot 唯一约束）。
 */
@Data
public class Checkpoint {

    /** 主键 */
    private Long id;

    /** 归属景区 ID */
    private Long scenicAreaId;

    /** 关联景点 ID（唯一） */
    private Long spotId;

    /** 打卡点经度 */
    private BigDecimal longitude;

    /** 打卡点纬度 */
    private BigDecimal latitude;

    /** 打卡有效半径（米，默认 50） */
    private Integer radius;

    /** 软删标记：0 正常 / 1 已删（checkpoint 表已加 deleted 列） */
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}