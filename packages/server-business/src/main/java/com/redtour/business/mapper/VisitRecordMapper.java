package com.redtour.business.mapper;

import com.redtour.business.entity.VisitRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 游览记录 Mapper（注解式 SQL）。
 * 被子代理B（个人中心统计/记录分页）与子代理D（路线完成写记录）共用。
 */
@Mapper
public interface VisitRecordMapper {

    /** 新增游览记录（created_at 由数据库默认）。 */
    @Insert("INSERT INTO visit_record (user_id, scenic_area_id, route_id, spot_count, duration) "
            + "VALUES (#{userId}, #{scenicAreaId}, #{routeId}, #{spotCount}, #{duration})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(VisitRecord record);

    /** 统计某用户的游览记录数。 */
    @Select("SELECT COUNT(1) FROM visit_record WHERE user_id = #{userId}")
    long countByUser(@Param("userId") Long userId);

    /** 某用户游览记录分页（按时间倒序）。 */
    @Select("SELECT id, user_id, scenic_area_id, route_id, spot_count, duration, created_at "
            + "FROM visit_record WHERE user_id = #{userId} ORDER BY created_at DESC "
            + "LIMIT #{offset}, #{pageSize}")
    List<VisitRecord> findByUserId(@Param("userId") Long userId,
                                   @Param("offset") int offset,
                                   @Param("pageSize") int pageSize);

    /** 统计某用户完成某条路线的次数（route 类成就 N 次判定用）。 */
    @Select("SELECT COUNT(1) FROM visit_record WHERE user_id = #{userId} AND route_id = #{routeId}")
    long countByUserAndRoute(@Param("userId") Long userId, @Param("routeId") Long routeId);
}
