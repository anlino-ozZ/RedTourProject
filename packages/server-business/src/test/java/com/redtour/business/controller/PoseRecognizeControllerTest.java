package com.redtour.business.controller;

import com.redtour.business.dto.PoseRecognizeRequest;
import com.redtour.business.dto.PoseRecognizeResult;
import com.redtour.business.dto.PoseTriggerContent;
import com.redtour.business.exception.GlobalExceptionHandler;
import com.redtour.business.service.PoseRecognizeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PoseRecognizeControllerTest {

    private PoseRecognizeService poseRecognizeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        poseRecognizeService = mock(PoseRecognizeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PoseRecognizeController(poseRecognizeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldExposePoseRecognitionContract() throws Exception {
        PoseTriggerContent trigger = new PoseTriggerContent();
        trigger.setTitle("军礼的由来");
        trigger.setAudioUrl("/audio/pose/salute.mp3");
        trigger.setWikiRef("wiki/军礼");
        PoseRecognizeResult result = new PoseRecognizeResult();
        result.setAction("salute");
        result.setConfidence(0.92);
        result.setKeypoints(List.of(List.of(120.0, 80.0)));
        result.setTriggerContent(trigger);
        result.setTimestamp(1_730_000_000_000L);
        when(poseRecognizeService.recognize(any(PoseRecognizeRequest.class)))
                .thenReturn(result);

        mockMvc.perform(post("/api/v1/pose/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"frame":"aW1hZ2U=","scenicAreaId":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.action").value("salute"))
                .andExpect(jsonPath("$.data.confidence").value(0.92))
                .andExpect(jsonPath("$.data.keypoints[0][0]").value(120.0))
                .andExpect(jsonPath("$.data.triggerContent.wikiRef").value("wiki/军礼"))
                .andExpect(jsonPath("$.data.timestamp").value(1_730_000_000_000L));

        verify(poseRecognizeService).recognize(any(PoseRecognizeRequest.class));
    }

    @Test
    void shouldRejectBlankFrame() throws Exception {
        mockMvc.perform(post("/api/v1/pose/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frame\":\" \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("frame 不能为空"));
    }
}
