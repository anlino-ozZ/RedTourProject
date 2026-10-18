package com.redtour.business.service;

import java.util.List;

/**
 * 成就解锁评估服务。
 * 在「打卡 / 路线完成 / 剧本完成」等业务事件后调用，按 achievement 的 trigger_type / trigger_condition
 * 判定当前用户是否满足解锁条件，满足则写入 user_achievement（幂等）。
 * 返回本次新解锁的成就标题列表，供业务接口回传给前端做即时提示。
 */
public interface AchievementEvaluationService {

    /** 打卡 / 路线完成事件：评估 visit（游览）、checkin（按景区打卡数）类成就。 */
    List<String> evaluateOnCheckin(Long userId, Long scenicAreaId);

    /** 剧本完成事件：评估 script（完成指定剧本）类成就。 */
    List<String> evaluateOnScriptComplete(Long userId, Long scriptId, Long scenicAreaId);

    /** 路线完成事件：评估 route（完成指定路线）类成就。 */
    List<String> evaluateOnRouteComplete(Long userId, Long routeId, Long scenicAreaId);
}
