package com.redtour.business.mapper;

import com.redtour.business.dto.HotSpotResult;
import com.redtour.business.dto.ProductClickResult;
import com.redtour.business.entity.PopularQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 运营分析 Mapper（注解式 SQL）。
 * 所有查询均带 scenicAreaId 隔离（super 可传 null 查全量），热门问题来自 popular_question 表。
 */
@Mapper
public interface AnalyticsMapper {

    /**
     * 游览记录按天聚合计数。
     * 返回每行 {visit_date: 'yyyy-MM-dd', visit_count: Long}。
     */
    @Select("""
            <script>
            SELECT DATE(created_at) AS visit_date, COUNT(1) AS visit_count
            FROM visit_record
            WHERE 1 = 1
            <if test="scenicAreaId != null">
                AND scenic_area_id = #{scenicAreaId}
            </if>
            <if test="startDate != null">
                AND DATE(created_at) &gt;= #{startDate}
            </if>
            <if test="endDate != null">
                AND DATE(created_at) &lt;= #{endDate}
            </if>
            GROUP BY DATE(created_at)
            ORDER BY visit_date
            </script>
            """)
    List<Map<String, Object>> countVisitsByDay(@Param("scenicAreaId") Long scenicAreaId,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate);

    /**
     * 打卡按景点聚合（join spot 取名称），按景区过滤。
     * 别名 spot_id / spot_name / visit_count 经驼峰映射为 HotSpotResult 字段。
     */
    @Select("""
            <script>
            SELECT c.spot_id AS spot_id, s.name AS spot_name, COUNT(1) AS visit_count
            FROM checkin c
            JOIN spot s ON s.id = c.spot_id
            WHERE 1 = 1
            <if test="scenicAreaId != null">
                AND c.scenic_area_id = #{scenicAreaId}
            </if>
            GROUP BY c.spot_id, s.name
            ORDER BY visit_count DESC, c.spot_id
            </script>
            """)
    List<HotSpotResult> countBySpot(@Param("scenicAreaId") Long scenicAreaId);

    /**
     * 热门问题：recommendation_question 表按 sort_weight（热度权重）倒序取前 limit。
     * sort_weight 别名 ask_count 映射到 PopularQuestion.askCount。
     */
    @Select("""
            SELECT id, question, sort_weight AS ask_count
            FROM recommendation_question
            ORDER BY sort_weight DESC, id
            LIMIT #{limit}
            """)
    List<PopularQuestion> findTopQuestions(@Param("limit") int limit);

    /**
     * 产品按 click_count 倒序（带景区隔离）。
     * 别名 click_count 经驼峰映射为 ProductClickResult.clickCount。
     */
    @Select("""
            <script>
            SELECT id, name, click_count
            FROM product
            <if test="scenicAreaId != null">
                WHERE scenic_area_id = #{scenicAreaId}
            </if>
            ORDER BY click_count DESC, id
            </script>
            """)
    List<ProductClickResult> findProductsByClicks(@Param("scenicAreaId") Long scenicAreaId);
}
