package com.redtour.business.client;

import com.redtour.business.dto.AskRequest;
import com.redtour.business.dto.AskResult;
import com.redtour.business.dto.SttResult;
import com.redtour.business.dto.WikiCompileRequest;
import com.redtour.business.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AI 引擎 HTTP 客户端
 * 对接 packages/ai-engine（:8001）的健康检查、问答、Wiki 编译、STT 与 TTS 接口。
 * 外部依赖异常统一转换为稳定降级结果或业务异常，避免 SDK 对象和底层堆栈泄漏。
 */
@Slf4j
@Component
public class AiEngineClient {

    private static final Pattern TTS_FILENAME = Pattern.compile("ask_[0-9a-f]{32}\\.wav");

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

    /**
     * 调用 AI 引擎 Wiki 编译接口。
     * 编译服务不可用时返回 status=failed，由 Wiki 业务层统一落库为失败状态。
     */
    public Map<String, Object> compileWiki(WikiCompileRequest request) {
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    baseUrl + "/engine/wiki/compile", HttpMethod.POST,
                    new HttpEntity<>(request), MAP_TYPE);
            Map<String, Object> body = resp.getBody();
            return body == null ? Map.of("status", "failed", "error", "AI 引擎返回空响应") : body;
        } catch (ResourceAccessException e) {
            log.error("[AI] Wiki 编译引擎不可达 (scenicAreaId={})", request == null ? null : request.getScenicAreaId(), e);
            return Map.of("status", "failed", "error", "AI 引擎暂不可用");
        } catch (RestClientException e) {
            log.error("[AI] Wiki 编译请求异常 (scenicAreaId={})",
                    request == null ? null : request.getScenicAreaId(), e);
            return Map.of("status", "failed", "error", "Wiki 编译服务异常");
        } catch (Exception e) {
            log.error("[AI] Wiki 编译未知异常", e);
            return Map.of("status", "failed", "error", "Wiki 编译服务异常");
        }
    }

    /** 获取 AI 引擎生成的 TTS 音频字节；非法文件名或引擎异常统一返回 null。 */
    public byte[] fetchTtsAudio(String filename) {
        if (filename == null || !TTS_FILENAME.matcher(filename).matches()) {
            return null;
        }
        try {
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    baseUrl + "/engine/audio/" + filename, HttpMethod.GET, null, byte[].class);
            byte[] body = resp.getBody();
            return body == null || body.length == 0 ? null : body;
        } catch (Exception e) {
            log.warn("[AI] TTS 音频获取失败 (filename={})", filename, e);
            return null;
        }
    }

    /** 将上传音频作为 multipart 的 audio 字段转发给 AI 引擎。 */
    public SttResult transcribe(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(400, "音频文件不能为空");
        }
        try {
            byte[] bytes = audio.getBytes();
            String filename = safeFilename(audio.getOriginalFilename());
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentType(parseMediaType(audio.getContentType()));
            HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, partHeaders);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("audio", filePart);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<SttResult> response = restTemplate.exchange(
                    baseUrl + "/engine/stt",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    SttResult.class);
            SttResult result = response.getBody();
            if (result == null) {
                throw new BusinessException(5001, "AI 引擎返回空响应");
            }
            return result;
        } catch (HttpClientErrorException e) {
            throw mapSttClientError(e);
        } catch (HttpServerErrorException e) {
            log.warn("[AI] STT 引擎错误 (status={}, size={})",
                    e.getStatusCode().value(), audio.getSize());
            if (e.getStatusCode().value() == 503) {
                throw new BusinessException(503, "语音识别服务暂不可用");
            }
            throw new BusinessException(5001, "语音识别服务异常");
        } catch (ResourceAccessException e) {
            log.error("[AI] STT 引擎连接超时或不可达 (size={})", audio.getSize(), e);
            throw new BusinessException(503, "语音识别服务暂不可用");
        } catch (IOException e) {
            log.warn("[AI] STT 上传音频读取失败 (size={})", audio.getSize(), e);
            throw new BusinessException(400, "音频文件读取失败");
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("[AI] STT 请求异常 (size={})", audio.getSize(), e);
            throw new BusinessException(5001, "语音识别服务异常");
        } catch (Exception e) {
            log.error("[AI] STT 未知异常 (size={})", audio.getSize(), e);
            throw new BusinessException(5001, "语音识别服务异常");
        }
    }

    private BusinessException mapSttClientError(HttpClientErrorException exception) {
        int status = exception.getStatusCode().value();
        return switch (status) {
            case 400 -> new BusinessException(400, "音频格式不受支持或文件无效");
            case 413 -> new BusinessException(413, "音频文件超过大小限制");
            case 422 -> new BusinessException(422, "音频中未识别到有效语音，或时长超过限制");
            default -> new BusinessException(5001, "语音识别服务异常");
        };
    }

    private MediaType parseMediaType(String contentType) {
        try {
            return contentType == null || contentType.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "audio.wav";
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replace('\r', '_')
                .replace('\n', '_');
        return filename.isBlank() ? "audio.wav" : filename;
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
