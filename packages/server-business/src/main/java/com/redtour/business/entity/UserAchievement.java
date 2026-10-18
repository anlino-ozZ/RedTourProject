package com.redtour.business.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户成就关联实体，对应 user_achievement 表。
 * 同一用户同一成就唯一（uk_user_achievement）。
 */
@Data
public class UserAchievement {

    /** 主键 */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 成就 ID */
    private Long achievementId;

    /** 解锁时间 */
    private LocalDateTime unlockedAt;

    /** 解锁来源: h5 / pose_touchscreen / script / checkin */
    private String unlockSource;

    /** 解锁时所在景区 ID（可空，如全局成就） */
    private Long scenicAreaId;
}