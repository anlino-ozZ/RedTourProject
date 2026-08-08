package com.redtour.business.service;

import java.util.Map;

/**
 * 健康检查服务
 */
public interface HealthService {

    /**
     * 聚合检查：数据库 / Redis / AI 引擎 / 硬件 TCP
     * @return 各组件状态
     */
    Map<String, Object> check();
}
