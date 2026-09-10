package com.redtour.business.controller;

import com.redtour.business.dto.SttResult;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.exception.GlobalExceptionHandler;
import com.redtour.business.service.SttService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SttControllerTest {

    private SttService sttService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sttService = mock(SttService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SttController(sttService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldProxyAudioAndReturnUnifiedResponse() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "visitor.wav", "audio/wav", "RIFF-audio".getBytes());
        SttResult result = new SttResult();
        result.setText("遵义会议具有重要历史意义。");
        result.setDurationMs(180L);
        when(sttService.transcribe(audio)).thenReturn(result);

        mockMvc.perform(multipart("/api/v1/stt").file(audio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.text").value("遵义会议具有重要历史意义。"))
                .andExpect(jsonPath("$.data.durationMs").value(180));

        verify(sttService).transcribe(audio);
    }

    @Test
    void shouldReturnParameterErrorWhenAudioPartIsMissing() throws Exception {
        when(sttService.transcribe(null))
                .thenThrow(new BusinessException(400, "音频文件不能为空"));

        mockMvc.perform(multipart("/api/v1/stt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("音频文件不能为空"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

}
