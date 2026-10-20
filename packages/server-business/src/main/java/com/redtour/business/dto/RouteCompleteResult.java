package com.redtour.business.dto;

import lombok.Data;

import java.util.List;

/**
 * 路线完成结果响应。
 */
@Data
public class RouteCompleteResult {

    private Long routeId;
    private boolean completed;
    /** 路线预计游览时长（分钟） */
    private Integer duration;
    /** 本次解锁的成就标题列表 */
    private List<String> achievements;
}
