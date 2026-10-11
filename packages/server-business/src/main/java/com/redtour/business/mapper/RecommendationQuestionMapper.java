package com.redtour.business.mapper;

import com.redtour.business.entity.RecommendationQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 推荐问题 Mapper。
 */
@Mapper
public interface RecommendationQuestionMapper {

    /** 查询指定景区权重最高的十条推荐问题。 */
    @Select("""
            SELECT id, scenic_area_id, question, sort_weight
            FROM recommendation_question
            WHERE scenic_area_id = #{scenicAreaId}
            ORDER BY sort_weight DESC, id ASC
            LIMIT 10
            """)
    List<RecommendationQuestion> findTopByScenicAreaId(@Param("scenicAreaId") Long scenicAreaId);
}
