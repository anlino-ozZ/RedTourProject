package com.redtour.business.mapper;

import com.redtour.business.entity.Spot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 景点 Mapper（注解式动态 SQL，风格对齐 ScenicAreaMapper）。
 */
@Mapper
public interface SpotMapper {

    /** 管理端分页查询（已过滤软删除）。 */
    @Select("""
            <script>
            SELECT id, scenic_area_id, name, intro, images, longitude, latitude, audio_url,
                   wiki_ref, sort_order, status, deleted, created_at, updated_at
            FROM spot
            WHERE deleted = 0
            <if test="scenicAreaId != null">
                AND scenic_area_id = #{scenicAreaId}
            </if>
            <if test="name != null and name != ''">
                AND name LIKE CONCAT('%', #{name}, '%')
            </if>
            <if test="status != null">
                AND status = #{status}
            </if>
            ORDER BY sort_order ASC, id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Spot> findPage(@Param("scenicAreaId") Long scenicAreaId,
                        @Param("name") String name,
                        @Param("status") Integer status,
                        @Param("offset") int offset,
                        @Param("pageSize") int pageSize);

    /** 管理端分页计数（与 findPage 同过滤条件）。 */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM spot
            WHERE deleted = 0
            <if test="scenicAreaId != null">
                AND scenic_area_id = #{scenicAreaId}
            </if>
            <if test="name != null and name != ''">
                AND name LIKE CONCAT('%', #{name}, '%')
            </if>
            <if test="status != null">
                AND status = #{status}
            </if>
            </script>
            """)
    long count(@Param("scenicAreaId") Long scenicAreaId,
               @Param("name") String name,
               @Param("status") Integer status);

    /** 按 ID 查询（已过滤软删除）。 */
    @Select("SELECT id, scenic_area_id, name, intro, images, longitude, latitude, audio_url, "
            + "wiki_ref, sort_order, status, deleted, created_at, updated_at "
            + "FROM spot WHERE id = #{id} AND deleted = 0 LIMIT 1")
    Spot findById(@Param("id") Long id);

    /** 按 ID 查询归属景区（关联校验用，如路线 spotIds 是否同属一个景区）。 */
    @Select("SELECT scenic_area_id FROM spot WHERE id = #{id} AND deleted = 0 LIMIT 1")
    Long selectScenicAreaIdById(@Param("id") Long id);

    /** 同景区内名称唯一性校验（新增时）。 */
    @Select("SELECT COUNT(1) FROM spot WHERE scenic_area_id = #{scenicAreaId} AND name = #{name} AND deleted = 0 LIMIT 1")
    long countByNameInScenicArea(@Param("scenicAreaId") Long scenicAreaId, @Param("name") String name);

    /** 同景区内名称唯一性校验（编辑时排除自身）。 */
    @Select("SELECT COUNT(1) FROM spot WHERE scenic_area_id = #{scenicAreaId} AND name = #{name} AND id != #{id} AND deleted = 0 LIMIT 1")
    long countByNameExcludeId(@Param("scenicAreaId") Long scenicAreaId, @Param("name") String name, @Param("id") Long id);

    /** 新增景点。 */
    @Insert("INSERT INTO spot "
            + "(scenic_area_id, name, intro, images, longitude, latitude, audio_url, "
            + "wiki_ref, sort_order, status, deleted) "
            + "VALUES (#{scenicAreaId}, #{name}, #{intro}, #{images}, #{longitude}, #{latitude}, #{audioUrl}, "
            + "#{wikiRef}, #{sortOrder}, #{status}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Spot spot);

    /** 编辑景点（局部更新非空字段，scenic_area_id 不可变）。 */
    @Update("""
            <script>
            UPDATE spot
            <set>
                <if test="name != null">name = #{name},</if>
                <if test="intro != null">intro = #{intro},</if>
                <if test="images != null">images = #{images},</if>
                <if test="longitude != null">longitude = #{longitude},</if>
                <if test="latitude != null">latitude = #{latitude},</if>
                <if test="audioUrl != null">audio_url = #{audioUrl},</if>
                <if test="wikiRef != null">wiki_ref = #{wikiRef},</if>
                <if test="sortOrder != null">sort_order = #{sortOrder},</if>
                <if test="status != null">status = #{status},</if>
                updated_at = CURRENT_TIMESTAMP
            </set>
            WHERE id = #{id} AND deleted = 0
            </script>
            """)
    int updateById(Spot spot);

    /** 上下架状态切换。 */
    @Update("UPDATE spot SET status = #{status}, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 软删除。 */
    @Update("UPDATE spot SET deleted = 1, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND deleted = 0")
    int softDelete(@Param("id") Long id);

    /** 游客端：仅上架景点分页（deleted=0 AND status=1）。 */
    @Select("""
            <script>
            SELECT id, scenic_area_id, name, intro, images, longitude, latitude, audio_url,
                   wiki_ref, sort_order, status, created_at
            FROM spot
            WHERE deleted = 0 AND status = 1
            <if test="scenicAreaId != null">
                AND scenic_area_id = #{scenicAreaId}
            </if>
            <if test="name != null and name != ''">
                AND name LIKE CONCAT('%', #{name}, '%')
            </if>
            ORDER BY sort_order ASC, id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Spot> findPublicPage(@Param("scenicAreaId") Long scenicAreaId,
                              @Param("name") String name,
                              @Param("offset") int offset,
                              @Param("pageSize") int pageSize);

    /** 游客端：仅上架景点分页计数。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM spot
            WHERE deleted = 0 AND status = 1
            <if test="scenicAreaId != null">
                AND scenic_area_id = #{scenicAreaId}
            </if>
            <if test="name != null and name != ''">
                AND name LIKE CONCAT('%', #{name}, '%')
            </if>
            </script>
            """)
    long countPublic(@Param("scenicAreaId") Long scenicAreaId, @Param("name") String name);

    /** 游客端：按 ID 查上架景点。 */
    @Select("SELECT id, scenic_area_id, name, intro, images, longitude, latitude, audio_url, "
            + "wiki_ref, sort_order, status, created_at "
            + "FROM spot WHERE id = #{id} AND deleted = 0 AND status = 1 LIMIT 1")
    Spot findPublicById(@Param("id") Long id);
}
