package com.redtour.business.service.impl;

import com.redtour.business.common.ResultCode;
import com.redtour.business.dto.RecommendationQuestionResult;
import com.redtour.business.entity.PopularQuestion;
import com.redtour.business.entity.RecommendationQuestion;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.AskLogMapper;
import com.redtour.business.mapper.RecommendationQuestionMapper;
import com.redtour.business.service.RecommendationQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 推荐问题服务：优先使用近期热门问题，并以景区配置和离线问题保证基本可用性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationQuestionServiceImpl implements RecommendationQuestionService {

    private static final int MIN_RECOMMENDATION_COUNT = 5;
    private static final int MAX_RECOMMENDATION_COUNT = 10;
    private static final int POPULAR_CANDIDATE_LIMIT = 30;
    private static final int POPULAR_LOOKBACK_DAYS = 30;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern SIMILARITY_NOISE = Pattern.compile("[\\s\\p{P}\\p{S}]+");
    private static final Set<String> MEANINGLESS_QUESTIONS = Set.of(
            "test", "测试", "你好", "您好", "在吗", "不知道", "无", "没有",
            "哈哈", "哈哈哈", "呵呵", "123", "111", "666"
    );
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

    private final AskLogMapper askLogMapper;
    private final RecommendationQuestionMapper recommendationQuestionMapper;

    @Override
    public List<RecommendationQuestionResult> listRecommendations(Long scenicAreaId) {
        validateScenicAreaId(scenicAreaId);

        List<PopularQuestion> popularQuestions = loadPopularQuestions(scenicAreaId);
        List<RecommendationQuestion> configuredQuestions = loadConfiguredQuestions(scenicAreaId);
        return buildRecommendations(popularQuestions, configuredQuestions);
    }

    private List<PopularQuestion> loadPopularQuestions(Long scenicAreaId) {
        LocalDateTime since = LocalDateTime.now().minusDays(POPULAR_LOOKBACK_DAYS);
        try {
            List<PopularQuestion> questions = askLogMapper.findPopularQuestions(
                    scenicAreaId, since, POPULAR_CANDIDATE_LIMIT);
            return questions == null ? List.of() : questions;
        } catch (Exception exception) {
            log.error("[热门问题] 查询失败，继续使用静态推荐 (scenicAreaId={})",
                    scenicAreaId, exception);
            return List.of();
        }
    }

    private List<RecommendationQuestion> loadConfiguredQuestions(Long scenicAreaId) {
        try {
            List<RecommendationQuestion> questions =
                    recommendationQuestionMapper.findTopByScenicAreaId(scenicAreaId);
            return questions == null ? List.of() : questions;
        } catch (Exception exception) {
            log.error("[推荐问题] 查询失败，继续使用离线兜底 (scenicAreaId={})",
                    scenicAreaId, exception);
            return List.of();
        }
    }

    private List<RecommendationQuestionResult> buildRecommendations(
            List<PopularQuestion> popularQuestions,
            List<RecommendationQuestion> configuredQuestions) {
        List<RecommendationQuestionResult> results = new ArrayList<>(MAX_RECOMMENDATION_COUNT);
        Set<String> seenQuestions = new HashSet<>();
        addPopularQuestions(results, seenQuestions, popularQuestions);

        List<RecommendationQuestion> orderedQuestions = configuredQuestions == null
                ? new ArrayList<>() : new ArrayList<>(configuredQuestions);
        orderedQuestions.removeIf(item -> item == null
                || item.getId() == null
                || !StringUtils.hasText(item.getQuestion()));
        orderedQuestions.sort(CONFIGURED_ORDER);

        for (RecommendationQuestion item : orderedQuestions) {
            addRecommendation(results, seenQuestions, item.getId(), item.getQuestion());
            if (results.size() == MAX_RECOMMENDATION_COUNT) {
                return List.copyOf(results);
            }
        }

        for (RecommendationQuestionResult fallback : OFFLINE_RECOMMENDATIONS) {
            if (results.size() >= MIN_RECOMMENDATION_COUNT) {
                break;
            }
            addRecommendation(results, seenQuestions, fallback.getId(), fallback.getQuestion());
        }
        return List.copyOf(results);
    }

    private void addPopularQuestions(
            List<RecommendationQuestionResult> results,
            Set<String> seenQuestions,
            List<PopularQuestion> popularQuestions) {
        if (popularQuestions == null) {
            return;
        }
        for (PopularQuestion item : popularQuestions) {
            if (item == null || item.getId() == null || isMeaninglessQuestion(item.getQuestion())) {
                continue;
            }
            addRecommendation(results, seenQuestions, item.getId(), item.getQuestion());
            if (results.size() == MAX_RECOMMENDATION_COUNT) {
                return;
            }
        }
    }

    private void addRecommendation(
            List<RecommendationQuestionResult> results,
            Set<String> seenQuestions,
            Long id,
            String rawQuestion) {
        String question = normalizeQuestion(rawQuestion);
        if (!StringUtils.hasText(question)) {
            return;
        }
        String similarityKey = similarityKey(question);
        if (seenQuestions.add(similarityKey)) {
            results.add(new RecommendationQuestionResult(id, question));
        }
    }

    private boolean isMeaninglessQuestion(String rawQuestion) {
        String question = normalizeQuestion(rawQuestion);
        if (!StringUtils.hasText(question) || question.codePointCount(0, question.length()) < 2) {
            return true;
        }
        String similarityKey = similarityKey(question);
        if (!StringUtils.hasText(similarityKey)
                || MEANINGLESS_QUESTIONS.contains(similarityKey)) {
            return true;
        }
        int[] codePoints = similarityKey.codePoints().toArray();
        boolean repeatedSingleCharacter = codePoints.length >= 2
                && Arrays.stream(codePoints).allMatch(value -> value == codePoints[0]);
        return repeatedSingleCharacter
                || question.codePoints().noneMatch(Character::isLetterOrDigit);
    }

    private String normalizeQuestion(String rawQuestion) {
        return StringUtils.hasText(rawQuestion)
                ? WHITESPACE.matcher(rawQuestion.trim()).replaceAll(" ") : "";
    }

    private String similarityKey(String question) {
        return SIMILARITY_NOISE.matcher(question)
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
    }

    private void validateScenicAreaId(Long scenicAreaId) {
        if (Objects.isNull(scenicAreaId) || scenicAreaId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "景区ID必须为正整数");
        }
    }
}
