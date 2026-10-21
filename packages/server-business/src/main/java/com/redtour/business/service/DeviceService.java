package com.redtour.business.service;

import com.redtour.business.dto.DeviceAudioPlayRequest;
import com.redtour.business.dto.DeviceCommandResult;
import com.redtour.business.dto.DeviceHeartbeatResult;
import com.redtour.business.dto.DeviceHeartbeatRequest;
import com.redtour.business.dto.DeviceStatusResult;
import com.redtour.business.dto.DeviceVolumeRequest;

import java.util.List;

/**
 * 设备服务：心跳上报、状态查询、指令下发（重启 / 播放音频 / 停止播放 / 音量）。
 */
public interface DeviceService {

    /** 处理设备心跳，upsert device_status 为在线。 */
    DeviceHeartbeatResult handleHeartbeat(DeviceHeartbeatRequest request);

    /** 查询设备状态列表（scenicAreaId 隔离：admin 强制本人，super 可传 null 查全量）。 */
    List<DeviceStatusResult> listStatus(Long scenicAreaId);

    /** 向指定设备下发重启指令（仅 super_admin）。 */
    void reboot(String deviceId);

    /** 下发播放讲解音频指令。 */
    DeviceCommandResult playAudio(String deviceId, DeviceAudioPlayRequest request);

    /** 下发停止播放指令。 */
    DeviceCommandResult stopAudio(String deviceId);

    /** 下发音量设置指令。 */
    DeviceCommandResult setVolume(String deviceId, DeviceVolumeRequest request);
}
