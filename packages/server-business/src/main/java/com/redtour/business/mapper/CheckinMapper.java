package com.redtour.business.mapper;

import com.redtour.business.entity.Checkin;
import com.redtour.business.entity.GuideRoute;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 打卡记录 Mapper（注解式 SQL）。
 *
 * 本 Mapper 被 CheckinServiceImpl（打卡）与 RouteServiceImpl（路线完成）共用，
 * 故同时承载打卡写入/统计与路线查询（findRouteById 内联，避免与 GuideRouteMapper 重复定义）。
 * 离线同步写入使用 client_id 唯一键去重。
 */
@Mapper
public interface CheckinMapper {

    /** 写入一条打卡记录（client_id 唯一键用于离线去重）。 */
    @Insert("""
            INSERT INTO checkin
                (device_id, user_id, spot_id, scenic_area_id, checkpoint_id, client_id)
            VALUES
                (#{deviceId}, #{userId}, #{spotId}, #{scenicAreaId}, #{checkpointId}, #{clientId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Checkin checkin);

    /** 是否已存在该 clientId 的打卡（用于同步去重预检）。 */
    @Select("SELECT COUNT(1) FROM checkin WHERE client_id = #{clientId} LIMIT 1")
    int countByClientId(@Param("clientId") String clientId);

    /** 按 clientId 查询打卡记录（同步回查使用）。 */
    @Select("SELECT id, device_id, user_id, spot_id, scenic_area_id, checkpoint_id, client_id, created_at "
            + "FROM checkin WHERE client_id = #{clientId} LIMIT 1")
    Checkin findByClientId(@Param("clientId") String clientId);

    /** 统计某用户在指定景点下已命中的去重打卡点数（用于「全部打卡点命中」完成判定）。 */
    @Select("SELECT COUNT(DISTINCT checkpoint_id) FROM checkin "
            + "WHERE user_id = #{userId} AND spot_id = #{spotId} AND checkpoint_id IS NOT NULL")
    int countDistinctCheckpoints(@Param("userId") Long userId, @Param("spotId") Long spotId);

    /** 统计某用户累计打卡次数。 */
    @Select("SELECT COUNT(1) FROM checkin WHERE user_id = #{userId}")
    int countByUser(@Param("userId") Long userId);

    /** 路线查询内联（RouteService / CheckinService 共用，避免与 GuideRouteMapper 重复定义）。 */
    @Select("SELECT id, scenic_area_id, name, description, spot_ids, duration, difficulty, status, created_at, updated_at "
            + "FROM guide_route WHERE id = #{routeId} AND deleted = 0 LIMIT 1")
    GuideRoute findRouteById(@Param("routeId") Long routeId);
}
