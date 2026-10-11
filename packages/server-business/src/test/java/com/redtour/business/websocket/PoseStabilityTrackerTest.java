package com.redtour.business.websocket;

import com.redtour.business.dto.PoseRecognizeResult;
import com.redtour.business.dto.PoseTriggerContent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PoseStabilityTrackerTest {

    @Test
    void shouldExposeTriggerOnlyOnThirdStableFrame() {
        PoseStabilityTracker tracker = new PoseStabilityTracker(3, 0.85);

        assertNull(tracker.gate(result("salute", 0.92)).getTriggerContent());
        assertNull(tracker.gate(result("salute", 0.93)).getTriggerContent());
        assertNotNull(tracker.gate(result("salute", 0.94)).getTriggerContent());
        assertNull(tracker.gate(result("salute", 0.95)).getTriggerContent());
    }

    @Test
    void shouldResetWhenActionChangesOrConfidenceDrops() {
        PoseStabilityTracker tracker = new PoseStabilityTracker(3, 0.85);

        assertNull(tracker.gate(result("salute", 0.92)).getTriggerContent());
        assertNull(tracker.gate(result("wave", 0.92)).getTriggerContent());
        assertNull(tracker.gate(result("wave", 0.80)).getTriggerContent());
        assertNull(tracker.gate(result("wave", 0.92)).getTriggerContent());
        assertNull(tracker.gate(result("wave", 0.92)).getTriggerContent());
        assertNotNull(tracker.gate(result("wave", 0.92)).getTriggerContent());
    }

    @Test
    void shouldNeverExposeUnknownTrigger() {
        PoseStabilityTracker tracker = new PoseStabilityTracker(1, 0.0);

        assertNull(tracker.gate(result("unknown", 1.0)).getTriggerContent());
        assertNull(tracker.gate(result("invalid", 1.0)).getTriggerContent());
    }

    private PoseRecognizeResult result(String action, double confidence) {
        PoseTriggerContent trigger = new PoseTriggerContent();
        trigger.setTitle("互动内容");
        trigger.setAudioUrl("/audio/pose/action.mp3");
        trigger.setWikiRef("wiki/互动");
        PoseRecognizeResult result = new PoseRecognizeResult();
        result.setAction(action);
        result.setConfidence(confidence);
        result.setTriggerContent(trigger);
        result.setTimestamp(123L);
        return result;
    }
}
