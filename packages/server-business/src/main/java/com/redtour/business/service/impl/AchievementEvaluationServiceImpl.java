package com.redtour.business.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redtour.business.entity.Achievement;
import com.redtour.business.mapper.AchievementMapper;
import com.redtour.business.mapper.UserAchievementMapper;
import com.redtour.business.mapper.VisitRecordMapper;
import com.redtour.business.service.AchievementEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 成就解锁评估实现。
 * trigger_condition 为 JSON，各类型字段约定：
 *  - visit:  {"count": N, "spot_ids": [..]}        用户在 spot_ids 中已完成(全部打卡点命中) >= count 个景点即解锁
 *  - checkin:{"spot_count": N, "scenic_area_id": X} 用户在景区 X 已完成 >= N 个景点即解锁
 *  - script: {"completed": N, "script_id": X}       用户已完成剧本 X >= N 次即解锁
 *  - route:  {"route_id": X, "completed": N}        用户已完成路线 X >= N 次即解锁（默认 N=1）
 *  - pose:  由硬件姿态识别触发，后端暂无入口，本评估器不处理（返回不解锁）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementEvaluationServiceImpl implements AchievementEvaluationService {

    private static final List<String> CHECKIN_TYPES = List.of("visit", "checkin");
    private static final List<String> SCRIPT_TYPES = List.of("script");

    private final AchievementMapper achievementMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final VisitRecordMapper visitRecordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<String> evaluateOnCheckin(Long userId, Long scenicAreaId) {
        List<String> unlocked = new ArrayList<>();
        for (Achievement a : loadCandidates(CHECKIN_TYPES, scenicAreaId)) {
            if (isUnlocked(userId, a.getId())) {
                continue;
            }
            if (matchVisitOrCheckin(a, userId, scenicAreaId)) {
                doUnlock(userId, a, "checkin", scenicAreaId, unlocked);
            }
        }
        return unlocked;
    }

    @Override
    public List<String> evaluateOnRouteComplete(Long userId, Long routeId, Long scenicAreaId) {
        List<String> unlocked = new ArrayList<>();
        for (Achievement a : loadCandidates(List.of("route"), scenicAreaId)) {
            if (isUnlocked(userId, a.getId())) {
                continue;
            }
            if (matchRoute(a, userId, routeId)) {
                doUnlock(userId, a, "route", scenicAreaId, unlocked);
            }
        }
        return unlocked;
    }

    @Override
    public List<String> evaluateOnScriptComplete(Long userId, Long scriptId, Long scenicAreaId) {
        List<String> unlocked = new ArrayList<>();
        for (Achievement a : loadCandidates(SCRIPT_TYPES, scenicAreaId)) {
            if (isUnlocked(userId, a.getId())) {
                continue;
            }
            if (matchScript(a, userId, scriptId)) {
                doUnlock(userId, a, "script", scenicAreaId, unlocked);
            }
        }
        return unlocked;
    }

    /**
     * 加载候选成就：只取归属本景区的成就 + 全局成就，保证解锁记录的
     * scenic_area_id 一定与成就归属一致，普管按景区查询时才能查全。
     */
    private List<Achievement> loadCandidates(List<String> types, Long scenicAreaId) {
        if (scenicAreaId == null) {
            log.warn("[成就] 缺少事件景区，跳过解锁评估");
            return List.of();
        }
        List<Achievement> list = achievementMapper.findActiveByTriggerTypes(scenicAreaId, types);
        return list == null ? List.of() : list;
    }

    private boolean isUnlocked(Long userId, Long achievementId) {
        return userAchievementMapper.countByUserAndAchievement(userId, achievementId) > 0;
    }

    private void doUnlock(Long userId, Achievement a, String source, Long scenicAreaId, List<String> out) {
        userAchievementMapper.insertIfAbsent(userId, a.getId(), source, scenicAreaId);
        out.add(a.getTitle());
        log.info("[成就] 用户 {} 解锁成就（{}）{}", userId, source, a.getTitle());
    }

    private boolean matchVisitOrCheckin(Achievement a, Long userId, Long scenicAreaId) {
        Map<String, Object> cond = parse(a);
        if (cond == null) {
            return false;
        }
        try {
            if ("visit".equals(a.getTriggerType())) {
                List<Integer> spotIds = (List<Integer>) cond.get("spot_ids");
                if (spotIds == null || spotIds.isEmpty()) {
                    return false;
                }
                List<Long> longIds = spotIds.stream().map(Long::valueOf).toList();
                int need = cond.containsKey("count") ? ((Number) cond.get("count")).intValue() : 1;
                long have = userAchievementMapper.countCompletedSpots(userId, longIds);
                return have >= need;
            }
            if ("checkin".equals(a.getTriggerType())) {
                int need = cond.containsKey("spot_count") ? ((Number) cond.get("spot_count")).intValue() : 1;
                Object areaObj = cond.get("scenic_area_id");
                Long area = areaObj != null ? ((Number) areaObj).longValue() : scenicAreaId;
                if (area == null) {
                    return false;
                }
                long have = userAchievementMapper.countCompletedSpotsInArea(userId, area);
                return have >= need;
            }
        } catch (Exception e) {
            log.warn("[成就] 触发条件判定异常 achievementId={} type={}", a.getId(), a.getTriggerType(), e);
        }
        return false;
    }

    private boolean matchRoute(Achievement a, Long userId, Long routeId) {
        Map<String, Object> cond = parse(a);
        if (cond == null) {
            return false;
        }
        try {
            Object ridObj = cond.get("route_id");
            Long rid = ridObj != null ? ((Number) ridObj).longValue() : null;
            if (rid == null || !rid.equals(routeId)) {
                return false;
            }
            int need = cond.containsKey("completed") ? ((Number) cond.get("completed")).intValue() : 1;
            if (need <= 1) {
                return true;
            }
            return visitRecordMapper.countByUserAndRoute(userId, routeId) >= need;
        } catch (Exception e) {
            log.warn("[成就] 触发条件判定异常 achievementId={} type=route", a.getId(), e);
        }
        return false;
    }

    private boolean matchScript(Achievement a, Long userId, Long scriptId) {
        Map<String, Object> cond = parse(a);
        if (cond == null) {
            return false;
        }
        try {
            int need = cond.containsKey("completed") ? ((Number) cond.get("completed")).intValue() : 1;
            Object sidObj = cond.get("script_id");
            Long sid = sidObj != null ? ((Number) sidObj).longValue() : null;
            if (sid == null) {
                return false;
            }
            return userAchievementMapper.countCompletedScript(userId, sid) >= need;
        } catch (Exception e) {
            log.warn("[成就] 触发条件判定异常 achievementId={} type={}", a.getId(), a.getTriggerType(), e);
        }
        return false;
    }

    private Map<String, Object> parse(Achievement a) {
        if (!StringUtils.hasText(a.getTriggerCondition())) {
            return null;
        }
        try {
            return objectMapper.readValue(a.getTriggerCondition(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[成就] 触发条件 JSON 解析失败 achievementId={} cond={}", a.getId(), a.getTriggerCondition(), e);
            return null;
        }
    }
}
