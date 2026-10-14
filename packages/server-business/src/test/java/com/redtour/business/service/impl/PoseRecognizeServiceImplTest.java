package com.redtour.business.service.impl;

import com.redtour.business.client.AiEngineClient;
import com.redtour.business.dto.PoseRecognizeRequest;
import com.redtour.business.dto.PoseRecognizeResult;
import com.redtour.business.dto.PoseTriggerContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PoseRecognizeServiceImplTest {

    @Test
    void shouldNormalizeUnknownActionAndInvalidFields() {
        AiEngineClient client = mock(AiEngineClient.class);
        PoseRecognizeServiceImpl service = new PoseRecognizeServiceImpl(client);
        PoseRecognizeRequest request = request();
        PoseRecognizeResult engineResult = new PoseRecognizeResult();
        engineResult.setAction("invalid");
        engineResult.setConfidence(2.0);
        engineResult.setKeypoints(null);
        engineResult.setTriggerContent(new PoseTriggerContent());
        engineResult.setTimestamp(-1L);
        when(client.recognizePose(request)).thenReturn(engineResult);

        PoseRecognizeResult result = service.recognize(request);

        assertEquals("unknown", result.getAction());
        assertEquals(0.0, result.getConfidence());
        assertEquals(List.of(), result.getKeypoints());
        assertNull(result.getTriggerContent());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void shouldPreserveValidRecognizedAction() {
        AiEngineClient client = mock(AiEngineClient.class);
        PoseRecognizeServiceImpl service = new PoseRecognizeServiceImpl(client);
        PoseRecognizeRequest request = request();
        PoseTriggerContent trigger = new PoseTriggerContent();
        trigger.setTitle("军礼的由来");
        trigger.setAudioUrl("/audio/pose/salute.mp3");
        trigger.setWikiRef("wiki/军礼");
        PoseRecognizeResult engineResult = new PoseRecognizeResult();
        engineResult.setAction("SALUTE");
        engineResult.setConfidence(0.92);
        engineResult.setKeypoints(List.of(List.of(1.0, 2.0)));
        engineResult.setTriggerContent(trigger);
        engineResult.setTimestamp(123L);
        when(client.recognizePose(request)).thenReturn(engineResult);

        PoseRecognizeResult result = service.recognize(request);

        assertEquals("salute", result.getAction());
        assertEquals(0.92, result.getConfidence());
        assertEquals(trigger, result.getTriggerContent());
        assertEquals(123L, result.getTimestamp());
    }

    @Test
    void shouldReturnCompleteFallbackForEmptyEngineResponse() {
        AiEngineClient client = mock(AiEngineClient.class);
        PoseRecognizeServiceImpl service = new PoseRecognizeServiceImpl(client);
        PoseRecognizeRequest request = request();
        when(client.recognizePose(request)).thenReturn(null);

        PoseRecognizeResult result = service.recognize(request);

        assertEquals("unknown", result.getAction());
        assertEquals(0.0, result.getConfidence());
        assertEquals(List.of(), result.getKeypoints());
        assertNull(result.getTriggerContent());
        assertNotNull(result.getTimestamp());
    }

    private PoseRecognizeRequest request() {
        PoseRecognizeRequest request = new PoseRecognizeRequest();
        request.setFrame("aW1hZ2U=");
        request.setScenicAreaId(1L);
        return request;
    }
}
