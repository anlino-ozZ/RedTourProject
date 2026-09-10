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

    WikiEntryResult update(Long id, WikiUpdateRequest request);

    WikiEntryResult compile(Long id);

    void delete(Long id);
}
