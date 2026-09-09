package com.redtour.business.client;

import com.redtour.business.dto.AskRequest;
import com.redtour.business.dto.AskResult;
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
    public AskResult ask(AskRequest req) {
        try {
            ResponseEntity<AskResult> resp = restTemplate.exchange(
                    baseUrl + "/engine/ask", HttpMethod.POST, new HttpEntity<>(req), AskResult.class);
            AskResult result = resp.getBody();
            return result == null ? buildFallback(req, "AI 引擎返回空响应，请稍后再试。")
                    : normalizeResult(req, result);
        } catch (ResourceAccessException e) {
            log.error("[AI] ask 请求失败：引擎连接超时或不可达 (question={})，降级返回",
                    req == null ? null : req.getQuestion(), e);
            return buildFallback(req, "AI 引擎暂不可用，请稍后再试。");
        } catch (RestClientException e) {
            log.error("[AI] ask 请求异常 (question={})",
                    req == null ? null : req.getQuestion(), e);
            return buildFallback(req, "问答服务异常，请联系管理员。");
        } catch (Exception e) {
            log.error("[AI] ask 未知异常", e);
            return buildFallback(req, "问答服务遇到未知错误。");
        }
    }

    /** 补齐 AI 引擎可能缺省的字段。 */
    private AskResult normalizeResult(AskRequest request, AskResult result) {
        if (result.getQuestion() == null || result.getQuestion().isBlank()) {
            result.setQuestion(request == null ? "" : request.getQuestion());
        }
        if (result.getAnswer() == null || result.getAnswer().isBlank()) {
            result.setAnswer("AI 引擎未返回有效回答，请稍后再试。");
        }
        if (result.getSources() == null) {
            result.setSources(new ArrayList<>());
        }
        if (result.getDurationMs() == null || result.getDurationMs() < 0) {
            result.setDurationMs(0L);
        }
        return result;
    }

    /** 构造降级响应，字段与接口文档 AskResult 保持一致。 */
    private AskResult buildFallback(AskRequest request, String answer) {
        AskResult result = new AskResult();
        result.setQuestion(request == null || request.getQuestion() == null ? "" : request.getQuestion());
        result.setAnswer(answer);
        result.setSources(new ArrayList<>());
        result.setDurationMs(0L);
        result.setAudioUrl(null);
        return result;
    }
}
