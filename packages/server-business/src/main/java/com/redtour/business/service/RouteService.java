package com.redtour.business.service;

import com.redtour.business.dto.RouteCompleteResult;

/**
 * 路线完成服务（需登录）。
 */
public interface RouteService {

    /** 完成路线：校验全部景点已打卡 → 写 visit_record。 */
    RouteCompleteResult complete(Long routeId);
}
