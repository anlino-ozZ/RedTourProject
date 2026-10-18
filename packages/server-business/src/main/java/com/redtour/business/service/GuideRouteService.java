package com.redtour.business.service;

import com.redtour.business.common.PageResult;
import com.redtour.business.dto.GuideRouteRequest;
import com.redtour.business.dto.GuideRouteResult;

/**
 * 导览路线管理服务（带景区归属隔离）。
 */
public interface GuideRouteService {

    /** 管理端分页列表（名称/景区/状态筛选；admin 仅本人景区）。 */
    PageResult<GuideRouteResult> list(int page, int pageSize, String name, Long scenicAreaId, Integer status);

    /** 管理端详情。 */
    GuideRouteResult get(Long id);

    /** 新增路线（admin 限本景区；super 可指定景区）。 */
    GuideRouteResult create(GuideRouteRequest request);

    /** 编辑路线（admin 限本景区；scenic_area_id 不变）。 */
    GuideRouteResult update(Long id, GuideRouteRequest request);

    /** 下线（status=0）。 */
    void delete(Long id);

    /** 上下架状态切换（admin 限本景区）。 */
    GuideRouteResult updateStatus(Long id, Integer status);

    /** 游客端：上架路线分页（可按景区/名称模糊筛选）。 */
    PageResult<GuideRouteResult> publicList(int page, int pageSize, Long scenicAreaId, String keyword);

    /** 游客端：上架路线详情。 */
    GuideRouteResult publicGet(Long id);
}
