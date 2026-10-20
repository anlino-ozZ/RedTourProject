package com.redtour.business.mapper;

import com.redtour.business.entity.Artifact;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文物 Mapper（注解式动态 SQL）。
 * artifact 已加 deleted 列：删除走软删（deleted=1），上下架仍用 status（0/1）。
 * 实际字段以 schema.sql / Artifact 实体为准：era, intro, images, location, category, wiki_ref。
 */
@Mapper
public interface ArtifactMapper {

    /** 管理端分页查询（带景区/名称/状态筛选）。 */
    @Select("""
            <script>
            SELECT id, scenic_area_id, name, era, intro, images, location, category, wiki_ref, status, created_at, updated_at
            FROM artifact
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
    List<Artifact> findPage(@Param("scenicAreaId") Long scenicAreaId,
                            @Param("name") String name,
                            @Param("status") Integer status,
                            @Param("offset") int offset,
                            @Param("pageSize") int pageSize);

    /** 管理端分页计数（与 findPage 同过滤条件）。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM artifact
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
    @Select("SELECT id, scenic_area_id, name, era, intro, images, location, category, wiki_ref, status, created_at, updated_at "
            + "FROM artifact WHERE id = #{id} AND deleted = 0 LIMIT 1")
    Artifact findById(@Param("id") Long id);

    /** 新增文物（scenic_area_id 由业务层按权限解析后写入）。 */
    @Insert("INSERT INTO artifact "
            + "(scenic_area_id, name, era, intro, images, location, category, wiki_ref, status) "
            + "VALUES (#{scenicAreaId}, #{name}, #{era}, #{intro}, #{images}, #{location}, #{category}, #{wikiRef}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Artifact artifact);

    /** 编辑文物（局部更新非空字段，scenic_area_id 不可变）。 */
    @Update("""
            <script>
            UPDATE artifact
            <set>
                <if test="name != null">name = #{name},</if>
                <if test="era != null">era = #{era},</if>
                <if test="intro != null">intro = #{intro},</if>
                <if test="images != null">images = #{images},</if>
                <if test="location != null">location = #{location},</if>
                <if test="category != null">category = #{category},</if>
                <if test="wikiRef != null">wiki_ref = #{wikiRef},</if>
                <if test="status != null">status = #{status},</if>
                updated_at = CURRENT_TIMESTAMP
            </set>
            WHERE id = #{id}
            </script>
            """)
    int updateById(Artifact artifact);

    /** 上下架状态切换。 */
    @Update("UPDATE artifact SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 软删除（deleted=1；artifact 表已加 deleted 列）。 */
    @Update("UPDATE artifact SET deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /** 游客端：仅上架文物分页（可按景区/名称模糊筛选）。 */
    @Select("""
            <script>
            SELECT id, scenic_area_id, name, era, intro, images, location, category, wiki_ref, status, created_at, updated_at
            FROM artifact
            WHERE status = 1 AND deleted = 0
            <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
            <if test="name != null and name != ''">AND name LIKE CONCAT('%', #{name}, '%')</if>
            ORDER BY id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Artifact> findPublicPage(@Param("scenicAreaId") Long scenicAreaId,
                                  @Param("name") String name,
                                  @Param("offset") int offset,
                                  @Param("pageSize") int pageSize);

    /** 游客端：仅上架文物分页计数。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM artifact
            WHERE status = 1 AND deleted = 0
            <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
            <if test="name != null and name != ''">AND name LIKE CONCAT('%', #{name}, '%')</if>
            </script>
            """)
    long countPublic(@Param("scenicAreaId") Long scenicAreaId, @Param("name") String name);

    /** 游客端：按 ID 查上架文物。 */
    @Select("SELECT id, scenic_area_id, name, era, intro, images, location, category, wiki_ref, status, created_at, updated_at "
            + "FROM artifact WHERE id = #{id} AND status = 1 AND deleted = 0 LIMIT 1")
    Artifact findPublicById(@Param("id") Long id);
}
