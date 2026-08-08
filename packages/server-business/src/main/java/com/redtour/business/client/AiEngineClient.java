package com.redtour.business.client;

import com.redtour.business.dto.AskRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * AI 引擎 HTTP 客户端
 * 对接 packages/ai-engine（:8001）的 /engine/health、/engine/ask 接口
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

    /** 智能问答：透传 AI 引擎，返回引擎响应体 */
    public Map<String, Object> ask(AskRequest req) {
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                baseUrl + "/engine/ask", HttpMethod.POST, new HttpEntity<>(req), MAP_TYPE);
        return resp.getBody();
    }
}
