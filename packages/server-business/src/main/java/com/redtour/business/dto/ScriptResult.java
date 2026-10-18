package com.redtour.business.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 剧本响应（管理端 / 游客端共用，完整字段）。
 */
@Data
public class ScriptResult {

    private Long id;
    private Long scenicAreaId;
    private String title;
    private String description;
    private String coverImage;
    private String roles;
    private String nodes;
    private Integer startNodeId;
    private String achievements;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
