package com.redtour.business.mapper;

import com.redtour.business.entity.ScenicArea;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 景区 Mapper（注解式动态 SQL，风格对齐 WikiEntryMapper）。
 */
@Mapper
public interface ScenicAreaMapper {

    /** 管理端分页查询（已过滤软删除）。scenicAreaId 非空时只返回该景区（普管数据隔离）。 */
    @Select("""
            <script>
            SELECT id, name, intro, longitude, latitude, address, open_hours, cover_image,
                   status, region, phone, sort_order, deleted, created_at, updated_at
            FROM scenic_area
            WHERE deleted = 0
            <if test="scenicAreaId != null">
                AND id = #{scenicAreaId}
            </if>
            <if test="name != null and name != ''">
                AND name LIKE CONCAT('%', #{name}, '%')
            </if>
            <if test="status != null">
                AND status = #{status}
            </if>
            <if test="region != null and region != ''">
                AND region LIKE CONCAT('%', #{region}, '%')
            </if>
            ORDER BY sort_order DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<ScenicArea> findPage(@Param("scenicAreaId") Long scenicAreaId,
                              @Param("name") String name,
                              @Param("status") Integer status,
                              @Param("region") String region,
                              @Param("offset") int offset,
                              @Param("pageSize") int pageSize);

    /** 管理端分页计数（与 findPage 同过滤条件）。 */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM scenic_area
            WHERE deleted = 0
            <if test="scenicAreaId != null">
                AND id = #{scenicAreaId}
            </if>
            <if test="name != null and name != ''">
                AND name LIKE CONCAT('%', #{name}, '%')
            </if>
            <if test="status != null">
                AND status = #{status}
            </if>
            <if test="region != null and region != ''">
                AND region LIKE CONCAT('%', #{region}, '%')
            </if>
            </script>
            """)
    long count(@Param("scenicAreaId") Long scenicAreaId,
               @Param("name") String name,
               @Param("status") Integer status,
               @Param("region") String region);

    /** 按 ID 查询（已过滤软删除）。 */
    @Select("SELECT id, name, intro, longitude, latitude, address, open_hours, cover_image, "
            + "status, region, phone, sort_order, deleted, created_at, updated_at "
            + "FROM scenic_area WHERE id = #{id} AND deleted = 0 LIMIT 1")
    ScenicArea findById(@Param("id") Long id);

    /** 名称唯一性校验（新增时）。 */
    @Select("SELECT COUNT(1) FROM scenic_area WHERE name = #{name} AND deleted = 0 LIMIT 1")
    long countByName(@Param("name") String name);

    /** 名称唯一性校验（编辑时排除自身）。 */
    @Select("SELECT COUNT(1) FROM scenic_area WHERE name = #{name} AND id != #{id} AND deleted = 0 LIMIT 1")
    long countByNameExcludeId(@Param("name") String name, @Param("id") Long id);

    /** 新增景区（软删除列默认 0，由实体写入）。 */
    @Insert("INSERT INTO scenic_area "
            + "(name, intro, longitude, latitude, address, open_hours, cover_image, "
            + "status, region, phone, sort_order, deleted) "
            + "VALUES (#{name}, #{intro}, #{longitude}, #{latitude}, #{address}, #{openHours}, #{coverImage}, "
            + "#{status}, #{region}, #{phone}, #{sortOrder}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScenicArea area);

    /** 编辑景区（局部更新非空字段）。 */
    @Update("""
            <script>
            UPDATE scenic_area
            <set>
                <if test="name != null">name = #{name},</if>
                <if test="intro != null">intro = #{intro},</if>
                <if test="longitude != null">longitude = #{longitude},</if>
                <if test="latitude != null">latitude = #{latitude},</if>
                <if test="address != null">address = #{address},</if>
                <if test="openHours != null">open_hours = #{openHours},</if>
                <if test="coverImage != null">cover_image = #{coverImage},</if>
                <if test="region != null">region = #{region},</if>
                <if test="phone != null">phone = #{phone},</if>
                <if test="sortOrder != null">sort_order = #{sortOrder},</if>
                <if test="status != null">status = #{status},</if>
                updated_at = CURRENT_TIMESTAMP
            </set>
            WHERE id = #{id} AND deleted = 0
            </script>
            """)
    int updateById(ScenicArea area);

    /** 上下架状态切换。 */
    @Update("UPDATE scenic_area SET status = #{status}, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 软删除。 */
    @Update("UPDATE scenic_area SET deleted = 1, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND deleted = 0")
    int softDelete(@Param("id") Long id);

    /** 游客端：仅上架景区分页。 */
    @Select("""
            <script>
            SELECT id, name, intro, longitude, latitude, address, open_hours, cover_image,
                   status, region, phone, sort_order, created_at
            FROM scenic_area
            WHERE deleted = 0 AND status = 1
            <if test="name != null and name != ''">
                AND name LIKE CONCAT('%', #{name}, '%')
            </if>
            ORDER BY sort_order DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<ScenicArea> findPublicPage(@Param("name") String name,
                                    @Param("offset") int offset,
                                    @Param("pageSize") int pageSize);

    /** 游客端：仅上架景区分页计数。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM scenic_area
            WHERE deleted = 0 AND status = 1
            <if test="name != null and name != ''">
                AND name LIKE CONCAT('%', #{name}, '%')
            </if>
            </script>
            """)
    long countPublic(@Param("name") String name);

    /** 游客端：按 ID 查上架景区。 */
    @Select("SELECT id, name, intro, longitude, latitude, address, open_hours, cover_image, "
            + "status, region, phone, sort_order, created_at "
            + "FROM scenic_area WHERE id = #{id} AND deleted = 0 AND status = 1 LIMIT 1")
    ScenicArea findPublicById(@Param("id") Long id);
}
