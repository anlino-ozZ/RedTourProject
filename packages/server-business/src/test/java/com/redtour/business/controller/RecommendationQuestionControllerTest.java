package com.redtour.business.controller;

import com.redtour.business.dto.RecommendationQuestionResult;
import com.redtour.business.exception.GlobalExceptionHandler;
import com.redtour.business.service.RecommendationQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationQuestionControllerTest {

    private RecommendationQuestionService recommendationQuestionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        recommendationQuestionService = mock(RecommendationQuestionService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RecommendationQuestionController(recommendationQuestionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeRecommendationContract() throws Exception {
        when(recommendationQuestionService.listRecommendations(1L)).thenReturn(List.of(
                new RecommendationQuestionResult(10L, "遵义会议有什么重要意义？"),
                new RecommendationQuestionResult(11L, "四渡赤水经历了哪些战役？")
        ));

        mockMvc.perform(get("/api/v1/ask/recommendations").param("scenicAreaId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].question").value("遵义会议有什么重要意义？"));

        verify(recommendationQuestionService).listRecommendations(1L);
    }

    @Test
    void shouldRejectMissingScenicAreaId() throws Exception {
        mockMvc.perform(get("/api/v1/ask/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("缺少必传参数: scenicAreaId"));

        verifyNoInteractions(recommendationQuestionService);
    }

    @Test
    void shouldRejectNonPositiveScenicAreaId() throws Exception {
        mockMvc.perform(get("/api/v1/ask/recommendations").param("scenicAreaId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("景区ID必须为正整数"));

        verifyNoInteractions(recommendationQuestionService);
    }
}
