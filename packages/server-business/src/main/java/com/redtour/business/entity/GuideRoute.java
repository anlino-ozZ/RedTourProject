package com.redtour.business.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导览路线实体，对应 guide_route 表。
 * spotIds 在数据库中以 JSON 数组字符串保存，由业务层转换为列表。
 */
@Data
public class GuideRoute {

    /** 主键 */
    private Long id;

    /** 归属景区 ID */
    private Long scenicAreaId;

    /** 路线名称 */
    private String name;

    /** 路线简介 */
    private String description;

    /** 关联景点 ID 列表（JSON 字符串，如 [1,3,5]） */
    private String spotIds;

    /** 预计游览时长（分钟） */
    private Integer duration;

    /** 难度等级: easy / medium / hard */
    private String difficulty;

    /** 状态: 0 下架 / 1 上架 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}