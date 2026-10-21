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
import com.redtour.business.mapper.ScenicAreaMapper;
import com.redtour.business.mapper.SpotMapper;
import com.redtour.business.service.CheckinService;
import com.redtour.business.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 离线数据同步服务实现：逐条写入，clientId 重复则跳过。
 * 归属景区一律由服务端推导/校验，不信任客户端传值，避免伪造其他景区数据污染分析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncServiceImpl implements SyncService {

    private final CheckinMapper checkinMapper;
    private final AskLogMapper askLogMapper;
    private final CheckinService checkinService;
    private final SpotMapper spotMapper;
    private final ScenicAreaMapper scenicAreaMapper;

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
                // 景区由 spotId 服务端推导，忽略客户端 scenicAreaId
                Long areaId = resolveCheckinAreaId(item);
                if (areaId == null) {
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
                    c.setScenicAreaId(areaId);
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
                        c.setScenicAreaId(areaId);
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
                // 问答没有 spotId 可推导，至少校验景区真实存在，拒绝伪造景区 ID
                if (!isValidScenicArea(item.getScenicAreaId())) {
                    log.warn("[同步] 问答景区不存在，跳过 scenicAreaId={} clientId={}",
                            item.getScenicAreaId(), item.getClientId());
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

    /**
     * 打卡归属景区由 spotId 推导（服务端为准），客户端 scenicAreaId 仅用于比对、不落库。
     * 景点不存在时返回 null，由调用方跳过该条。
     */
    private Long resolveCheckinAreaId(CheckinSyncItem item) {
        if (item.getSpotId() == null) {
            return null;
        }
        Long areaId = spotMapper.selectScenicAreaIdById(item.getSpotId());
        if (areaId == null) {
            log.warn("[同步] 打卡景点不存在，跳过 spotId={} clientId={}", item.getSpotId(), item.getClientId());
            return null;
        }
        if (item.getScenicAreaId() != null && !areaId.equals(item.getScenicAreaId())) {
            log.warn("[同步] 打卡景区与景点归属不一致，以服务端为准 spotId={} client={} server={}",
                    item.getSpotId(), item.getScenicAreaId(), areaId);
        }
        return areaId;
    }

    /** 校验景区是否真实存在（含上架状态由调用方另行判断）。 */
    private boolean isValidScenicArea(Long scenicAreaId) {
        return scenicAreaId != null && scenicAreaId > 0
                && scenicAreaMapper.findById(scenicAreaId) != null;
    }
}
