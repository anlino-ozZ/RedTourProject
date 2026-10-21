// 设备监控接口（接口文档 第十二章 设备模块）
// 设备接口走 /devices（非 /admin 前缀），但同样需要管理员 JWT
import request from './request'

/** 设备运行状态（树莓派 5 + Hailo8L 边缘节点） */
export interface DeviceStatus {
  deviceId: string
  scenicAreaId: number
  /** 是否在线（后端按心跳判定：超过 2 分钟未上报为离线） */
  online: boolean
  /** CPU 温度（℃） */
  cpuTemp: number
  /** 内存占用率（%） */
  memoryUsage: number
  /** 磁盘占用率（%） */
  diskUsage: number
  /** Hailo8L 加速器状态，如 active */
  hailoStatus: string
  cameraConnected: boolean
  speakerConnected: boolean
  /** 最后心跳时间（ISO 字符串） */
  lastHeartbeat: string
}

/** 远程重启指令下发结果 */
export interface DeviceRebootResult {
  deviceId: string
  command: 'reboot'
  sent: boolean
}

/** 硬件指令下发结果（播放 / 停止 / 音量通用） */
export interface DeviceCommandResult {
  deviceId: string
  /** 指令名：play_audio / stop_audio / set_volume */
  command: string
  sent: boolean
  /** 硬件返回的原始响应，便于排查 */
  response: string
}

/**
 * 设备状态列表
 * @param scenicAreaId 景区 ID；普管必传（管辖景区），超管不传=全部景区
 */
export function getDeviceStatus(scenicAreaId?: number) {
  return request.get('/devices/status', {
    params: scenicAreaId != null ? { scenicAreaId } : undefined,
  }) as Promise<DeviceStatus[]>
}

/**
 * 远程重启设备（仅超管；离线设备返回 503）
 */
export function rebootDevice(deviceId: string) {
  return request.post(`/devices/${deviceId}/reboot`) as Promise<DeviceRebootResult>
}

/**
 * 下发播放讲解音频指令
 * @param audioUrl 相对路径（如 /audio/spots/1.mp3）或完整 http(s) URL
 */
export function playDeviceAudio(deviceId: string, audioUrl: string) {
  return request.post(`/devices/${deviceId}/audio/play`, {
    audioUrl,
  }) as Promise<DeviceCommandResult>
}

/** 下发停止播放指令 */
export function stopDeviceAudio(deviceId: string) {
  return request.post(`/devices/${deviceId}/audio/stop`) as Promise<DeviceCommandResult>
}

/** 下发音量设置指令（0-100） */
export function setDeviceVolume(deviceId: string, volume: number) {
  return request.post(`/devices/${deviceId}/volume`, { volume }) as Promise<DeviceCommandResult>
}
