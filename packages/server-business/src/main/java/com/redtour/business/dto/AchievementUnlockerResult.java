package com.redtour.business.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 某成就的解锁者（管理端 GET /api/v1/admin/achievements/{id}/users 返回项）。
 */
@Data
public class AchievementUnlockerResult {

    /** 用户 ID */
    private Long userId;

    /** 昵称（来自 sys_user.nickname） */
    private String nickname;

    /** 解锁时间 */
    private LocalDateTime unlockedAt;

    /** 解锁来源 */
    private String unlockSource;

    /** 解锁时所在景区 ID */
    private Long scenicAreaId;
}
