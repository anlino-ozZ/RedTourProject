package com.redtour.business.mapper;

import com.redtour.business.entity.Script;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 剧本 Mapper（注解式动态 SQL，风格对齐 ScenicAreaMapper）。
 * script 表已加 deleted 列：删除走软删（deleted=1），上下架仍用 status（0/1）。
 */
@Mapper
public interface ScriptMapper {

    /** 管理端分页查询（带 scenic_area_id / title 模糊 / status 过滤）。 */
    @Select("""
            <script>
            SELECT id, scenic_area_id, title, description, cover_image, roles, nodes,
                   start_node_id, achievements, status, created_at, updated_at
            FROM script
            <where>
                AND deleted = 0
                <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
                <if test="title != null and title != ''">AND title LIKE CONCAT('%', #{title}, '%')</if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Script> findPage(@Param("scenicAreaId") Long scenicAreaId,
                          @Param("title") String title,
                          @Param("status") Integer status,
                          @Param("offset") int offset,
                          @Param("pageSize") int pageSize);

    /** 管理端分页计数（与 findPage 同过滤条件）。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM script
            <where>
                AND deleted = 0
                <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
                <if test="title != null and title != ''">AND title LIKE CONCAT('%', #{title}, '%')</if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            </script>
            """)
    long count(@Param("scenicAreaId") Long scenicAreaId,
               @Param("title") String title,
               @Param("status") Integer status);

    /** 按 ID 查询。 */
    @Select("SELECT id, scenic_area_id, title, description, cover_image, roles, nodes, "
            + "start_node_id, achievements, status, created_at, updated_at "
            + "FROM script WHERE id = #{id} AND deleted = 0 LIMIT 1")
    Script findById(@Param("id") Long id);

    /** 新增剧本。 */
    @Insert("INSERT INTO script "
            + "(scenic_area_id, title, description, cover_image, roles, nodes, "
            + "start_node_id, achievements, status) "
            + "VALUES (#{scenicAreaId}, #{title}, #{description}, #{coverImage}, #{roles}, #{nodes}, "
            + "#{startNodeId}, #{achievements}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Script script);

    /** 编辑剧本（局部更新非空字段）。 */
    @Update("""
            <script>
            UPDATE script
            <set>
                <if test="title != null">title = #{title},</if>
                <if test="description != null">description = #{description},</if>
                <if test="coverImage != null">cover_image = #{coverImage},</if>
                <if test="roles != null">roles = #{roles},</if>
                <if test="nodes != null">nodes = #{nodes},</if>
                <if test="startNodeId != null">start_node_id = #{startNodeId},</if>
                <if test="achievements != null">achievements = #{achievements},</if>
                <if test="status != null">status = #{status},</if>
                updated_at = CURRENT_TIMESTAMP
            </set>
            WHERE id = #{id}
            </script>
            """)
    int updateById(Script script);

    /** 上下架状态切换。 */
    @Update("UPDATE script SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 软删除（deleted=1；script 表已加 deleted 列）。 */
    @Update("UPDATE script SET deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /** 游客端：仅上架剧本，按景区过滤。 */
    @Select("""
            <script>
            SELECT id, scenic_area_id, title, description, cover_image, roles, nodes,
                   start_node_id, achievements, status, created_at, updated_at
            FROM script
            WHERE status = 1 AND deleted = 0
            <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
            ORDER BY id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Script> findPublicPage(@Param("scenicAreaId") Long scenicAreaId,
                                @Param("offset") int offset,
                                @Param("pageSize") int pageSize);

    /** 游客端：上架剧本详情。 */
    @Select("SELECT id, scenic_area_id, title, description, cover_image, roles, nodes, "
            + "start_node_id, achievements, status, created_at, updated_at "
            + "FROM script WHERE id = #{id} AND status = 1 AND deleted = 0 LIMIT 1")
    Script findPublicById(@Param("id") Long id);
}
