package com.redtour.business.service;

import com.redtour.business.dto.DeviceHeartbeatResult;
import com.redtour.business.dto.DeviceHeartbeatRequest;
import com.redtour.business.dto.DeviceStatusResult;

import java.util.List;

/**
 * 设备服务：心跳上报、状态查询、远程重启。
 */
public interface DeviceService {

    /** 处理设备心跳，upsert device_status 为在线。 */
    DeviceHeartbeatResult handleHeartbeat(DeviceHeartbeatRequest request);

    /** 查询设备状态列表（scenicAreaId 隔离：admin 强制本人，super 可传 null 查全量）。 */
    List<DeviceStatusResult> listStatus(Long scenicAreaId);

    /** 向指定设备下发重启指令（仅 super_admin）。 */
    void reboot(String deviceId);
}
