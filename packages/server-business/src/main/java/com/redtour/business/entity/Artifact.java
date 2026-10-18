package com.redtour.business.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文物实体，对应 artifact 表。
 * images 在数据库中以 JSON 字符串保存。
 */
@Data
public class Artifact {

    /** 主键 */
    private Long id;

    /** 归属景区 ID */
    private Long scenicAreaId;

    /** 文物名称 */
    private String name;

    /** 年代，如 1935年 */
    private String era;

    /** 文物简介 */
    private String intro;

    /** 图片 URL 数组（JSON 字符串） */
    private String images;

    /** 出土/收藏地点 */
    private String location;

    /** 品类，如 武器 / 文献 */
    private String category;

    /** 关联 Wiki 条目路径 */
    private String wikiRef;

    /** 状态: 0 下架 / 1 上架 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}