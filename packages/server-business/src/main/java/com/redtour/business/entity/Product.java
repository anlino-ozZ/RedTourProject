package com.redtour.business.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 特产/文创产品实体，对应 product 表。
 */
@Data
public class Product {

    /** 主键 */
    private Long id;

    /** 归属景区 ID */
    private Long scenicAreaId;

    /** 产品名称 */
    private String name;

    /** 价格（元） */
    private BigDecimal price;

    /** 产品描述 */
    private String description;

    /** 主图 URL */
    private String image;

    /** 库存数量 */
    private Integer stock;

    /** 品类: specialty 特产 / cultural 文创 */
    private String category;

    /** 状态: 0 下架 / 1 上架 */
    private Integer status;

    /** 点击量（详情浏览自增，brief B3 / 分析接口使用） */
    private Integer clickCount;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}