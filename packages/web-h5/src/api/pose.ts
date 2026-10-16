/**
 * 姿态识别接口（对齐 docs/05 第七节 · 后端 PoseRecognizeController）
 * - POST /pose/recognize  上传单帧图像（Base64）做一次识别，返回动作 + 触发讲解
 * 说明：实时推流（WS /pose/stream）供触摸屏端使用；H5 端采用单帧识别，
 * 游客站到摄像头前做动作（敬礼 / 推磨 / 挥手）后识别，成功即播报关联讲解。
 * 类型复用 public-common 的 PoseRecognizeResult，避免各端重复定义。
 */
import type { PoseRecognizeResult, PoseTriggerContent } from '@red-tour-project/common'
import request from './request'
import { DEFAULT_SCENIC_AREA_ID } from './guide'
import { POSE_RECOGNIZE_TIMEOUT_MS } from './timeouts'

export type { PoseRecognizeResult, PoseTriggerContent }

/** 动作 → 中文名（后端 action 取值：salute / mill / wave / unknown） */
export const POSE_ACTION_LABEL: Record<string, string> = {
  salute: '敬礼',
  mill: '推磨',
  wave: '挥手',
  unknown: '未识别到动作',
}

/**
 * 单帧姿态识别
 * @param frame Base64 图像数据（不含 data:image/xxx;base64, 前缀，后端按纯 Base64 解码）
 * @param scenicAreaId 景区 ID，用于匹配触发内容
 */
export function recognizePose(
  frame: string,
  scenicAreaId: number = DEFAULT_SCENIC_AREA_ID,
): Promise<PoseRecognizeResult> {
  // 首帧需要加载模型，推理耗时较长，放宽单次请求超时
  return request.post(
    '/pose/recognize',
    { frame, scenicAreaId },
    { timeout: POSE_RECOGNIZE_TIMEOUT_MS },
  )
}

/** 截取 video 当前帧并转成后端需要的 Base64（JPEG 压缩以控制体积） */
/** 截帧结果：Base64 数据 + 截帧画布尺寸（用于把后端关键点坐标映射回画面） */
export interface CapturedFrame {
  data: string
  width: number
  height: number
}

/** 截取 video 当前帧，返回 Base64 与画布尺寸；画面未就绪时返回 null */
export function captureFrame(video: HTMLVideoElement, maxWidth = 480): CapturedFrame | null {
  if (!video.videoWidth || !video.videoHeight) return null
  const canvas = document.createElement('canvas')
  const scale = video.videoWidth > maxWidth ? maxWidth / video.videoWidth : 1
  canvas.width = Math.max(1, Math.round(video.videoWidth * scale))
  canvas.height = Math.max(1, Math.round(video.videoHeight * scale))
  const ctx = canvas.getContext('2d')
  if (!ctx) return null
  ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
  const data = canvas.toDataURL('image/jpeg', 0.8).replace(/^data:image\/\w+;base64,/, '')
  return { data, width: canvas.width, height: canvas.height }
}

export function captureFrameFromVideo(video: HTMLVideoElement, maxWidth = 480): string {
  return captureFrame(video, maxWidth)?.data ?? ''
}

/** 将后端返回的讲解音频地址解析为可播放地址（相对路径拼接后端 origin） */
export function resolvePoseAudioUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) return url
  const origin = new URL(import.meta.env.VITE_API_BASE_URL, window.location.origin).origin
  return `${origin}${url.startsWith('/') ? '' : '/'}${url}`
}