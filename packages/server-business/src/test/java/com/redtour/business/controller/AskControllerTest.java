package com.redtour.business.controller;

import com.redtour.business.dto.AskRequest;
import com.redtour.business.dto.AskResult;
import com.redtour.business.exception.GlobalExceptionHandler;
import com.redtour.business.service.AskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AskControllerTest {

    private AskService askService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        askService = mock(AskService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AskController(askService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeAskContractAndForwardDeviceHeader() throws Exception {
        AskResult result = new AskResult();
        result.setQuestion("遵义会议的意义是什么？");
        result.setAnswer("遵义会议是重要转折点。");
        result.setSources(List.of("wiki/遵义会议"));
        result.setDurationMs(1800L);
        when(askService.ask(any(AskRequest.class), eq("rpi-001"))).thenReturn(result);

        mockMvc.perform(post("/api/v1/ask")
                        .header("X-Device-Id", "rpi-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"遵义会议的意义是什么？","scenicAreaId":1,"useVoice":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.question").value("遵义会议的意义是什么？"))
                .andExpect(jsonPath("$.data.answer").value("遵义会议是重要转折点。"))
                .andExpect(jsonPath("$.data.sources[0]").value("wiki/遵义会议"))
                .andExpect(jsonPath("$.data.durationMs").value(1800))
                .andExpect(jsonPath("$.data.audioUrl").doesNotExist());

        ArgumentCaptor<AskRequest> requestCaptor = ArgumentCaptor.forClass(AskRequest.class);
        verify(askService).ask(requestCaptor.capture(), eq("rpi-001"));
        assertEquals(1L, requestCaptor.getValue().getScenicAreaId());
    }

    @Test
    void shouldRejectBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \",\"scenicAreaId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("问题不能为空"));

        verifyNoInteractions(askService);
    }
}
