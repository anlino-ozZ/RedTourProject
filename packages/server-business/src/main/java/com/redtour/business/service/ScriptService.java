package com.redtour.business.service;

import com.redtour.business.common.PageResult;
import com.redtour.business.dto.ScriptCompleteResult;
import com.redtour.business.dto.ScriptRequest;
import com.redtour.business.dto.ScriptResult;

/**
 * 剧本管理服务（管理端 CRUD + 游客端只读，带景区归属隔离）。
 */
public interface ScriptService {

    /** 管理端分页列表（title 模糊 / status 过滤，带 scenicAreaId 隔离）。 */
    PageResult<ScriptResult> list(int page, int pageSize, String title, Long scenicAreaId, Integer status);

    /** 管理端详情（带 scenicAreaId 隔离）。 */
    ScriptResult get(Long id);

    /** 新增剧本（scenicAreaId 隔离）。 */
    ScriptResult create(ScriptRequest request);

    /** 编辑剧本（scenicAreaId 隔离）。 */
    ScriptResult update(Long id, ScriptRequest request);

    /** 下线（status = 0）。 */
    void delete(Long id);

    /** 上下架状态切换。 */
    ScriptResult updateStatus(Long id, Integer status);

    /** 游客端：按景区筛选，仅上架剧本。 */
    PageResult<ScriptResult> publicList(int page, int pageSize, Long scenicAreaId);

    /** 游客端：上架剧本详情。 */
    ScriptResult publicGet(Long id);

    /** 游客端：标记完成某剧本（需登录），写入剧本进度并触发成就解锁判定。 */
    ScriptCompleteResult complete(Long scriptId);
}
