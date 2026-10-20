package com.redtour.business.service.impl;

import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.AchievementRequest;
import com.redtour.business.dto.AchievementResult;
import com.redtour.business.entity.Achievement;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.AchievementMapper;
import com.redtour.business.service.AchievementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 成就定义管理服务实现：全局模块，无 scenic_area_id，不做景区隔离。
 * 管理端仍受 @RequireRole(super_admin, admin) 保护。
 * 删除前校验是否被用户解锁（user_achievement 引用），已引用则 409。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementServiceImpl implements AchievementService {

    private static final int MAX_PAGE_SIZE = 100;

    /** 系统支持且可解锁的触发类型白名单；其余类型建出来也不会被解锁，一律拒绝。 */
    private static final Set<String> ALLOWED_TRIGGER_TYPES = Set.of("visit", "checkin", "script", "route");

    private final AchievementMapper achievementMapper;

    @Override
    public PageResult<AchievementResult> list(int page, int pageSize, String triggerType, String name, Long scenicAreaId) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        String normalizedTrigger = StringUtils.hasText(triggerType) ? triggerType.trim() : null;
        String normalizedName = StringUtils.hasText(name) ? name.trim() : null;
        Long resolvedArea = scopeScenicAreaId(scenicAreaId);
        List<Achievement> rows = achievementMapper.findPage(normalizedTrigger, normalizedName, resolvedArea, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<AchievementResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, achievementMapper.count(normalizedTrigger, normalizedName, resolvedArea), page, pageSize);
    }

    @Override
    public AchievementResult get(Long id) {
        Achievement achievement = loadAchievement(id);
        authorizeScenicArea(achievement.getScenicAreaId());
        return toResult(achievement);
    }

    @Override
    public AchievementResult create(AchievementRequest request) {
        String triggerType = request.getTriggerType().trim();
        if (!ALLOWED_TRIGGER_TYPES.contains(triggerType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的触发类型: " + triggerType);
        }
        Long scenicAreaId = resolveCreateScenicAreaId(request.getScenicAreaId());
        Achievement achievement = new Achievement();
        achievement.setTitle(request.getTitle().trim());
        achievement.setIcon(blankToNull(request.getIcon()));
        achievement.setDescription(blankToNull(request.getDescription()));
        achievement.setTriggerType(triggerType);
        achievement.setTriggerCondition(blankToNull(request.getTriggerCondition()));
        achievement.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        achievement.setScenicAreaId(scenicAreaId);
        achievementMapper.insert(achievement);
        if (achievement.getId() == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "成就创建失败");
        }
        return toResult(achievement);
    }

    @Override
    public AchievementResult update(Long id, AchievementRequest request) {
        Achievement achievement = loadAchievement(id);
        authorizeScenicArea(achievement.getScenicAreaId());
        if (request.getTitle() != null) {
            achievement.setTitle(request.getTitle().trim());
        }
        if (request.getIcon() != null) {
            achievement.setIcon(blankToNull(request.getIcon()));
        }
        if (request.getDescription() != null) {
            achievement.setDescription(blankToNull(request.getDescription()));
        }
        if (request.getTriggerType() != null) {
            String triggerType = request.getTriggerType().trim();
            if (!ALLOWED_TRIGGER_TYPES.contains(triggerType)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的触发类型: " + triggerType);
            }
            achievement.setTriggerType(triggerType);
        }
        if (request.getTriggerCondition() != null) {
            achievement.setTriggerCondition(blankToNull(request.getTriggerCondition()));
        }
        if (request.getStatus() != null) {
            if (request.getStatus() != 0 && request.getStatus() != 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "状态值不合法");
            }
            achievement.setStatus(request.getStatus());
        }
        if (request.getScenicAreaId() != null) {
            authorizeScenicArea(request.getScenicAreaId());
            achievement.setScenicAreaId(request.getScenicAreaId());
        }
        achievementMapper.updateById(achievement);
        return toResult(achievement);
    }

    @Override
    public void delete(Long id) {
        Achievement achievement = loadAchievement(id);
        authorizeScenicArea(achievement.getScenicAreaId());
        if (achievementMapper.countUserAchievement(id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该成就已被用户解锁，无法删除");
        }
        achievementMapper.delete(id);
    }

    private Achievement loadAchievement(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "成就ID必须为正整数");
        }
        Achievement achievement = achievementMapper.findById(id);
        if (achievement == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "成就不存在");
        }
        return achievement;
    }

    private AchievementResult toResult(Achievement achievement) {
        AchievementResult result = new AchievementResult();
        result.setId(achievement.getId());
        result.setTitle(achievement.getTitle());
        result.setIcon(achievement.getIcon());
        result.setDescription(achievement.getDescription());
        result.setTriggerType(achievement.getTriggerType());
        result.setTriggerCondition(achievement.getTriggerCondition());
        result.setStatus(achievement.getStatus());
        result.setScenicAreaId(achievement.getScenicAreaId());
        result.setCreatedAt(achievement.getCreatedAt());
        return result;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private void validatePage(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数不合法");
        }
    }

    /** 列表/详情按景区隔离：超管可透传筛选或查全部；普管强制本人景区。 */
    private Long scopeScenicAreaId(Long scenicAreaId) {
        SysUser user = UserContext.getCurrentUser();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(user.getRole())) {
            return scenicAreaId;
        }
        if ("admin".equals(user.getRole())) {
            if (user.getScenicAreaId() == null) {
                throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
            }
            return user.getScenicAreaId();
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权访问成就数据");
    }

    /** 操作（详情/编辑/删除）景区隔离：超管放行；普管仅限本人景区，否则 403。 */
    private void authorizeScenicArea(Long scenicAreaId) {
        SysUser user = UserContext.getCurrentUser();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(user.getRole())) {
            return;
        }
        if ("admin".equals(user.getRole())) {
            if (user.getScenicAreaId() == null) {
                throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
            }
            if (scenicAreaId == null || !user.getScenicAreaId().equals(scenicAreaId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他景区的成就");
            }
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权访问成就数据");
    }

    /** 创建时解析归属景区：超管必须显式指定；普管强制本人景区。 */
    private Long resolveCreateScenicAreaId(Long scenicAreaId) {
        SysUser user = UserContext.getCurrentUser();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(user.getRole())) {
            if (scenicAreaId == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "超级管理员创建成就须指定归属景区");
            }
            return scenicAreaId;
        }
        if ("admin".equals(user.getRole())) {
            if (user.getScenicAreaId() == null) {
                throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
            }
            return user.getScenicAreaId();
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权访问成就数据");
    }
}
