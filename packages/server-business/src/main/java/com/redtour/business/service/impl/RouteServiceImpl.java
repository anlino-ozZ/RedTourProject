package com.redtour.business.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.RouteCompleteResult;
import com.redtour.business.entity.GuideRoute;
import com.redtour.business.entity.SysUser;
import com.redtour.business.entity.VisitRecord;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.CheckinMapper;
import com.redtour.business.mapper.VisitRecordMapper;
import com.redtour.business.service.AchievementEvaluationService;
import com.redtour.business.service.CheckinService;
import com.redtour.business.service.RouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 路线完成服务实现：全景点打卡校验 + 写入游览记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteServiceImpl implements RouteService {

    private final CheckinMapper checkinMapper;
    private final VisitRecordMapper visitRecordMapper;
    private final AchievementEvaluationService achievementEvaluationService;
    private final CheckinService checkinService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public RouteCompleteResult complete(Long routeId) {
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
        int total = spotIds.size();
        int completed = total == 0 ? 0 : checkinService.countCompletedSpots(current.getId(), spotIds);
        if (completed < total) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "尚未完成全部打卡点");
        }

        // 幂等：已完成的路线不再重复写入游览记录（计数守卫 + 唯一索引双重保障，防并发竞态）
        boolean alreadyCompleted = visitRecordMapper.countByUserAndRoute(current.getId(), routeId) > 0;
        if (!alreadyCompleted) {
            VisitRecord record = new VisitRecord();
            record.setUserId(current.getId());
            record.setScenicAreaId(route.getScenicAreaId());
            record.setRouteId(route.getId());
            record.setSpotCount(total);
            record.setDuration(route.getDuration() == null ? 0 : route.getDuration());
            try {
                visitRecordMapper.insert(record);
            } catch (DataIntegrityViolationException e) {
                // 并发竞态下唯一索引 (user_id, route_id) 已存在记录，视为已完成，不报错
                log.debug("路线完成并发重复，忽略唯一键冲突 userId={}, routeId={}", current.getId(), routeId);
            }
        }

        RouteCompleteResult result = new RouteCompleteResult();
        result.setRouteId(route.getId());
        result.setCompleted(true);
        result.setDuration(route.getDuration() == null ? 0 : route.getDuration());
        List<String> unlocked = new ArrayList<>();
        unlocked.addAll(achievementEvaluationService.evaluateOnCheckin(current.getId(), route.getScenicAreaId()));
        unlocked.addAll(achievementEvaluationService.evaluateOnRouteComplete(
                current.getId(), route.getId(), route.getScenicAreaId()));
        result.setAchievements(unlocked);
        return result;
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
            log.warn("[路线完成] 路线景点数据解析失败 spotIds={}", spotIdsJson, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "路线景点数据异常");
        }
    }
}
