package com.redtour.business.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成就定义响应。
 */
@Data
public class AchievementResult {

    private Long id;
    private String title;
    private String icon;
    private String description;
    /** 触发类型: visit / pose / script / checkin */
    private String triggerType;
    /** 触发条件配置（JSON 字符串） */
    private String triggerCondition;
    /** 状态: 0 下架 / 1 上架 */
    private Integer status;
    /** 归属景区 ID；NULL 表示全局成就 */
    private Long scenicAreaId;
    private LocalDateTime createdAt;
}
