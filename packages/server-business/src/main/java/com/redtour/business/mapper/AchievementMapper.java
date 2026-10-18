package com.redtour.business.mapper;

import com.redtour.business.entity.Achievement;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 成就定义 Mapper（注解式动态 SQL）。
 *
 * 按 scenic_area_id 做景区隔离（与景区/景点/文物一致）；成就无 deleted 列，下线与删除统一用 status=0。
 * 字段以 schema.sql / Achievement 实体为准：title, icon, description, trigger_type, trigger_condition, status, scenic_area_id。
 */
@Mapper
public interface AchievementMapper {

    /** 管理端分页查询（可按触发类型/标题筛选）。 */
    @Select("""
            <script>
            SELECT id, title, icon, description, trigger_type, trigger_condition, status, scenic_area_id, created_at
            FROM achievement
            <where>
                <if test="triggerType != null and triggerType != ''">AND trigger_type = #{triggerType}</if>
                <if test="name != null and name != ''">AND title LIKE CONCAT('%', #{name}, '%')</if>
                <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Achievement> findPage(@Param("triggerType") String triggerType,
                               @Param("name") String name,
                               @Param("scenicAreaId") Long scenicAreaId,
                               @Param("offset") int offset,
                               @Param("pageSize") int pageSize);

    /** 管理端分页计数（与 findPage 同过滤条件）。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM achievement
            <where>
                <if test="triggerType != null and triggerType != ''">AND trigger_type = #{triggerType}</if>
                <if test="name != null and name != ''">AND title LIKE CONCAT('%', #{name}, '%')</if>
                <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
            </where>
            </script>
            """)
    long count(@Param("triggerType") String triggerType, @Param("name") String name, @Param("scenicAreaId") Long scenicAreaId);

    /** 按 ID 查询。 */
    @Select("SELECT id, title, icon, description, trigger_type, trigger_condition, status, scenic_area_id, created_at "
            + "FROM achievement WHERE id = #{id} LIMIT 1")
    Achievement findById(@Param("id") Long id);

    /** 新增成就。 */
    @Insert("INSERT INTO achievement (title, icon, description, trigger_type, trigger_condition, status, scenic_area_id) "
            + "VALUES (#{title}, #{icon}, #{description}, #{triggerType}, #{triggerCondition}, #{status}, #{scenicAreaId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Achievement achievement);

    /** 编辑成就（局部更新非空字段）。 */
    @Update("""
            <script>
            UPDATE achievement
            <set>
                <if test="title != null">title = #{title},</if>
                <if test="icon != null">icon = #{icon},</if>
                <if test="description != null">description = #{description},</if>
                <if test="triggerType != null">trigger_type = #{triggerType},</if>
                <if test="triggerCondition != null">trigger_condition = #{triggerCondition},</if>
                <if test="status != null">status = #{status},</if>
                <if test="scenicAreaId != null">scenic_area_id = #{scenicAreaId},</if>
            </set>
            WHERE id = #{id}
            </script>
            """)
    int updateById(Achievement achievement);

    /** 下线（无 deleted 列，用 status=0 表达删除）。 */
    @Update("UPDATE achievement SET status = 0 WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /** 删除前校验是否被用户引用（已被解锁则不可删，返回 >0 表示已引用）。 */
    @Select("SELECT COUNT(1) FROM user_achievement WHERE achievement_id = #{id}")
    long countUserAchievement(@Param("id") Long id);

    /** 加载上架成就中指定触发类型的候选（用于解锁判定）。 */
    @Select("<script>"
            + "SELECT id, title, icon, description, trigger_type, trigger_condition, status, scenic_area_id, created_at "
            + "FROM achievement "
            + "WHERE status = 1 "
            + "<if test='triggerTypes != null and triggerTypes.size() > 0'>"
            + "  AND trigger_type IN "
            + "  <foreach item='t' collection='triggerTypes' open='(' separator=',' close=')'>#{t}</foreach>"
            + "</if>"
            + "ORDER BY id"
            + "</script>")
    List<Achievement> findActiveByTriggerTypes(@Param("triggerTypes") List<String> triggerTypes);
}
