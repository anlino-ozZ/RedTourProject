package com.redtour.business.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 特产/文创产品响应。
 */
@Data
public class ProductResult {

    private Long id;
    private Long scenicAreaId;
    private String name;
    private BigDecimal price;
    private String description;
    private String image;
    private Integer stock;
    /** 品类: specialty 特产 / cultural 文创 */
    private String category;
    /** 状态: 0 下架 / 1 上架 */
    private Integer status;
    /** 浏览量 */
    private Integer clickCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
