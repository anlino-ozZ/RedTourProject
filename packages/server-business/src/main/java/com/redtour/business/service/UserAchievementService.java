package com.redtour.business.service;

import com.redtour.business.dto.AchievementUsersResult;
import com.redtour.business.dto.UserAchievementResult;

import java.util.List;

/**
 * 用户成就服务（公开端当前用户成就 + 管理端成就解锁概况）。
 */
public interface UserAchievementService {

    /** 当前用户已解锁成就列表（join achievement）。 */
    List<UserAchievementResult> listCurrentUserAchievements(Long userId);

    /**
     * 某成就的解锁概况（管理端）：
     * super 可按 scenicAreaId 过滤；admin 强制本人景区。
     */
    AchievementUsersResult listAchievementUsers(Long achievementId, Long scenicAreaId);
}
