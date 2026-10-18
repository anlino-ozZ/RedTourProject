package com.redtour.business.service.impl;

import com.redtour.business.dto.AskLogSyncItem;
import com.redtour.business.dto.CheckinSyncItem;
import com.redtour.business.dto.SyncRequest;
import com.redtour.business.dto.SyncResult;
import com.redtour.business.entity.AskLog;
import com.redtour.business.entity.Checkin;
import com.redtour.business.entity.Checkpoint;
import com.redtour.business.mapper.AskLogMapper;
import com.redtour.business.mapper.CheckinMapper;
import com.redtour.business.service.CheckinService;
import com.redtour.business.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import java.time.LocalDateTime;

/**
 * 离线数据同步服务实现：逐条写入，clientId 重复则跳过。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncServiceImpl implements SyncService {

    private final CheckinMapper checkinMapper;
    private final AskLogMapper askLogMapper;
    private final CheckinService checkinService;

    @Override
    public SyncResult sync(SyncRequest request, Long userId) {
        int syncedCheckins = 0;
        int syncedAskLogs = 0;
        int skipped = 0;

        if (request.getCheckins() != null) {
            for (CheckinSyncItem item : request.getCheckins()) {
                if (item == null || item.getClientId() == null || item.getClientId().isBlank()) {
                    skipped++;
                    continue;
                }
                List<Checkpoint> matched = (item.getLongitude() != null && item.getLatitude() != null)
                        ? checkinService.resolveMatchedCheckpoints(item.getSpotId(), item.getLongitude(), item.getLatitude())
                        : List.of();
                if (matched.isEmpty()) {
                    // 无坐标或不在任何打卡点范围内：保留原行为写一条无 checkpoint_id 记录（不计入完成进度）
                    if (checkinMapper.countByClientId(item.getClientId()) > 0) {
                        skipped++;
                        continue;
                    }
                    Checkin c = new Checkin();
                    c.setDeviceId(null);
                    c.setUserId(userId);
                    c.setSpotId(item.getSpotId());
                    c.setScenicAreaId(item.getScenicAreaId());
                    c.setClientId(item.getClientId());
                    c.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt() : LocalDateTime.now());
                    try {
                        checkinMapper.insert(c);
                        syncedCheckins++;
                    } catch (DataIntegrityViolationException e) {
                        log.warn("[同步] 打卡 clientId 冲突，已跳过: {}", item.getClientId());
                        skipped++;
                    }
                } else {
                    for (Checkpoint cp : matched) {
                        String rowClientId = item.getClientId() + ":" + cp.getId();
                        if (checkinMapper.countByClientId(rowClientId) > 0) {
                            skipped++;
                            continue;
                        }
                        Checkin c = new Checkin();
                        c.setDeviceId(null);
                        c.setUserId(userId);
                        c.setSpotId(item.getSpotId());
                        c.setScenicAreaId(item.getScenicAreaId());
                        c.setCheckpointId(cp.getId());
                        c.setClientId(rowClientId);
                        c.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt() : LocalDateTime.now());
                        try {
                            checkinMapper.insert(c);
                            syncedCheckins++;
                        } catch (DataIntegrityViolationException e) {
                            log.warn("[同步] 打卡 clientId 冲突，已跳过: {}", rowClientId);
                            skipped++;
                        }
                    }
                }
            }
        }

        if (request.getAskLogs() != null) {
            for (AskLogSyncItem item : request.getAskLogs()) {
                if (item == null || item.getClientId() == null || item.getClientId().isBlank()) {
                    skipped++;
                    continue;
                }
                if (askLogMapper.countByClientId(item.getClientId()) > 0) {
                    skipped++;
                    continue;
                }
                AskLog askLog = new AskLog();
                askLog.setScenicAreaId(item.getScenicAreaId());
                askLog.setUserId(userId);
                askLog.setQuestion(item.getQuestion());
                askLog.setAnswer(item.getAnswer());
                askLog.setClientId(item.getClientId());
                askLog.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt() : LocalDateTime.now());
                try {
                    askLogMapper.insert(askLog);
                    syncedAskLogs++;
                } catch (DataIntegrityViolationException e) {
                    log.warn("[同步] 问答 clientId 冲突，已跳过: {}", item.getClientId());
                    skipped++;
                }
            }
        }

        return new SyncResult(syncedCheckins, syncedAskLogs, skipped);
    }
}
