package com.redtour.business.service.impl;

import com.redtour.business.dto.RecommendationQuestionResult;
import com.redtour.business.entity.PopularQuestion;
import com.redtour.business.entity.RecommendationQuestion;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.AskLogMapper;
import com.redtour.business.mapper.RecommendationQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecommendationQuestionServiceImplTest {

    private AskLogMapper askLogMapper;
    private RecommendationQuestionMapper recommendationQuestionMapper;
    private RecommendationQuestionServiceImpl recommendationQuestionService;

    @BeforeEach
    void setUp() {
        askLogMapper = mock(AskLogMapper.class);
        recommendationQuestionMapper = mock(RecommendationQuestionMapper.class);
        recommendationQuestionService = new RecommendationQuestionServiceImpl(
                askLogMapper, recommendationQuestionMapper);
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
    void shouldPrioritizePopularQuestionsAndFillWithConfiguredQuestions() {
        LocalDateTime now = LocalDateTime.now();
        when(askLogMapper.findPopularQuestions(eq(1L), any(LocalDateTime.class), eq(30)))
                .thenReturn(List.of(
                        popularQuestion(101L, "遵义会议有什么重要意义？", 8L, now),
                        popularQuestion(102L, "四渡赤水经历了哪些战役？", 5L, now.minusHours(1))
                ));
        when(recommendationQuestionMapper.findTopByScenicAreaId(1L)).thenReturn(List.of(
                question(1L, "静态推荐一", 100),
                question(2L, "静态推荐二", 90),
                question(3L, "静态推荐三", 80)
        ));

        List<RecommendationQuestionResult> result =
                recommendationQuestionService.listRecommendations(1L);

        assertEquals(List.of(101L, 102L, 1L, 2L, 3L),
                result.stream().map(RecommendationQuestionResult::getId).toList());
        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(askLogMapper).findPopularQuestions(eq(1L), sinceCaptor.capture(), eq(30));
        LocalDateTime expectedSince = LocalDateTime.now().minusDays(30);
        assertTrue(sinceCaptor.getValue().isAfter(expectedSince.minusSeconds(2)));
        assertTrue(sinceCaptor.getValue().isBefore(expectedSince.plusSeconds(2)));
    }

    @Test
    void shouldCleanMergeAndIgnoreMeaninglessPopularQuestions() {
        when(askLogMapper.findPopularQuestions(eq(1L), any(LocalDateTime.class), eq(30)))
                .thenReturn(List.of(
                        popularQuestion(101L, "  遵义会议   有什么意义？ ", 9L, LocalDateTime.now()),
                        popularQuestion(102L, "遵义会议 有什么意义", 8L, LocalDateTime.now()),
                        popularQuestion(103L, "哈哈哈哈", 7L, LocalDateTime.now()),
                        popularQuestion(104L, "!!!", 6L, LocalDateTime.now()),
                        popularQuestion(105L, "四渡赤水是什么？", 5L, LocalDateTime.now())
                ));
        when(recommendationQuestionMapper.findTopByScenicAreaId(1L)).thenReturn(List.of(
                question(1L, "遵义会议 有什么意义。", 100)
        ));

        List<RecommendationQuestionResult> result =
                recommendationQuestionService.listRecommendations(1L);

        assertEquals("遵义会议 有什么意义？", result.get(0).getQuestion());
        assertEquals("四渡赤水是什么？", result.get(1).getQuestion());
        assertEquals(5, result.size());
        assertEquals(1, result.stream()
                .filter(item -> item.getQuestion().contains("遵义会议"))
                .count());
        assertFalse(result.stream().anyMatch(item -> item.getQuestion().contains("哈哈")));
    }

    @Test
    void shouldUseConfiguredQuestionsWhenPopularQueryFails() {
        when(askLogMapper.findPopularQuestions(eq(1L), any(LocalDateTime.class), eq(30)))
                .thenThrow(new RuntimeException("ask log unavailable"));
        when(recommendationQuestionMapper.findTopByScenicAreaId(1L)).thenReturn(List.of(
                question(8L, "仍可展示的静态问题", 20)
        ));

        List<RecommendationQuestionResult> result =
                recommendationQuestionService.listRecommendations(1L);

        assertEquals(5, result.size());
        assertEquals(8L, result.get(0).getId());
    }

    @Test
    void shouldUsePopularQuestionsWhenConfiguredQueryFails() {
        when(askLogMapper.findPopularQuestions(eq(1L), any(LocalDateTime.class), eq(30)))
                .thenReturn(List.of(
                        popularQuestion(101L, "热门问题一", 9L, LocalDateTime.now()),
                        popularQuestion(102L, "热门问题二", 8L, LocalDateTime.now())
                ));
        when(recommendationQuestionMapper.findTopByScenicAreaId(1L))
                .thenThrow(new RuntimeException("configuration unavailable"));

        List<RecommendationQuestionResult> result =
                recommendationQuestionService.listRecommendations(1L);

        assertEquals(5, result.size());
        assertEquals(List.of(101L, 102L), result.subList(0, 2).stream()
                .map(RecommendationQuestionResult::getId).toList());
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
        verifyNoInteractions(askLogMapper);
        verifyNoInteractions(recommendationQuestionMapper);
    }

    private PopularQuestion popularQuestion(
            Long id, String text, Long count, LocalDateTime latestAskedAt) {
        PopularQuestion question = new PopularQuestion();
        question.setId(id);
        question.setQuestion(text);
        question.setAskCount(count);
        question.setLatestAskedAt(latestAskedAt);
        return question;
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
