package com.redtour.business.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文物响应。字段以 schema.sql / Artifact 实体为准（intro / images / era / location / category / wikiRef）。
 */
@Data
public class ArtifactResult {

    private Long id;
    private Long scenicAreaId;
    private String name;
    private String era;
    private String intro;
    /** 图片 URL 数组（JSON 字符串） */
    private String images;
    private String location;
    private String category;
    private String wikiRef;
    /** 状态: 0 下架 / 1 上架 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
