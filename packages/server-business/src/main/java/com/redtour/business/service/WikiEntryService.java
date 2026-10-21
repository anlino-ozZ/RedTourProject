package com.redtour.business.service;

import com.redtour.business.common.PageResult;
import com.redtour.business.dto.WikiEntryResult;
import com.redtour.business.dto.WikiUpdateRequest;
import com.redtour.business.dto.WikiUploadRequest;

/**
 * Wiki 条目管理服务。
 */
public interface WikiEntryService {

    PageResult<WikiEntryResult> list(Long scenicAreaId, int page, int pageSize, String keyword);

    WikiEntryResult upload(WikiUploadRequest request);

    WikiEntryResult get(Long id);

    /** 触摸屏公开列表：无鉴权，按景区过滤，不带正文。 */
    PageResult<WikiEntryResult> publicList(Long scenicAreaId, int page, int pageSize, String keyword);

    /** 触摸屏公开详情：无鉴权，带正文。 */
    WikiEntryResult publicGet(Long id);

    WikiEntryResult update(Long id, WikiUpdateRequest request);

    WikiEntryResult compile(Long id);

    void delete(Long id);
}
