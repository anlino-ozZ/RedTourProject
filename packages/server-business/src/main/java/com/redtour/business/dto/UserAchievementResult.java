package com.redtour.business.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前用户已解锁成就（公开端 GET /api/v1/achievements 返回项）。
 */
@Data
public class UserAchievementResult {

    /** 成就 ID */
    private Long achievementId;

    /** 成就名称（来自 achievement.title） */
    private String name;

    /** 成就描述 */
    private String description;

    /** 解锁时间 */
    private LocalDateTime unlockedAt;

    /** 解锁来源: h5 / pose_touchscreen / script / checkin */
    private String unlockSource;

    /** 解锁时所在景区 ID（可空） */
    private Long scenicAreaId;
}
