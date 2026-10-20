package com.redtour.business.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

/**
 * 离线同步请求体：批量打卡 + 批量问答，按 clientId 去重后写入。
 */
@Data
public class SyncRequest {

    @Valid
    private List<CheckinSyncItem> checkins;

    @Valid
    private List<AskLogSyncItem> askLogs;
}
