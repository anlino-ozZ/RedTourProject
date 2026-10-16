/**
 * AI 智慧问答相关接口
 * - POST /ask                   智能问答（JWT 白名单公开接口，token 由 request 拦截器自动携带）
 * - POST /stt                   语音转文字（录音文件上传，返回识别文本）
 * - GET  /ask/recommendations   获取推荐问题
 * - GET  /audio/tts/{filename}  TTS 音频资源（/ask 返回的 audioUrl 直接交给 audio 播放）
 * 契约与 api/guide.ts（导览语音讲解）保持一致
 */
import request from './request'
import { DEFAULT_SCENIC_AREA_ID } from './scenic'

/** 问答接口返回结构（字段与后端 AskResult 一致） */
export interface AskResult {
  question: string
  answer: string
  sources: unknown[]
  durationMs: number
  /** 后端生成的 TTS 音频地址；null 表示未生成，前端用浏览器语音合成朗读兜底 */
  audioUrl: string | null
}

/** 语音转文字返回结构 */
export interface SttResult {
  /** 识别出的文本 */
  text: string
}

/**
 * 当前景区 ID
 * TODO: H5 暂无"选择景区"功能，暂用后端种子数据默认景区；
 * 后续接入景区切换后从全局 store 读取
 */
export { DEFAULT_SCENIC_AREA_ID }

/** 智能问答（useVoice=true 时后端生成 TTS，返回 audioUrl） */
export function askAi(params: {
  question: string
  scenicAreaId?: number
  useVoice?: boolean
}): Promise<AskResult> {
  return request.post(
    '/ask',
    {
      useVoice: true,
      ...params,
      scenicAreaId: params.scenicAreaId ?? DEFAULT_SCENIC_AREA_ID,
    },
    // LLM 生成耗时较长，放宽单次请求超时
    { timeout: 60000 },
  )
}

/** 语音转文字：上传录音文件（multipart/form-data，字段名须为后端 SttController 约定的 audio），返回识别文本 */
export function speechToText(file: Blob, filename = 'recording.webm'): Promise<SttResult> {
  const form = new FormData()
  form.append('audio', file, filename)
  return request.post('/stt', form, { timeout: 30000 })
}

/** 获取推荐问题列表；silent=true 时请求失败不弹全局错误提示（用于后台轮询） */
export async function getRecommendations(
  scenicAreaId: number = DEFAULT_SCENIC_AREA_ID,
  options?: { silent?: boolean },
): Promise<string[]> {
  const list = await request.get('/ask/recommendations', {
    params: { scenicAreaId },
    silent: options?.silent,
  } as Parameters<typeof request.get>[1])
  if (!Array.isArray(list)) return []
  return list.map((item: unknown) =>
    typeof item === 'string' ? item : (item as { question?: string }).question ?? '',
  ).filter(Boolean)
}

/** 将后端返回的音频地址解析为可播放地址（相对路径拼接后端 origin，dev 下 /audio 不在 5173 上） */
export function resolveTtsUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) return url
  const origin = new URL(import.meta.env.VITE_API_BASE_URL, window.location.origin).origin
  return `${origin}${url.startsWith('/') ? '' : '/'}${url}`
}

/** 从问答来源列表中提取第一条可展示的标题（兼容 string / {title} / {name} 等结构） */
export function extractSourceTitle(sources: unknown[] | undefined): string | undefined {
  const first = sources?.[0]
  if (first == null) return undefined
  if (typeof first === 'string') return first
  const obj = first as Record<string, unknown>
  const t = obj.title ?? obj.name ?? obj.source
  return typeof t === 'string' ? t : undefined
}
