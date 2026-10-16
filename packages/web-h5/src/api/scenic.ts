/**
 * 景区接口（对齐 docs/05 第三节「景区模块」）
 * - GET /scenic-areas        景区列表（游客端下拉选择用，可按名称模糊搜索）
 * - GET /scenic-areas/{id}   景区详情
 * 后端 ScenicAreaController 尚未实现，当前 USE_MOCK=true 用 H5 侧 mock 兜底；
 * 后端 ready 后把 USE_MOCK 改为 false 即可切换（字段与契约一致，调用方零改动）。
 */
import type { ScenicAreaInfo } from '@red-tour-project/common'
import request from './request'

export type { ScenicAreaInfo }

/** 当前景区 ID：H5 暂无景区切换功能，统一使用后端种子数据里的遵义会议纪念馆 */
export const DEFAULT_SCENIC_AREA_ID = 1

const USE_MOCK = true

/** mock 景区（与后端种子数据 id/name 对齐，便于切换真实接口时对照） */
const mockScenicAreas: ScenicAreaInfo[] = [
  {
    id: 1,
    name: '遵义会议纪念馆',
    intro: '遵义会议是中国共产党历史上一个生死攸关的转折点。',
    longitude: 106.9272,
    latitude: 27.6934,
    address: '贵州省遵义市红花岗区子尹路96号',
    openHours: '08:30-17:00',
    coverImage: '',
    status: 1,
  },
]

/** 获取景区列表（keyword 可选，按名称模糊搜索） */
export async function getScenicAreas(params?: { keyword?: string }): Promise<ScenicAreaInfo[]> {
  if (!USE_MOCK) {
    return request.get('/scenic-areas', { params })
  }
  const keyword = params?.keyword?.trim()
  return keyword ? mockScenicAreas.filter((area) => area.name.includes(keyword)) : mockScenicAreas
}

/** 获取景区详情 */
export async function getScenicAreaById(
  id: number = DEFAULT_SCENIC_AREA_ID,
): Promise<ScenicAreaInfo | undefined> {
  if (!USE_MOCK) {
    return request.get(`/scenic-areas/${id}`)
  }
  return mockScenicAreas.find((area) => area.id === id)
}