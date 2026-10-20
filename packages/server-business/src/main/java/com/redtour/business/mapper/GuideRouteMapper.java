package com.redtour.business.mapper;

import com.redtour.business.entity.GuideRoute;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 导览路线 Mapper（注解式动态 SQL，风格对齐 ScenicAreaMapper）。
 * guide_route 已加 deleted 列：删除走软删（deleted=1），上下架仍用 status（0/1）。
 */
@Mapper
public interface GuideRouteMapper {

    /** 管理端分页查询（带景区/名称/状态筛选）。 */
    @Select("""
            <script>
            SELECT id, scenic_area_id, name, description, spot_ids, duration, difficulty, status, created_at, updated_at
            FROM guide_route
            <where>
                AND deleted = 0
                <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
                <if test="name != null and name != ''">AND name LIKE CONCAT('%', #{name}, '%')</if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<GuideRoute> findPage(@Param("scenicAreaId") Long scenicAreaId,
                              @Param("name") String name,
                              @Param("status") Integer status,
                              @Param("offset") int offset,
                              @Param("pageSize") int pageSize);

    /** 管理端分页计数（与 findPage 同过滤条件）。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM guide_route
            <where>
                AND deleted = 0
                <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
                <if test="name != null and name != ''">AND name LIKE CONCAT('%', #{name}, '%')</if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            </script>
            """)
    long count(@Param("scenicAreaId") Long scenicAreaId,
               @Param("name") String name,
               @Param("status") Integer status);

    /** 按 ID 查询（不过滤状态，供管理端使用）。 */
    @Select("SELECT id, scenic_area_id, name, description, spot_ids, duration, difficulty, status, created_at, updated_at "
            + "FROM guide_route WHERE id = #{id} AND deleted = 0 LIMIT 1")
    GuideRoute findById(@Param("id") Long id);

    /** 新增路线（scenic_area_id 由业务层按权限解析后写入）。 */
    @Insert("INSERT INTO guide_route "
            + "(scenic_area_id, name, description, spot_ids, duration, difficulty, status) "
            + "VALUES (#{scenicAreaId}, #{name}, #{description}, #{spotIds}, #{duration}, #{difficulty}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GuideRoute route);

    /** 编辑路线（局部更新非空字段，scenic_area_id 不可变）。 */
    @Update("""
            <script>
            UPDATE guide_route
            <set>
                <if test="name != null">name = #{name},</if>
                <if test="description != null">description = #{description},</if>
                <if test="spotIds != null">spot_ids = #{spotIds},</if>
                <if test="duration != null">duration = #{duration},</if>
                <if test="difficulty != null">difficulty = #{difficulty},</if>
                <if test="status != null">status = #{status},</if>
                updated_at = CURRENT_TIMESTAMP
            </set>
            WHERE id = #{id}
            </script>
            """)
    int updateById(GuideRoute route);

    /** 上下架状态切换。 */
    @Update("UPDATE guide_route SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 软删除（deleted=1；guide_route 表已加 deleted 列）。 */
    @Update("UPDATE guide_route SET deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /** 游客端：仅上架路线分页（可按景区/名称模糊筛选）。 */
    @Select("""
            <script>
            SELECT id, scenic_area_id, name, description, spot_ids, duration, difficulty, status, created_at, updated_at
            FROM guide_route
            WHERE status = 1 AND deleted = 0
            <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
            <if test="name != null and name != ''">AND name LIKE CONCAT('%', #{name}, '%')</if>
            ORDER BY id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<GuideRoute> findPublicPage(@Param("scenicAreaId") Long scenicAreaId,
                                    @Param("name") String name,
                                    @Param("offset") int offset,
                                    @Param("pageSize") int pageSize);

    /** 游客端：仅上架路线分页计数。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM guide_route
            WHERE status = 1 AND deleted = 0
            <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
            <if test="name != null and name != ''">AND name LIKE CONCAT('%', #{name}, '%')</if>
            </script>
            """)
    long countPublic(@Param("scenicAreaId") Long scenicAreaId, @Param("name") String name);

    /** 游客端：按 ID 查上架路线。 */
    @Select("SELECT id, scenic_area_id, name, description, spot_ids, duration, difficulty, status, created_at, updated_at "
            + "FROM guide_route WHERE id = #{id} AND status = 1 AND deleted = 0 LIMIT 1")
    GuideRoute findPublicById(@Param("id") Long id);
}
