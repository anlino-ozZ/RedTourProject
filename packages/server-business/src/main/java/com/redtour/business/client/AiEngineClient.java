package com.redtour.business.client;

import com.redtour.business.dto.AskRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 引擎 HTTP 客户端
 * 对接 packages/ai-engine（:8001）的 /engine/health、/engine/ask 接口
 * 所有对外方法均捕获异常并返回降级结果，避免上层业务被网络抖动打断
 */
@Slf4j
@Component
public class AiEngineClient {

    private final RestTemplate restTemplate;

    @Value("${ai-engine.base-url}")
    private String baseUrl;

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    public AiEngineClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** AI 引擎健康检查 */
    public Map<String, Object> health() {
        try {
            ResponseEntity<Map<String, Object>> resp =
                    restTemplate.exchange(baseUrl + "/engine/health", HttpMethod.GET, null, MAP_TYPE);
            return resp.getBody();
        } catch (Exception e) {
            log.error("[AI] 健康检查失败: {}", e.getMessage());
            return Map.of("status", "unavailable");
        }
    }

    /**
     * 智能问答：透传 AI 引擎，失败时返回降级响应
     * - 引擎连接失败（ResourceAccessException）：返回带 fallback 答案的结构
     * - 其他 4xx/5xx：记录日志并返回错误说明
     */
    public Map<String, Object> ask(AskRequest req) {
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    baseUrl + "/engine/ask", HttpMethod.POST, new HttpEntity<>(req), MAP_TYPE);
            return resp.getBody();
        } catch (ResourceAccessException e) {
            log.error("[AI] ask 请求失败：引擎连接超时或不可达 (question={})，降级返回",
                    req == null ? null : req.getQuestion(), e);
            return buildFallback("AI 引擎暂不可用，请稍后再试。");
        } catch (RestClientException e) {
            log.error("[AI] ask 请求异常 (question={})",
                    req == null ? null : req.getQuestion(), e);
            return buildFallback("问答服务异常，请联系管理员。");
        } catch (Exception e) {
            log.error("[AI] ask 未知异常", e);
            return buildFallback("问答服务遇到未知错误。");
        }
    }

    /** 构造降级响应 Map，字段与接口文档 AskResult 保持一致 */
    private Map<String, Object> buildFallback(String answer) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("question", "");
        m.put("answer", answer);
        m.put("sources", new ArrayList<>());
        m.put("durationMs", 0);
        m.put("audioUrl", null);
        return m;
    }
}
