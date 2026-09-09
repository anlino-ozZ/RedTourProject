package com.redtour.business.service.impl;

import com.redtour.business.dto.RecommendationQuestionResult;
import com.redtour.business.entity.RecommendationQuestion;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.RecommendationQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecommendationQuestionServiceImplTest {

    private RecommendationQuestionMapper recommendationQuestionMapper;
    private RecommendationQuestionServiceImpl recommendationQuestionService;

    @BeforeEach
    void setUp() {
        recommendationQuestionMapper = mock(RecommendationQuestionMapper.class);
        recommendationQuestionService = new RecommendationQuestionServiceImpl(recommendationQuestionMapper);
    }

    @Test
    void shouldOrderConfiguredQuestionsAndFillToFive() {
        when(recommendationQuestionMapper.findTopByScenicAreaId(1L)).thenReturn(List.of(
                question(8L, "低权重问题", 20),
                question(5L, "同权重较大ID问题", 80),
                question(2L, "最高权重问题", 100),
                question(3L, "同权重较小ID问题", 80)
        ));

        List<RecommendationQuestionResult> result =
                recommendationQuestionService.listRecommendations(1L);

        assertEquals(5, result.size());
        assertEquals(List.of(2L, 3L, 5L, 8L, -1L),
                result.stream().map(RecommendationQuestionResult::getId).toList());
        verify(recommendationQuestionMapper).findTopByScenicAreaId(1L);
    }

    @Test
    void shouldReturnFiveOfflineQuestionsWhenScenicAreaHasNoConfiguration() {
        when(recommendationQuestionMapper.findTopByScenicAreaId(99L)).thenReturn(List.of());

        List<RecommendationQuestionResult> result =
                recommendationQuestionService.listRecommendations(99L);

        assertEquals(5, result.size());
        assertTrue(result.stream().allMatch(item -> item.getId() < 0));
        assertTrue(result.stream().allMatch(item -> !item.getQuestion().isBlank()));
    }

    @Test
    void shouldReturnOfflineQuestionsWhenDatabaseQueryFails() {
        when(recommendationQuestionMapper.findTopByScenicAreaId(1L))
                .thenThrow(new RuntimeException("database unavailable"));

        List<RecommendationQuestionResult> result =
                recommendationQuestionService.listRecommendations(1L);

        assertEquals(5, result.size());
    }

    @Test
    void shouldLimitConfiguredQuestionsToTen() {
        List<RecommendationQuestion> configuredQuestions = LongStream.rangeClosed(1, 11)
                .mapToObj(id -> question(id, "问题" + id, 100 - (int) id))
                .toList();
        when(recommendationQuestionMapper.findTopByScenicAreaId(1L))
                .thenReturn(configuredQuestions);

        List<RecommendationQuestionResult> result =
                recommendationQuestionService.listRecommendations(1L);

        assertEquals(10, result.size());
        assertEquals(10L, result.get(9).getId());
    }

    @Test
    void shouldRejectInvalidScenicAreaIdBeforeQueryingDatabase() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> recommendationQuestionService.listRecommendations(0L));

        assertEquals(400, exception.getCode());
        assertEquals("景区ID必须为正整数", exception.getMessage());
        verifyNoInteractions(recommendationQuestionMapper);
    }

    private RecommendationQuestion question(Long id, String text, Integer sortWeight) {
        RecommendationQuestion question = new RecommendationQuestion();
        question.setId(id);
        question.setScenicAreaId(1L);
        question.setQuestion(text);
        question.setSortWeight(sortWeight);
        return question;
    }
}
