package com.redtour.business.service.impl;

import com.redtour.business.client.AiEngineClient;
import com.redtour.business.client.HardwareTcpClient;
import com.redtour.business.mapper.HealthMapper;
import com.redtour.business.service.HealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查服务实现：聚合 DB / Redis / AI 引擎 / 硬件 TCP 四项检查
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthServiceImpl implements HealthService {

    private final HealthMapper healthMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AiEngineClient aiEngineClient;
    private final HardwareTcpClient hardwareTcpClient;

    @Override
    public Map<String, Object> check() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 数据库（mapper 层）
        try {
            result.put("db", healthMapper.checkDb() != null ? "ok" : "down");
        } catch (Exception e) {
            log.warn("[健康检查] 数据库异常: {}", e.getMessage());
            result.put("db", "unavailable");
        }

        // Redis
        try {
            redisTemplate.opsForValue().set("health:probe", "1");
            result.put("redis", "ok");
        } catch (Exception e) {
            log.warn("[健康检查] Redis 异常: {}", e.getMessage());
            result.put("redis", "unavailable");
        }

        // AI 引擎（HTTP）
        try {
            Map<String, Object> ai = aiEngineClient.health();
            result.put("ai", ai == null ? "unknown" : ai.getOrDefault("status", "unknown"));
        } catch (Exception e) {
            result.put("ai", "unavailable");
        }

        // 树莓派硬件（TCP）
        try {
            String resp = hardwareTcpClient.sendCommand("{\"cmd\":\"ping\"}");
            result.put("hardware", resp != null ? "ok" : "down");
        } catch (Exception e) {
            log.warn("[健康检查] 硬件 TCP 异常: {}", e.getMessage());
            result.put("hardware", "unavailable");
        }

        return result;
    }
}
