// PC管理后台 文件上传服务封装（接口文档 14.14）
// 图片/音频/Wiki 文档统一走 POST /admin/uploads（multipart/form-data）
import request from './request'

/** 上传业务类型：决定后端存储子目录与大小限制 */
export type UploadFileType = 'image' | 'audio' | 'wiki' | 'icon'

/** 单文件大小上限（MB），与后端约定一致 */
export const UPLOAD_SIZE_LIMIT_MB: Record<UploadFileType, number> = {
  image: 10,
  icon: 10,
  audio: 50,
  wiki: 20,
}

/** 各类型默认 accept */
export const UPLOAD_ACCEPT: Record<UploadFileType, string> = {
  image: 'image/*',
  icon: 'image/*',
  audio: 'audio/*',
  wiki: '.pdf,.doc,.docx,.txt,.md',
}

/** 上传参数 */
export interface UploadParams {
  type: UploadFileType
  /** 归属景区 ID（普管必传，用于拼接存储路径；新增景区等无 ID 场景可不传） */
  scenicAreaId?: number
}

/**
 * 上传一个或多个文件
 * 注意：FormData 交给浏览器自动生成 multipart boundary，不能手动设置 Content-Type
 * @returns 后端返回的可访问相对 URL 数组，如 ['/uploads/scenic_1/spots/xxx.jpg']
 */
export function uploadFiles(files: File[], params: UploadParams): Promise<string[]> {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  formData.append('type', params.type)
  if (params.scenicAreaId) {
    formData.append('scenicAreaId', String(params.scenicAreaId))
  }
  // 大文件（音频 50MB / Wiki 20MB）放宽超时到 2 分钟
  return request.post('/admin/uploads', formData, { timeout: 120000 }) as Promise<string[]>
}

/**
 * 删除已上传文件（传 URL 数组）
 * 仅在业务数据已落库、确认不再需要文件时调用；表单编辑中途移除不要调用，
 * 避免用户取消编辑后服务器文件已被删除
 */
export function deleteUploadedFiles(urls: string[]): Promise<null> {
  return request.delete('/admin/uploads', { data: urls }) as Promise<null>
}

// ===== 资源 URL 解析 =====
// 后端返回相对路径（/uploads/...）：生产同源可直接访问，
// 开发环境需拼到后端源站（VITE_API_BASE_URL 去掉 /api/v1 后缀）
const API_BASE = import.meta.env.VITE_API_BASE_URL as string
const API_ORIGIN = API_BASE.replace(/\/api\/v1\/?$/, '')

/** 将后端返回的相对 URL 解析为可直接用于 <img src>/<audio src> 的完整地址 */
export function resolveFileUrl(url?: string | null): string {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  return `${API_ORIGIN}${url.startsWith('/') ? url : `/${url}`}`
}
