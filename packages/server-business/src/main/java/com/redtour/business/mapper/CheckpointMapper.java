package com.redtour.business.mapper;

import com.redtour.business.entity.Checkpoint;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 打卡点 Mapper（注解式动态 SQL）。
 *
 * 注意：checkpoint 表本身无 scenic_area_id 列，也不含 created_at/updated_at 列；
 * 一个景点允许多个打卡点（spot_id 无唯一约束）。景区归属通过 spot 表间接确定，
 * 因此按景区筛选/隔离时通过 spot 表关联（scenic_area_id = 景区）。
 */
@Mapper
public interface CheckpointMapper {

    /** 管理端分页查询（可按景区/景点筛选；景区隔离由 scenicAreaId 参数承载）。 */
    @Select("""
            <script>
            SELECT c.id, c.spot_id, c.longitude, c.latitude, c.radius
            FROM checkpoint c
            <where>
                AND c.deleted = 0
                <if test="scenicAreaId != null">
                    AND c.spot_id IN (SELECT id FROM spot WHERE scenic_area_id = #{scenicAreaId})
                </if>
                <if test="spotId != null">AND c.spot_id = #{spotId}</if>
            </where>
            ORDER BY c.id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Checkpoint> findPage(@Param("scenicAreaId") Long scenicAreaId,
                              @Param("spotId") Long spotId,
                              @Param("offset") int offset,
                              @Param("pageSize") int pageSize);

    /** 管理端分页计数（与 findPage 同过滤条件）。 */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM checkpoint c
            <where>
                AND c.deleted = 0
                <if test="scenicAreaId != null">
                    AND c.spot_id IN (SELECT id FROM spot WHERE scenic_area_id = #{scenicAreaId})
                </if>
                <if test="spotId != null">AND c.spot_id = #{spotId}</if>
            </where>
            </script>
            """)
    long count(@Param("scenicAreaId") Long scenicAreaId, @Param("spotId") Long spotId);

    /** 按 ID 查询（排除软删）。 */
    @Select("SELECT id, spot_id, longitude, latitude, radius "
            + "FROM checkpoint WHERE id = #{id} AND deleted = 0 LIMIT 1")
    Checkpoint findById(@Param("id") Long id);

    /** 按景点 ID 查询其全部打卡点（仅未删；brief B2 要求）。 */
    @Select("SELECT id, spot_id, longitude, latitude, radius "
            + "FROM checkpoint WHERE spot_id = #{spotId} AND deleted = 0 ORDER BY id ASC")
    List<Checkpoint> findBySpotId(@Param("spotId") Long spotId);

    /** 新增打卡点。 */
    @Insert("INSERT INTO checkpoint (spot_id, longitude, latitude, radius) "
            + "VALUES (#{spotId}, #{longitude}, #{latitude}, #{radius})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Checkpoint checkpoint);

    /** 编辑打卡点（局部更新非空字段，spot_id 可改）。 */
    @Update("""
            <script>
            UPDATE checkpoint
            <set>
                <if test="spotId != null">spot_id = #{spotId},</if>
                <if test="longitude != null">longitude = #{longitude},</if>
                <if test="latitude != null">latitude = #{latitude},</if>
                <if test="radius != null">radius = #{radius},</if>
            </set>
            WHERE id = #{id}
            </script>
            """)
    int updateById(Checkpoint checkpoint);

    /** 软删除打卡点（deleted=1；checkpoint 表已加 deleted 列）。 */
    @Update("UPDATE checkpoint SET deleted = 1 WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /** 通过打卡点 ID 反查其归属景区（用于权限隔离校验）。 */
    @Select("SELECT s.scenic_area_id FROM checkpoint c JOIN spot s ON c.spot_id = s.id "
            + "WHERE c.id = #{checkpointId} LIMIT 1")
    Long selectScenicAreaIdByCheckpointId(@Param("checkpointId") Long checkpointId);

    /** 通过景点 ID 反查其归属景区（用于新增时权限隔离校验）。 */
    @Select("SELECT scenic_area_id FROM spot WHERE id = #{spotId} LIMIT 1")
    Long selectScenicAreaIdBySpotId(@Param("spotId") Long spotId);

    /** 统计某景点的未删打卡点总数（用于「全部打卡点命中」完成判定）。 */
    @Select("SELECT COUNT(1) FROM checkpoint WHERE spot_id = #{spotId} AND deleted = 0")
    int countBySpotId(@Param("spotId") Long spotId);
}
