/**
 * 景区接口（对齐 docs/05 第三节「景区模块」与后端新上传的公开接口）
 * - GET /scenic-areas        景区列表（游客端下拉选择用，可按名称模糊搜索）
 * - GET /scenic-areas/{id}   景区详情
 *
 * 两个接口均为公开接口（无鉴权）。策略为「真实接口优先 + 失败降级本地兜底」：
 * 请求成功即使用后端数据；失败（后端未上线 / 网络异常）时静默返回 mock 兜底数据，
 * 并用 source 标明数据来源，调用方可据此区分展示。后端上线后调用方零改动。
 */
import type { ScenicAreaInfo } from '@red-tour-project/common'
import request from './request'

export type { ScenicAreaInfo }

/** 当前景区 ID：H5 暂无景区切换功能，统一使用后端种子数据里的遵义会议纪念馆 */
export const DEFAULT_SCENIC_AREA_ID = 1

/** 数据来源：api=后端真实接口；fallback=后端未就绪时的本地兜底数据 */
export type ScenicDataSource = 'api' | 'fallback'

/** 景区列表返回：列表 + 数据来源 */
export interface ScenicAreaListResult {
  list: ScenicAreaInfo[]
  source: ScenicDataSource
}

/** 景区详情返回：详情 + 数据来源 */
export interface ScenicAreaDetailResult {
  area?: ScenicAreaInfo
  source: ScenicDataSource
}

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

/** 兜底数据按名称关键字过滤 */
function mockList(keyword?: string): ScenicAreaInfo[] {
  const key = keyword?.trim()
  return key ? mockScenicAreas.filter((area) => area.name.includes(key)) : mockScenicAreas
}

/** 兼容后端返回数组或分页对象（list / records） */
function normalizeList(payload: unknown): ScenicAreaInfo[] {
  if (Array.isArray(payload)) return payload as ScenicAreaInfo[]
  const page = payload as { list?: ScenicAreaInfo[]; records?: ScenicAreaInfo[] } | null
  if (page?.list) return page.list
  if (page?.records) return page.records
  return []
}

/**
 * 获取景区列表（keyword 可选，按名称模糊搜索）
 * 后端未就绪（接口 500 / 网络异常）时静默降级为本地兜底数据，不弹全局错误提示
 */
export async function getScenicAreas(params?: { keyword?: string }): Promise<ScenicAreaListResult> {
  try {
    // silent：后端未上线期间由本函数兜底，不弹全局错误提示
    const payload: unknown = await request.get('/scenic-areas', { params, silent: true })
    const list = normalizeList(payload)
    return { list, source: 'api' }
  } catch {
    // 后端接口未上线或网络异常：走下方兜底数据，页面不中断
  }
  return { list: mockList(params?.keyword), source: 'fallback' }
}

/** 获取景区详情（后端未就绪时静默降级为本地兜底数据） */
export async function getScenicAreaById(
  id: number = DEFAULT_SCENIC_AREA_ID,
): Promise<ScenicAreaDetailResult> {
  try {
    const payload: unknown = await request.get(`/scenic-areas/${id}`, { silent: true })
    const area = payload as ScenicAreaInfo | null
    if (area?.id) return { area, source: 'api' }
  } catch {
    // 后端接口未上线或网络异常：走下方兜底数据，页面不中断
  }
  return { area: mockScenicAreas.find((item) => item.id === id), source: 'fallback' }
}
