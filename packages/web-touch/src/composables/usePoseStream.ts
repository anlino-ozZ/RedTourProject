// 实时姿态识别 WebSocket（W-T-06，对接 A-13 /api/v1/pose/stream）
// 前端推送 Base64 视频帧，接收 action / confidence / keypoints / triggerContent
// 断线自动指数退避重连；组件卸载时彻底关闭，避免泄漏
import { onBeforeUnmount, ref, type Ref } from 'vue'

export interface PoseResult {
  /** 动作标识：salute / mill / wave / unknown（后端也可能返回其它标识，原样透传） */
  action: string
  /** 置信度 0-1 */
  confidence: number
  /** COCO-17 关键点，归一化坐标 [[x, y], ...]（可能为空表示未检测到人体） */
  keypoints?: number[][]
  /** 触发讲解内容（confidence ≥ 0.85 时用于全屏特效展示） */
  triggerContent?: string
}

export type StreamStatus = 'mock' | 'connecting' | 'live' | 'reconnecting'

interface UsePoseStreamOptions {
  /** 实时返回最新识别结果（仅在连接存活时回调） */
  onResult: (r: PoseResult) => void
  /** 获取当前摄像头 video 元素（用于抽帧） */
  getVideo: () => HTMLVideoElement | null
  /** 推帧频率，默认 8fps（平衡识别实时性与带宽/引擎压力） */
  fps?: number
}

export function usePoseStream(opts: UsePoseStreamOptions) {
  const status: Ref<StreamStatus> = ref('mock')
  let ws: WebSocket | null = null
  let frameTimer: ReturnType<typeof setInterval> | null = null
  let manualClose = false
  let retryCount = 0
  let retryTimer: ReturnType<typeof setTimeout> | null = null

  // 抽帧用离屏 canvas（缩小到 480 宽，降低带宽与引擎压力）
  const captureCanvas = document.createElement('canvas')
  const captureCtx = captureCanvas.getContext('2d')
  const FRAME_WIDTH = 480

  // WS 地址由 HTTP baseURL 派生：http(s)://host/api/v1 → ws(s)://host/api/v1/pose/stream
  function buildWsUrl(): string {
    const base: string = import.meta.env.VITE_API_BASE_URL || ''
    const wsBase = base.replace(/^http/, 'ws')
    const scenicId = Number(import.meta.env.VITE_TOUCH_SCENIC_AREA_ID) || 1
    const deviceId = import.meta.env.VITE_TOUCH_DEVICE_NO || 'RT-TS-05'
    return `${wsBase}/pose/stream?scenicAreaId=${scenicId}&deviceId=${encodeURIComponent(deviceId)}`
  }

  function captureFrame(): string | null {
    const video = opts.getVideo()
    if (!video || !captureCtx || video.readyState < 2 || video.videoWidth === 0) return null
    const scale = FRAME_WIDTH / video.videoWidth
    captureCanvas.width = FRAME_WIDTH
    captureCanvas.height = Math.round(video.videoHeight * scale)
    // 发送原始（非镜像）帧，保证后端坐标基于真实画面
    captureCtx.drawImage(video, 0, 0, captureCanvas.width, captureCanvas.height)
    return captureCanvas.toDataURL('image/jpeg', 0.7).replace(/^data:image\/\w+;base64,/, '')
  }

  function startFramePusher() {
    stopFramePusher()
    const interval = 1000 / (opts.fps ?? 8)
    frameTimer = setInterval(() => {
      if (!ws || ws.readyState !== WebSocket.OPEN) return
      const base64 = captureFrame()
      if (!base64) return
      // 消息格式为单一事实入口，如后端契约调整只改这里
      ws.send(JSON.stringify({ image: base64 }))
    }, interval)
  }

  function stopFramePusher() {
    if (frameTimer) {
      clearInterval(frameTimer)
      frameTimer = null
    }
  }

  // 兼容多种响应包裹：裸结果 / {data: 结果} / {type, payload}
  function normalizeResult(raw: unknown): PoseResult | null {
    if (!raw || typeof raw !== 'object') return null
    const obj = raw as Record<string, unknown>
    const inner =
      (obj.data && typeof obj.data === 'object' ? obj.data : obj) as Record<string, unknown> ?? obj
    if (typeof inner.action !== 'string' && typeof inner.confidence !== 'number') return null
    return {
      action: String(inner.action ?? 'unknown'),
      confidence: Number(inner.confidence ?? 0),
      keypoints: Array.isArray(inner.keypoints) ? (inner.keypoints as number[][]) : undefined,
      triggerContent:
        typeof inner.triggerContent === 'string' ? inner.triggerContent : undefined,
    }
  }

  function scheduleReconnect() {
    if (manualClose) return
    status.value = 'reconnecting'
    const delay = Math.min(8000, 1000 * 2 ** retryCount++)
    retryTimer = setTimeout(connect, delay)
  }

  function connect() {
    if (manualClose) return
    status.value = retryCount > 0 ? 'reconnecting' : 'connecting'
    let socket: WebSocket
    try {
      socket = new WebSocket(buildWsUrl())
    } catch {
      scheduleReconnect()
      return
    }
    ws = socket

    socket.onopen = () => {
      retryCount = 0
      status.value = 'live'
      startFramePusher()
    }

    socket.onmessage = (ev) => {
      try {
        const parsed = JSON.parse(typeof ev.data === 'string' ? ev.data : '')
        const result = normalizeResult(parsed)
        if (result) opts.onResult(result)
      } catch {
        // 非 JSON / 心跳包等忽略
      }
    }

    socket.onerror = () => {
      socket.close()
    }

    socket.onclose = () => {
      stopFramePusher()
      if (ws === socket) ws = null
      scheduleReconnect()
    }
  }

  function disconnect() {
    manualClose = true
    stopFramePusher()
    if (retryTimer) clearTimeout(retryTimer)
    if (ws) {
      ws.onclose = null
      ws.onerror = null
      ws.onopen = null
      ws.onmessage = null
      ws.close()
      ws = null
    }
    status.value = 'mock'
  }

  onBeforeUnmount(disconnect)

  return { status, connect, disconnect }
}
