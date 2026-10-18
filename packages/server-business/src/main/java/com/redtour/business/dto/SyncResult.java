package com.redtour.business.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 离线同步结果汇总。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncResult {

    /** 成功写入的打卡数 */
    private int syncedCheckins;

    /** 成功写入的问答数 */
    private int syncedAskLogs;

    /** 因 clientId 重复被跳过的条数（打卡 + 问答） */
    private int skipped;
}
