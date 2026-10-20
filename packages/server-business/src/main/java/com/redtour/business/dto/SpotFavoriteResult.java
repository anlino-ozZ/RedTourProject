package com.redtour.business.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收藏景点响应（个人中心 GET /api/v1/profile/favorites 返回项，join spot）。
 */
@Data
public class SpotFavoriteResult {

    /** spot_favorite 主键 */
    private Long favoriteId;

    /** 收藏时间 */
    private LocalDateTime favoritedAt;

    /** 景点 ID */
    private Long id;

    /** 归属景区 ID */
    private Long scenicAreaId;

    /** 景点名称 */
    private String name;

    /** 景点简介 */
    private String intro;

    /** 图片 URL 列表（JSON 字符串） */
    private String images;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 讲解音频 URL */
    private String audioUrl;

    /** 关联 Wiki 条目路径 */
    private String wikiRef;

    /** 排序权重 */
    private Integer sortOrder;

    /** 状态: 0 下架 / 1 上架 */
    private Integer status;
}
