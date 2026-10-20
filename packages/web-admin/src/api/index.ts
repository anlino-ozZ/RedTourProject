import request from './request'
import type { ScenicAreaInfo } from '@red-tour-project/common'

// 公开景区列表接口（接口文档第三章 GET /scenic-areas，无需鉴权）
// 用于下拉选择景区（如用户管理绑定管辖景区）
export function getScenicAreas(keyword?: string) {
  return request.get('/scenic-areas', {
    params: keyword ? { keyword } : undefined,
  }) as Promise<ScenicAreaInfo[]>
}

// 健康检查
export function getHealth() {
  return request.get('/health')
}
