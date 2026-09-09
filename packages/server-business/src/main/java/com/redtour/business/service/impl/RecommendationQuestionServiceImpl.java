package com.redtour.business.service.impl;

import com.redtour.business.common.ResultCode;
import com.redtour.business.dto.RecommendationQuestionResult;
import com.redtour.business.entity.RecommendationQuestion;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.RecommendationQuestionMapper;
import com.redtour.business.service.RecommendationQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 推荐问题服务：优先读取景区配置，并以统一离线问题保证基本可用性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationQuestionServiceImpl implements RecommendationQuestionService {

    private static final int MIN_RECOMMENDATION_COUNT = 5;
    private static final int MAX_RECOMMENDATION_COUNT = 10;
    private static final List<RecommendationQuestionResult> OFFLINE_RECOMMENDATIONS = List.of(
            new RecommendationQuestionResult(-1L, "这个景区有哪些必看景点？"),
            new RecommendationQuestionResult(-2L, "这里发生过哪些重要历史事件？"),
            new RecommendationQuestionResult(-3L, "可以推荐一条适合首次参观的游览路线吗？"),
            new RecommendationQuestionResult(-4L, "景区内有哪些值得了解的红色文化故事？"),
            new RecommendationQuestionResult(-5L, "参观这个景区时有哪些注意事项？")
    );

    private static final Comparator<RecommendationQuestion> CONFIGURED_ORDER = Comparator
            .<RecommendationQuestion>comparingInt(item -> item.getSortWeight() == null
                    ? Integer.MIN_VALUE : item.getSortWeight())
            .reversed()
            .thenComparingLong(item -> item.getId() == null ? Long.MAX_VALUE : item.getId());

    private final RecommendationQuestionMapper recommendationQuestionMapper;

    @Override
    public List<RecommendationQuestionResult> listRecommendations(Long scenicAreaId) {
        validateScenicAreaId(scenicAreaId);

        List<RecommendationQuestion> configuredQuestions;
        try {
            configuredQuestions = recommendationQuestionMapper.findTopByScenicAreaId(scenicAreaId);
        } catch (Exception exception) {
            log.error("[推荐问题] 查询失败，使用离线兜底 (scenicAreaId={})", scenicAreaId, exception);
            configuredQuestions = List.of();
        }
        return buildRecommendations(configuredQuestions);
    }

    private List<RecommendationQuestionResult> buildRecommendations(
            List<RecommendationQuestion> configuredQuestions) {
        List<RecommendationQuestion> orderedQuestions = configuredQuestions == null
                ? new ArrayList<>() : new ArrayList<>(configuredQuestions);
        orderedQuestions.removeIf(item -> item == null
                || item.getId() == null
                || !StringUtils.hasText(item.getQuestion()));
        orderedQuestions.sort(CONFIGURED_ORDER);

        List<RecommendationQuestionResult> results = new ArrayList<>(MAX_RECOMMENDATION_COUNT);
        Set<String> seenQuestions = new HashSet<>();
        for (RecommendationQuestion item : orderedQuestions) {
            String question = item.getQuestion().trim();
            if (seenQuestions.add(question)) {
                results.add(new RecommendationQuestionResult(item.getId(), question));
            }
            if (results.size() == MAX_RECOMMENDATION_COUNT) {
                return List.copyOf(results);
            }
        }

        for (RecommendationQuestionResult fallback : OFFLINE_RECOMMENDATIONS) {
            if (results.size() >= MIN_RECOMMENDATION_COUNT) {
                break;
            }
            if (seenQuestions.add(fallback.getQuestion())) {
                results.add(fallback);
            }
        }
        return List.copyOf(results);
    }

    private void validateScenicAreaId(Long scenicAreaId) {
        if (Objects.isNull(scenicAreaId) || scenicAreaId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "景区ID必须为正整数");
        }
    }
}
