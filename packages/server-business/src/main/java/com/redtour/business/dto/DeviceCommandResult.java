package com.redtour.business.dto;

import lombok.Data;

/**
 * 硬件指令下发结果（管理端通用）。
 */
@Data
public class DeviceCommandResult {

    /** 目标设备 ID */
    private String deviceId;

    /** 下发的指令名，如 play_audio / stop_audio / set_volume */
    private String command;

    /** 是否已成功送达硬件 */
    private Boolean sent;

    /** 硬件返回的原始响应（便于前端排查，如 {"cmd":"play_audio","status":"ok"}） */
    private String response;
}
