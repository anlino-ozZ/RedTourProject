package com.redtour.business.service;

import com.redtour.business.common.PageResult;
import com.redtour.business.dto.AchievementRequest;
import com.redtour.business.dto.AchievementResult;

/**
 * 成就定义管理服务（按 scenic_area_id 隔离；仅 super_admin/admin 可管理，普管限本人景区）。
 */
public interface AchievementService {

    /** 管理端分页列表（按触发类型/标题/景区筛选；普管仅见本人景区）。 */
    PageResult<AchievementResult> list(int page, int pageSize, String triggerType, String name, Long scenicAreaId);

    /** 管理端详情。 */
    AchievementResult get(Long id);

    /** 新增成就。 */
    AchievementResult create(AchievementRequest request);

    /** 编辑成就。 */
    AchievementResult update(Long id, AchievementRequest request);

    /** 删除成就（已被用户解锁则 409；否则 status=0 下线）。 */
    void delete(Long id);
}
