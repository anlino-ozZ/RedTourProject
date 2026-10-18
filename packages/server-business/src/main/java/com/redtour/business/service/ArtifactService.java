package com.redtour.business.service;

import com.redtour.business.common.PageResult;
import com.redtour.business.dto.ArtifactRequest;
import com.redtour.business.dto.ArtifactResult;

/**
 * 文物管理服务（带景区归属隔离）。
 */
public interface ArtifactService {

    /** 管理端分页列表（名称/景区/状态筛选；admin 仅本人景区）。 */
    PageResult<ArtifactResult> list(int page, int pageSize, String name, Long scenicAreaId, Integer status);

    /** 管理端详情。 */
    ArtifactResult get(Long id);

    /** 新增文物（admin 限本景区；super 可指定景区）。 */
    ArtifactResult create(ArtifactRequest request);

    /** 编辑文物（admin 限本景区；scenic_area_id 不变）。 */
    ArtifactResult update(Long id, ArtifactRequest request);

    /** 下线（status=0）。 */
    void delete(Long id);

    /** 上下架状态切换（admin 限本景区）。 */
    ArtifactResult updateStatus(Long id, Integer status);

    /** 游客端：上架文物分页（可按景区/名称模糊筛选）。 */
    PageResult<ArtifactResult> publicList(int page, int pageSize, Long scenicAreaId, String keyword);

    /** 游客端：上架文物详情。 */
    ArtifactResult publicGet(Long id);
}
