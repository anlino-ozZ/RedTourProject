package com.redtour.business.service;

import com.redtour.business.dto.SyncRequest;
import com.redtour.business.dto.SyncResult;

/**
 * 离线数据同步服务（需登录）：批量写入打卡与问答，按 clientId 去重。
 */
public interface SyncService {

    SyncResult sync(SyncRequest request, Long userId);
}
