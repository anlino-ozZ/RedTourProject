package com.redtour.business.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 景点响应（管理端 / 游客端共用，完整字段不裁剪）。
 */
@Data
public class SpotResult {

    private Long id;
    private Long scenicAreaId;
    private String name;
    private String intro;
    private String images;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String audioUrl;
    private String wikiRef;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
