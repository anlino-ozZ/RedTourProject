package com.redtour.business.service;

import com.redtour.business.common.PageResult;
import com.redtour.business.dto.ScenicAreaRequest;
import com.redtour.business.dto.ScenicAreaResult;

/**
 * 景区管理服务。
 */
public interface ScenicAreaService {

    /** 管理端分页列表（名称模糊 / 状态 / 地区筛选）。 */
    PageResult<ScenicAreaResult> list(int page, int pageSize, String name, Integer status, String region);

    /** 管理端详情。 */
    ScenicAreaResult get(Long id);

    /** 新增景区（仅超级管理员）。 */
    ScenicAreaResult create(ScenicAreaRequest request);

    /** 编辑景区（admin 限本景区）。 */
    ScenicAreaResult update(Long id, ScenicAreaRequest request);

    /** 删除景区（软删除，admin 限本景区）。 */
    void delete(Long id);

    /** 上下架状态切换（admin 限本景区）。 */
    ScenicAreaResult updateStatus(Long id, Integer status);

    /** 游客端：仅上架景区分页。 */
    PageResult<ScenicAreaResult> publicList(int page, int pageSize, String keyword);

    /** 游客端：上架景区详情。 */
    ScenicAreaResult publicGet(Long id);
}
