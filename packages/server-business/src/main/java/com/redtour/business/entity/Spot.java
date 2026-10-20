package com.redtour.business.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 景点实体，对应 spot 表。
 * images 在数据库中以 JSON 字符串保存，由业务层转换为列表。
 */
@Data
public class Spot {

    /** 主键 */
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

    /** 排序权重（越小越靠前） */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 状态: 0 下架 / 1 上架 */
    private Integer status;

    /** 软删除: 0 未删 / 1 已删 */
    private Integer deleted;
}