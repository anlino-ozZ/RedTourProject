import request from './request'
import type { ScenicAreaInfo } from '@red-tour-project/common'

// 公开景区列表接口（接口文档第三章 GET /scenic-areas，无需鉴权）
// 用于下拉选择景区（如用户管理绑定管辖景区）
// 注意：后端该接口返回分页对象 { list, total }，这里统一归一化为数组，避免调用方遍历报错
export async function getScenicAreas(keyword?: string): Promise<ScenicAreaInfo[]> {
  const res = await request.get('/scenic-areas', {
    params: keyword ? { keyword } : undefined,
  })
  if (Array.isArray(res)) return res as ScenicAreaInfo[]
  const list = (res as { list?: ScenicAreaInfo[] } | undefined)?.list
  return Array.isArray(list) ? list : []
}

// 健康检查
export function getHealth() {
  return request.get('/health')
}
