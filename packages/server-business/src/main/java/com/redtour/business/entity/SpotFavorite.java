package com.redtour.business.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 景点收藏实体，对应 spot_favorite 表。
 * 同一用户同一景点唯一（uk_user_spot）。
 */
@Data
public class SpotFavorite {

    /** 主键 */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 景点 ID */
    private Long spotId;

    /** 收藏时间 */
    private LocalDateTime createdAt;
}