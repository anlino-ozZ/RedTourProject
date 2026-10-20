package com.redtour.business.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 景区实体，对应 scenic_area 表。
 */
@Data
public class ScenicArea {

    /** 主键 */
    private Long id;

    /** 景区名称 */
    private String name;

    /** 景区简介 */
    private String intro;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 详细地址 */
    private String address;

    /** 开放时间，如 08:30-17:00 */
    private String openHours;

    /** 封面图 URL */
    private String coverImage;

    /** 状态: 0 下架 / 1 上架 */
    private Integer status;

    /** 所属地区/城市 */
    private String region;

    /** 联系电话 */
    private String phone;

    /** 排序权重，越大越靠前 */
    private Integer sortOrder;

    /** 软删除: 0 未删 / 1 已删 */
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}