package com.redtour.business.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 剧本进度实体，对应 script_progress 表。
 * 同一用户同一剧本唯一（uk_user_script）。
 */
@Data
public class ScriptProgress {

    /** 主键 */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 剧本 ID */
    private Long scriptId;

    /** 当前节点 ID */
    private Integer currentNodeId;

    /** 是否完成: 0 进行中 / 1 已完成 */
    private Integer completed;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}