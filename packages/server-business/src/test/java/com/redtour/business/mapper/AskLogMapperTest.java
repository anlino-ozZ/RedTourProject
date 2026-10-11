package com.redtour.business.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AskLogMapperTest {

    @Test
    void shouldAggregateRecentValidQuestionsWithIndexFriendlyFilters()
            throws NoSuchMethodException {
        Method method = AskLogMapper.class.getMethod(
                "findPopularQuestions", Long.class, LocalDateTime.class, int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase();

        assertTrue(sql.contains("WHERE SCENIC_AREA_ID = #{SCENICAREAID}"));
        assertTrue(sql.contains("AND CREATED_AT >= #{SINCE}"));
        assertTrue(sql.contains("GROUP BY TRIM(QUESTION)"));
        assertTrue(sql.contains("ORDER BY ASK_COUNT DESC, LATEST_ASKED_AT DESC, ID ASC"));
        assertTrue(sql.endsWith("LIMIT #{LIMIT}"));
        assertTrue(sql.contains("CHAR_LENGTH(TRIM(ANSWER)) > 0"));
        assertTrue(sql.contains("TRIM(ANSWER) NOT IN"));
    }
}
