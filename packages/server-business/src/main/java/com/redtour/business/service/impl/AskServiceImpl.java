package com.redtour.business.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redtour.business.client.AiEngineClient;
import com.redtour.business.common.ResultCode;
import com.redtour.business.dto.AskRequest;
import com.redtour.business.dto.AskResult;
import com.redtour.business.entity.AskLog;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.AskLogMapper;
import com.redtour.business.service.AskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 智能问答服务：透传 AI 引擎，并以不阻断回答的方式记录 AskLog。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AskServiceImpl implements AskService {

    private static final int DEVICE_ID_MAX_LENGTH = 50;

    private final AiEngineClient aiEngineClient;
    private final AskLogMapper askLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AskResult ask(AskRequest request, String deviceId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        AskResult result = aiEngineClient.ask(request);
        persistLog(request, result, normalizedDeviceId);
        return result;
    }

    private void persistLog(AskRequest request, AskResult result, String deviceId) {
        // 接口契约允许缺省景区，但现有 ask_log.scenic_area_id 为 NOT NULL。
        if (request.getScenicAreaId() == null) {
            log.warn("[问答日志] scenicAreaId 缺失，跳过日志写入 (question={})", request.getQuestion());
            return;
        }

        AskLog askLog = new AskLog();
        askLog.setScenicAreaId(request.getScenicAreaId());
        askLog.setQuestion(request.getQuestion());
        askLog.setAnswer(result.getAnswer());
        askLog.setSources(serializeSources(result.getSources()));
        askLog.setDurationMs(result.getDurationMs());
        askLog.setDeviceId(deviceId);
        try {
            askLogMapper.insert(askLog);
        } catch (Exception exception) {
            // 问答记录属于旁路数据，数据库短暂异常不能覆盖已经生成的 AI 回答。
            log.error("[问答日志] 写入失败 (scenicAreaId={}, question={})",
                    request.getScenicAreaId(), request.getQuestion(), exception);
        }
    }

    private String serializeSources(List<String> sources) {
        try {
            return objectMapper.writeValueAsString(sources == null ? List.of() : sources);
        } catch (JsonProcessingException exception) {
            log.warn("[问答日志] sources 序列化失败，按空列表记录", exception);
            return "[]";
        }
    }

    private String normalizeDeviceId(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        String normalized = deviceId.trim();
        if (normalized.length() > DEVICE_ID_MAX_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "设备ID长度不能超过50个字符");
        }
        return normalized;
    }
}
