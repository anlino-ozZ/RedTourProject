package com.redtour.business.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导览路线响应。
 */
@Data
public class GuideRouteResult {

    private Long id;
    private Long scenicAreaId;
    private String name;
    private String description;
    /** 关联景点 ID 列表（JSON 数组字符串）。 */
    private String spotIds;
    private Integer duration;
    /** 难度等级: easy / medium / hard */
    private String difficulty;
    /** 状态: 0 下线 / 1 上线 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
