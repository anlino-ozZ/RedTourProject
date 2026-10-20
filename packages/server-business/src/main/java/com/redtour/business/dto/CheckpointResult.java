package com.redtour.business.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打卡点响应。
 * 注意：checkpoint 表无 scenic_area_id 列，景区归属通过 spot 间接确定，故结果不含 scenicAreaId；
 * schema 亦无 created_at/updated_at 列（实体字段将保持为 null）。
 */
@Data
public class CheckpointResult {

    private Long id;
    private Long spotId;
    private BigDecimal longitude;
    private BigDecimal latitude;
    /** 打卡有效半径（米） */
    private Integer radius;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
