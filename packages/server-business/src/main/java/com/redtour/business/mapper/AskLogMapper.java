package com.redtour.business.mapper;

import com.redtour.business.entity.AskLog;
import com.redtour.business.entity.PopularQuestion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 问答记录 Mapper。
 */
@Mapper
public interface AskLogMapper {

    /** 写入一次问答记录。 */
    @Insert("INSERT INTO ask_log "
            + "(scenic_area_id, question, answer, sources, duration_ms, device_id) "
            + "VALUES (#{scenicAreaId}, #{question}, #{answer}, #{sources}, #{durationMs}, #{deviceId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AskLog askLog);

    /** 查询指定景区最近一段时间的热门有效问题。 */
    @Select("""
            SELECT MIN(id) AS id,
                   TRIM(question) AS question,
                   COUNT(*) AS ask_count,
                   MAX(created_at) AS latest_asked_at
            FROM ask_log
            WHERE scenic_area_id = #{scenicAreaId}
              AND created_at >= #{since}
              AND question IS NOT NULL
              AND CHAR_LENGTH(TRIM(question)) BETWEEN 2 AND 500
              AND answer IS NOT NULL
              AND CHAR_LENGTH(TRIM(answer)) > 0
              AND TRIM(answer) NOT IN (
                  '当前本地问答模型暂不可用，请稍后重试。',
                  '知识库暂无足够依据，暂时无法可靠回答这个问题。',
                  'AI 引擎返回空响应，请稍后再试。',
                  'AI 引擎暂不可用，请稍后再试。',
                  'AI 引擎未返回有效回答，请稍后再试。',
                  '问答服务异常，请联系管理员。',
                  '问答服务遇到未知错误。'
              )
            GROUP BY TRIM(question)
            ORDER BY ask_count DESC, latest_asked_at DESC, id ASC
            LIMIT #{limit}
            """)
    List<PopularQuestion> findPopularQuestions(
            @Param("scenicAreaId") Long scenicAreaId,
            @Param("since") LocalDateTime since,
            @Param("limit") int limit);
}
