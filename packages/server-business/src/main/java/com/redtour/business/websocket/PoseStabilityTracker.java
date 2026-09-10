package com.redtour.business.websocket;

import com.redtour.business.dto.PoseRecognizeResult;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/**
 * 连续帧稳定门：仅第 N 个同动作高置信度结果保留 triggerContent。
 */
public class PoseStabilityTracker {

    private static final Set<String> ACTIONS = Set.of("salute", "mill", "wave");

    private final int stableFrames;
    private final double minConfidence;
    private String lastAction;
    private int consecutiveFrames;

    public PoseStabilityTracker(int stableFrames, double minConfidence) {
        this.stableFrames = Math.max(1, stableFrames);
        this.minConfidence = Math.max(0.0, Math.min(1.0, minConfidence));
    }

    public synchronized PoseRecognizeResult gate(PoseRecognizeResult result) {
        if (result == null) {
            result = new PoseRecognizeResult();
        }
        String action = result.getAction() == null
                ? "unknown" : result.getAction().trim().toLowerCase(Locale.ROOT);
        double confidence = result.getConfidence() == null ? 0.0 : result.getConfidence();
        if (!ACTIONS.contains(action) || !Double.isFinite(confidence)) {
            action = "unknown";
            confidence = 0.0;
        } else {
            confidence = Math.max(0.0, Math.min(1.0, confidence));
        }
        result.setAction(action);
        result.setConfidence(confidence);
        if (result.getKeypoints() == null) {
            result.setKeypoints(new ArrayList<>());
        }
        if (result.getTimestamp() == null || result.getTimestamp() < 0) {
            result.setTimestamp(System.currentTimeMillis());
        }

        boolean eligible = !action.equals("unknown")
                && confidence >= minConfidence
                && result.getTriggerContent() != null;
        if (!eligible) {
            reset();
            result.setTriggerContent(null);
            return result;
        }
        if (action.equals(lastAction)) {
            consecutiveFrames++;
        } else {
            lastAction = action;
            consecutiveFrames = 1;
        }
        if (consecutiveFrames != stableFrames) {
            result.setTriggerContent(null);
        }
        return result;
    }

    private void reset() {
        lastAction = null;
        consecutiveFrames = 0;
    }
}
