package com.redtour.business.mapper;

import com.redtour.business.dto.AchievementUnlockerResult;
import com.redtour.business.dto.UserAchievementResult;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户成就关联 Mapper（注解式 SQL）。
 */
@Mapper
public interface UserAchievementMapper {

    /** 当前用户的成就列表（join achievement 补充标题与描述）。 */
    @Select("""
            SELECT ua.achievement_id, a.title AS name, a.description, ua.unlocked_at,
                   ua.unlock_source, ua.scenic_area_id
            FROM user_achievement ua
            LEFT JOIN achievement a ON a.id = ua.achievement_id
            WHERE ua.user_id = #{userId}
            ORDER BY ua.unlocked_at DESC
            """)
    @Results({
            @Result(column = "achievement_id", property = "achievementId"),
            @Result(column = "name", property = "name"),
            @Result(column = "description", property = "description"),
            @Result(column = "unlocked_at", property = "unlockedAt"),
            @Result(column = "unlock_source", property = "unlockSource"),
            @Result(column = "scenic_area_id", property = "scenicAreaId")
    })
    List<UserAchievementResult> findByUserId(@Param("userId") Long userId);

    /** 当前用户已解锁成就数量（用于个人中心统计）。 */
    @Select("SELECT COUNT(1) FROM user_achievement WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    /** 用户是否已解锁某成就（防重复写入）。 */
    @Select("SELECT COUNT(1) FROM user_achievement WHERE user_id = #{userId} AND achievement_id = #{achievementId}")
    long countByUserAndAchievement(@Param("userId") Long userId, @Param("achievementId") Long achievementId);

    /**
     * 用户在指定景点集合中【已完成】的景点数（visit 类判定用）。
     * 景点完成 = 该景点下全部打卡点均已命中（已命中去重打卡点数 ≥ 该景点打卡点总数）。
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM ("
            + "  SELECT c.spot_id FROM checkin c"
            + "  WHERE c.user_id = #{userId} AND c.checkpoint_id IS NOT NULL"
            + "    AND c.spot_id IN "
            + "    <foreach item='s' collection='spotIds' open='(' separator=',' close=')'>#{s}</foreach>"
            + "  GROUP BY c.spot_id"
            + "  HAVING COUNT(DISTINCT c.checkpoint_id) >= (SELECT COUNT(1) FROM checkpoint cp WHERE cp.spot_id = c.spot_id AND cp.deleted = 0)"
            + ") t"
            + "</script>")
    long countCompletedSpots(@Param("userId") Long userId, @Param("spotIds") List<Long> spotIds);

    /**
     * 用户在某景区【已完成】的景点数（checkin 类判定用）。
     * 景点完成 = 该景点下全部打卡点均已命中。
     */
    @Select("SELECT COUNT(*) FROM ("
            + "  SELECT c.spot_id FROM checkin c"
            + "  JOIN spot s ON s.id = c.spot_id"
            + "  WHERE c.user_id = #{userId} AND s.scenic_area_id = #{scenicAreaId} AND c.checkpoint_id IS NOT NULL"
            + "  GROUP BY c.spot_id"
            + "  HAVING COUNT(DISTINCT c.checkpoint_id) >= (SELECT COUNT(1) FROM checkpoint cp WHERE cp.spot_id = c.spot_id AND cp.deleted = 0)"
            + ") t")
    long countCompletedSpotsInArea(@Param("userId") Long userId, @Param("scenicAreaId") Long scenicAreaId);

    /** 用户是否已完成某剧本（script 类判定用）。 */
    @Select("SELECT COUNT(1) FROM script_progress WHERE user_id = #{userId} AND script_id = #{scriptId} AND completed = 1")
    long countCompletedScript(@Param("userId") Long userId, @Param("scriptId") Long scriptId);

    /** 某成就被多少用户解锁；scenicAreaId 非空时只统计该景区（普管数据隔离）。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM user_achievement
            WHERE achievement_id = #{achievementId}
            <if test="scenicAreaId != null">
                AND scenic_area_id = #{scenicAreaId}
            </if>
            </script>
            """)
    long countByAchievementId(@Param("achievementId") Long achievementId,
                              @Param("scenicAreaId") Long scenicAreaId);

    /** 写入用户成就解锁记录（uk_user_achievement 幂等：已存在则跳过）。 */
    @Insert("INSERT INTO user_achievement (user_id, achievement_id, unlocked_at, unlock_source, scenic_area_id) "
            + "VALUES (#{userId}, #{achievementId}, NOW(), #{unlockSource}, #{scenicAreaId}) "
            + "ON DUPLICATE KEY UPDATE id = id")
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("achievementId") Long achievementId,
                       @Param("unlockSource") String unlockSource,
                       @Param("scenicAreaId") Long scenicAreaId);

    /** 某成就的解锁者列表（join sys_user 取昵称）。scenicAreaId 为 null 时不过滤（超管全量）。 */
    @Select("""
            <script>
            SELECT ua.user_id, u.nickname, ua.unlocked_at, ua.unlock_source, ua.scenic_area_id
            FROM user_achievement ua
            LEFT JOIN sys_user u ON u.id = ua.user_id
            WHERE ua.achievement_id = #{achievementId}
            <if test="scenicAreaId != null">AND ua.scenic_area_id = #{scenicAreaId}</if>
            ORDER BY ua.unlocked_at DESC
            </script>
            """)
    @Results({
            @Result(column = "user_id", property = "userId"),
            @Result(column = "nickname", property = "nickname"),
            @Result(column = "unlocked_at", property = "unlockedAt"),
            @Result(column = "unlock_source", property = "unlockSource"),
            @Result(column = "scenic_area_id", property = "scenicAreaId")
    })
    List<AchievementUnlockerResult> listUnlockers(@Param("achievementId") Long achievementId,
                                                 @Param("scenicAreaId") Long scenicAreaId);
}
