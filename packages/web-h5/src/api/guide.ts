/**
 * 智能导览相关接口
 * - 语音讲解复用问答接口 POST /ask（JWT 白名单公开接口，token 由 request 拦截器自动携带）
 */
import request from './request'
import { DEFAULT_SCENIC_AREA_ID } from './scenic'
import { AI_REQUEST_TIMEOUT_MS } from './timeouts'

/** 问答/讲解接口返回结构（字段与后端 AskResult 一致） */
export interface AskResult {
  question: string
  answer: string
  sources: unknown[]
  durationMs: number
  /** 后端生成的 TTS 音频地址；null 表示未生成，前端用浏览器语音合成兜底 */
  audioUrl: string | null
}

/**
 * 当前景区 ID
 * TODO: H5 暂无"选择景区"功能，暂用后端种子数据默认景区（1=遵义会议纪念馆）；
 * 后续接入景区切换后从全局 store 读取
 */
export { DEFAULT_SCENIC_AREA_ID }

/** 智能问答/语音讲解（useVoice=true 时后端生成 TTS，返回 audioUrl） */
export function askNarration(params: {
  question: string
  useVoice: boolean
}): Promise<AskResult> {
  // 语音讲解同样复用 /ask（本地模型推理实测 110~215s），超时与 qa.ts 保持同一个来源
  return request.post(
    '/ask',
    {
      ...params,
      scenicAreaId: DEFAULT_SCENIC_AREA_ID,
    },
    { timeout: AI_REQUEST_TIMEOUT_MS },
  )
}

/** 将后端返回的音频地址解析为可播放地址（相对路径拼接后端 origin） */
export function resolveAudioUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) return url
  const origin = new URL(import.meta.env.VITE_API_BASE_URL, window.location.origin).origin
  return `${origin}${url.startsWith('/') ? '' : '/'}${url}`
}
