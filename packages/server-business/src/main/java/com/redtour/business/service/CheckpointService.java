package com.redtour.business.service;

import com.redtour.business.common.PageResult;
import com.redtour.business.dto.CheckpointRequest;
import com.redtour.business.dto.CheckpointResult;

/**
 * 打卡点管理服务。
 * checkpoint 表无 scenic_area_id 列，景区归属通过 spot 表间接确定；
 * 隔离以"spot 所属景区"为准（admin 仅本人景区）。
 */
public interface CheckpointService {

    /** 管理端分页列表（可按景区/景点筛选）。 */
    PageResult<CheckpointResult> list(int page, int pageSize, Long scenicAreaId, Long spotId);

    /** 管理端详情（按归属景区隔离）。 */
    CheckpointResult get(Long id);

    /** 新增打卡点（按 spot 反查景区做隔离校验）。 */
    CheckpointResult create(CheckpointRequest request);

    /** 编辑打卡点（按归属景区隔离）。 */
    CheckpointResult update(Long id, CheckpointRequest request);

    /** 删除打卡点（按归属景区隔离）。 */
    void delete(Long id);
}
