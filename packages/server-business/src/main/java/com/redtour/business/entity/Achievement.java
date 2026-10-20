package com.redtour.business.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成就定义实体，对应 achievement 表。
 * 全景区公用，按 triggerCondition 区分触发规则。
 */
@Data
public class Achievement {

    /** 主键 */
    private Long id;

    /** 成就标题 */
    private String title;

    /** 图标 URL */
    private String icon;

    /** 成就描述 */
    private String description;

    /** 触发类型: visit 游览 / checkin 打卡 / script 剧本 / route 路线 / pose 姿态 */
    private String triggerType;

    /** 触发条件配置（JSON 字符串） */
    private String triggerCondition;

    /** 状态: 0 下架 / 1 上架（brief A.7 / B5，全局模块统一用 status=0 表达下线） */
    private Integer status;

    /** 归属景区 ID；NULL 表示全局成就（仅超管可管理） */
    private Long scenicAreaId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}