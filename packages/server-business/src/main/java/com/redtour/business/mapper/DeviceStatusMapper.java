package com.redtour.business.mapper;

import com.redtour.business.entity.DeviceStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 设备状态 Mapper（注解式 SQL，对齐 ScenicAreaMapper 风格）。
 * 设备主键为 device_id（非自增），心跳 upsert 使用 ON DUPLICATE KEY UPDATE。
 */
@Mapper
public interface DeviceStatusMapper {

    /**
     * 心跳 upsert：存在则更新为在线并刷新各指标与 last_heartbeat，否则插入在线记录。
     */
    @Insert("""
            INSERT INTO device_status
                (device_id, scenic_area_id, online, cpu_temp, memory_usage, disk_usage,
                 hailo_status, camera_connected, speaker_connected, last_heartbeat)
            VALUES
                (#{deviceId}, #{scenicAreaId}, 1, #{cpuTemp}, #{memoryUsage}, #{diskUsage},
                 #{hailoStatus}, #{cameraConnected}, #{speakerConnected}, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                scenic_area_id = VALUES(scenic_area_id),
                online = 1,
                cpu_temp = VALUES(cpu_temp),
                memory_usage = VALUES(memory_usage),
                disk_usage = VALUES(disk_usage),
                hailo_status = VALUES(hailo_status),
                camera_connected = VALUES(camera_connected),
                speaker_connected = VALUES(speaker_connected),
                last_heartbeat = CURRENT_TIMESTAMP
            """)
    int upsert(DeviceStatus status);

    /**
     * 按景区查询设备状态列表；scenicAreaId 为 null 时返回全部（super_admin 全量视角）。
     * online 不直接取库里存值，而是按 last_heartbeat 是否在最近 2 分钟内实时判定，
     * 避免设备断电后仍显示在线（前端 DeviceStatus.online 注释同此口径）。
     *
     * 注意：注解 SQL 走 MyBatis &lt;script&gt; 解析，比较符需转义（AnalyticsMapper 同），
     * 这里改用 BETWEEN 区间判断，彻底避免 &lt; &gt; 字符。
     * last_heartbeat 为 NULL 时 IFNULL 兜底成 1970 年，差值远超 2 分钟，自然判为离线。
     */
    @Select("""
            <script>
            SELECT device_id, scenic_area_id,
                   IF(TIMESTAMPDIFF(MINUTE, IFNULL(last_heartbeat, '1970-01-01 00:00:00'), NOW())
                      BETWEEN 0 AND 2, 1, 0) AS online,
                   cpu_temp, memory_usage, disk_usage,
                   hailo_status, camera_connected, speaker_connected, last_heartbeat
            FROM device_status
            <if test="scenicAreaId != null">
                WHERE scenic_area_id = #{scenicAreaId}
            </if>
            ORDER BY device_id
            </script>
            """)
    List<DeviceStatus> findByScenicAreaId(@Param("scenicAreaId") Long scenicAreaId);

    /** 按设备 ID 查询归属景区（用于下发指令前校验设备归属）。 */
    @Select("SELECT device_id, scenic_area_id FROM device_status WHERE device_id = #{deviceId} LIMIT 1")
    DeviceStatus findByDeviceId(@Param("deviceId") String deviceId);
}
