import request from './request'
import type {
  ScenicAreaInfo,
  PageResult,
  WikiEntry,
  AskRequest,
  AskResult,
  RecommendationQuestion,
  SttResult,
} from '@red-tour-project/common'

// 触摸屏大屏接口

/** 获取景区详情（触摸屏设备绑定的景区） */
export function getScenicArea(id: number) {
  return request.get<unknown, ScenicAreaInfo>(`/scenic-areas/${id}`)
}

/** Wiki 条目分页列表（快捷信息页数据源，按 tags 取条目） */
export function getWikiList(params: {
  scenicAreaId: number
  page?: number
  pageSize?: number
  keyword?: string
}) {
  return request.get<unknown, PageResult<WikiEntry>>('/admin/wiki', { params })
}

/** Wiki 条目详情 */
export function getWikiDetail(id: number) {
  return request.get<unknown, WikiEntry>(`/admin/wiki/${id}`)
}

/** 智能问答（业务后端透传 AI 引擎，A-03） */
export function askQuestion(data: AskRequest) {
  return request.post<unknown, AskResult>('/ask', data)
}

/**
 * 语音识别 STT（A-11）：multipart 上传录音，返回转写文本
 * @param audio 录音 Blob（MediaRecorder 输出，webm/opus 等浏览器原生格式）
 * @param filename 文件名后缀需与实际编码一致，后端据此解析容器格式
 */
export function speechToText(audio: Blob, filename = 'audio.webm') {
  const form = new FormData()
  form.append('audio', audio, filename)
  // 不手动设置 Content-Type：浏览器发送 FormData 时自动补 multipart boundary
  return request.post<unknown, SttResult>('/voice/stt', form, {
    timeout: 30000,
  })
}

/** 获取推荐问题（A-04） */
export function getRecommendations(scenicAreaId: number) {
  return request.get<unknown, RecommendationQuestion[]>('/ask/recommendations', {
    params: { scenicAreaId },
  })
}

/** 健康检查 */
export function getHealth() {
  return request.get('/health')
}
