package com.redtour.business.dto;

import lombok.Data;

import java.util.List;

/**
 * 某成就的解锁概况（管理端 GET /api/v1/admin/achievements/{id}/users 响应）。
 */
@Data
public class AchievementUsersResult {

    /** 解锁总人数 */
    private long count;

    /** 解锁者明细 */
    private List<AchievementUnlockerResult> list;

    public AchievementUsersResult() {
    }

    public AchievementUsersResult(long count, List<AchievementUnlockerResult> list) {
        this.count = count;
        this.list = list;
    }
}
