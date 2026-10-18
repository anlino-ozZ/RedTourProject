package com.redtour.business.service.impl;

import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.AchievementUsersResult;
import com.redtour.business.dto.AchievementUnlockerResult;
import com.redtour.business.dto.UserAchievementResult;
import com.redtour.business.entity.Achievement;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.AchievementMapper;
import com.redtour.business.mapper.UserAchievementMapper;
import com.redtour.business.service.UserAchievementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户成就服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAchievementServiceImpl implements UserAchievementService {

    private final UserAchievementMapper userAchievementMapper;
    private final AchievementMapper achievementMapper;

    @Override
    public List<UserAchievementResult> listCurrentUserAchievements(Long userId) {
        List<UserAchievementResult> rows = userAchievementMapper.findByUserId(userId);
        return rows == null ? List.of() : rows;
    }

    @Override
    public AchievementUsersResult listAchievementUsers(Long achievementId, Long scenicAreaId) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Achievement achievement = achievementMapper.findById(achievementId);
        if (achievement == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "成就不存在");
        }
        // 权限隔离：super 透传筛选；admin 强制本人景区且只能访问归属本景区的成就；其它角色拒绝。
        Long resolvedArea;
        if ("super_admin".equals(current.getRole())) {
            resolvedArea = scenicAreaId;
        } else if ("admin".equals(current.getRole())) {
            if (current.getScenicAreaId() == null) {
                throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
            }
            if (achievement.getScenicAreaId() == null) {
                // 全局成就仅超管可管理
                throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该数据");
            }
            if (!achievement.getScenicAreaId().equals(current.getScenicAreaId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他景区的成就");
            }
            resolvedArea = current.getScenicAreaId();
        } else {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该数据");
        }
        long count = userAchievementMapper.countByAchievementId(achievementId);
        List<AchievementUnlockerResult> list = userAchievementMapper.listUnlockers(achievementId, resolvedArea);
        if (list == null) {
            list = List.of();
        }
        return new AchievementUsersResult(count, list);
    }
}
