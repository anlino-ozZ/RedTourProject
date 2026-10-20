package com.redtour.business.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.CheckinRequest;
import com.redtour.business.dto.CheckinResult;
import com.redtour.business.entity.Checkin;
import com.redtour.business.entity.Checkpoint;
import com.redtour.business.entity.GuideRoute;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.CheckinMapper;
import com.redtour.business.mapper.CheckpointMapper;
import com.redtour.business.service.AchievementEvaluationService;
import com.redtour.business.service.CheckinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 路线打卡服务实现：景点归属校验 + 打卡点半径内判定（haversine，单位米）+ 进度统计。
 *
 * 完成规则（brief B2）：一个景点下的【全部】打卡点都被打到，才算该景点完成。
 * 因此 checkin 记录细化到打卡点级别：每次打卡按当前坐标命中哪些打卡点，
 * 就为这些打卡点各写一条 checkin（checkpoint_id 非空）；景点完成判定即
 * 该用户对「该景点已命中打卡点数」≥「该景点打卡点总数」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private final CheckinMapper checkinMapper;
    private final CheckpointMapper checkpointMapper;
    private final AchievementEvaluationService achievementEvaluationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public CheckinResult checkin(Long routeId, CheckinRequest request) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (routeId == null || routeId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "路线ID不合法");
        }
        GuideRoute route = checkinMapper.findRouteById(routeId);
        if (route == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "路线不存在");
        }
        List<Long> spotIds = parseSpotIds(route.getSpotIds());
        if (!spotIds.contains(request.getSpotId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不在该路线景点范围内");
        }

        // 命中坐标范围内的全部打卡点（可为多个，重叠半径场景）
        List<Checkpoint> matched = resolveMatchedCheckpoints(request.getSpotId(),
                request.getLongitude(), request.getLatitude());
        if (matched.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "距离打卡点过远");
        }

        // 每个命中的打卡点各写一条记录（细化到打卡点级别）
        for (Checkpoint cp : matched) {
            Checkin checkin = new Checkin();
            checkin.setDeviceId(null);
            checkin.setUserId(current.getId());
            checkin.setSpotId(request.getSpotId());
            checkin.setScenicAreaId(route.getScenicAreaId());
            checkin.setCheckpointId(cp.getId());
            checkin.setClientId(UUID.randomUUID().toString());
            checkinMapper.insert(checkin);
            if (checkin.getId() == null) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "打卡记录创建失败");
            }
        }

        int total = spotIds.size();
        int completed = total == 0 ? 0 : countCompletedSpots(current.getId(), spotIds);

        // 本次打卡后，该景点是否已全部打卡点命中
        int spotTotal = checkpointMapper.countBySpotId(request.getSpotId());
        int spotHit = checkinMapper.countDistinctCheckpoints(current.getId(), request.getSpotId());

        CheckinResult result = new CheckinResult();
        result.setSpotId(request.getSpotId());
        result.setChecked(spotTotal > 0 && spotHit >= spotTotal);
        result.setProgress(completed + "/" + total);
        result.setAchievementUnlocked(
                achievementEvaluationService.evaluateOnCheckin(current.getId(), route.getScenicAreaId()));
        return result;
    }

    @Override
    public List<Checkpoint> resolveMatchedCheckpoints(Long spotId, BigDecimal longitude, BigDecimal latitude) {
        List<Checkpoint> checkpoints = checkpointMapper.findBySpotId(spotId);
        if (checkpoints == null || checkpoints.isEmpty()
                || longitude == null || latitude == null) {
            return List.of();
        }
        List<Checkpoint> matched = new ArrayList<>();
        for (Checkpoint cp : checkpoints) {
            if (cp.getLongitude() == null || cp.getLatitude() == null || cp.getRadius() == null) {
                continue;
            }
            if (haversineMeters(longitude.doubleValue(), latitude.doubleValue(),
                    cp.getLongitude().doubleValue(), cp.getLatitude().doubleValue()) <= cp.getRadius()) {
                matched.add(cp);
            }
        }
        return matched;
    }

    @Override
    public int countCompletedSpots(Long userId, List<Long> spotIds) {
        if (spotIds == null || spotIds.isEmpty()) {
            return 0;
        }
        int completed = 0;
        for (Long spotId : spotIds) {
            int total = checkpointMapper.countBySpotId(spotId);
            if (total <= 0) {
                // 未配置打卡点的景点不参与完成判定（视为未完成，避免路线卡死；由管理端配置保证）
                continue;
            }
            int hit = checkinMapper.countDistinctCheckpoints(userId, spotId);
            if (hit >= total) {
                completed++;
            }
        }
        return completed;
    }

    /** 解析路线 spot_ids JSON 数组字符串为 Long 列表。 */
    private List<Long> parseSpotIds(String spotIdsJson) {
        if (spotIdsJson == null || spotIdsJson.isBlank()) {
            return List.of();
        }
        try {
            List<Integer> raw = objectMapper.readValue(spotIdsJson, new TypeReference<List<Integer>>() {});
            return raw.stream().map(Long::valueOf).toList();
        } catch (Exception e) {
            log.warn("[打卡] 路线景点数据解析失败 spotIds={}", spotIdsJson, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "路线景点数据异常");
        }
    }

    /** haversine 球面距离（米）。 */
    private double haversineMeters(double lon1, double lat1, double lon2, double lat2) {
        final int r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
