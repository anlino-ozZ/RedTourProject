package com.redtour.business.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 景区响应（管理端 / 游客端共用，内部字段已在游客端查询中裁剪）。
 */
@Data
public class ScenicAreaResult {

    private Long id;
    private String name;
    private String intro;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String address;
    private String openHours;
    private String coverImage;
    private Integer status;
    private String region;
    private String phone;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
