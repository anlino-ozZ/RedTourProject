package com.redtour.business.service;

import com.redtour.business.common.PageResult;
import com.redtour.business.dto.SpotRequest;
import com.redtour.business.dto.SpotResult;

/**
 * 景点管理服务。
 */
public interface SpotService {

    /** 管理端分页列表（按景区/名称/状态筛选；admin 仅本人景区）。 */
    PageResult<SpotResult> list(int page, int pageSize, Long scenicAreaId, String name, Integer status);

    /** 管理端详情。 */
    SpotResult get(Long id);

    /** 新增景点（admin 限本景区；super 可指定景区）。 */
    SpotResult create(SpotRequest request);

    /** 编辑景点（admin 限本景区；scenic_area_id 不变）。 */
    SpotResult update(Long id, SpotRequest request);

    /** 删除景点（软删除，admin 限本景区）。 */
    void delete(Long id);

    /** 上下架状态切换（admin 限本景区）。 */
    SpotResult updateStatus(Long id, Integer status);

    /** 游客端：上架景点分页（可按景区/名称筛选）。 */
    PageResult<SpotResult> publicList(int page, int pageSize, Long scenicAreaId, String keyword);

    /** 游客端：上架景点详情。 */
    SpotResult publicGet(Long id);
}
