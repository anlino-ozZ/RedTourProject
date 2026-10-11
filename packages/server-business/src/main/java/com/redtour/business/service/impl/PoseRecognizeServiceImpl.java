package com.redtour.business.service.impl;

import com.redtour.business.client.AiEngineClient;
import com.redtour.business.dto.PoseRecognizeRequest;
import com.redtour.business.dto.PoseRecognizeResult;
import com.redtour.business.service.PoseRecognizeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 姿态识别代理服务，统一清洗 AI 引擎返回字段。
 */
@Service
@RequiredArgsConstructor
public class PoseRecognizeServiceImpl implements PoseRecognizeService {

    private static final Set<String> ACTIONS = Set.of("salute", "mill", "wave", "unknown");

    private final AiEngineClient aiEngineClient;

    @Override
    public PoseRecognizeResult recognize(PoseRecognizeRequest request) {
        PoseRecognizeResult result = aiEngineClient.recognizePose(request);
        if (result == null) {
            return fallback();
        }
        String action = result.getAction() == null
                ? "unknown" : result.getAction().trim().toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            action = "unknown";
        }
        if (!action.equals("unknown") && !hasCompleteTrigger(result)) {
            action = "unknown";
        }
        result.setAction(action);
        double confidence = result.getConfidence() == null ? 0.0 : result.getConfidence();
        if (!Double.isFinite(confidence)) {
            confidence = 0.0;
        }
        result.setConfidence(action.equals("unknown")
                ? 0.0 : Math.max(0.0, Math.min(1.0, confidence)));
        result.setKeypoints(normalizeKeypoints(result.getKeypoints()));
        if (action.equals("unknown")) {
            result.setTriggerContent(null);
        }
        if (result.getTimestamp() == null || result.getTimestamp() < 0) {
            result.setTimestamp(System.currentTimeMillis());
        }
        return result;
    }

    private boolean hasCompleteTrigger(PoseRecognizeResult result) {
        return result.getTriggerContent() != null
                && result.getTriggerContent().getTitle() != null
                && !result.getTriggerContent().getTitle().isBlank()
                && result.getTriggerContent().getAudioUrl() != null
                && !result.getTriggerContent().getAudioUrl().isBlank()
                && result.getTriggerContent().getWikiRef() != null
                && !result.getTriggerContent().getWikiRef().isBlank();
    }

    private List<List<Double>> normalizeKeypoints(List<List<Double>> keypoints) {
        if (keypoints == null) {
            return new ArrayList<>();
        }
        List<List<Double>> normalized = new ArrayList<>();
        for (List<Double> point : keypoints) {
            if (point == null || point.size() < 2 || point.get(0) == null || point.get(1) == null) {
                continue;
            }
            double x = point.get(0);
            double y = point.get(1);
            if (Double.isFinite(x) && Double.isFinite(y)) {
                normalized.add(List.of(x, y));
            }
        }
        return normalized;
    }

    private PoseRecognizeResult fallback() {
        PoseRecognizeResult result = new PoseRecognizeResult();
        result.setAction("unknown");
        result.setConfidence(0.0);
        result.setKeypoints(new ArrayList<>());
        result.setTriggerContent(null);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }
}
