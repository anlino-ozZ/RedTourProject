/**
 * 景点接口桩（后端 S-04 Spot CRUD 尚未完成）
 * - GET /spots            景点列表（分页、按名称模糊查、上下架筛选）
 * - GET /spots/{id}       景点详情
 * 当前 USE_MOCK=true 返回 mock 数据；后端 ready 后改为 false 即可零改动切换
 */
import request from './request'
import { DEFAULT_SCENIC_AREA_ID } from './scenic'
import { spots as mockSpots, type GuideSpot, type InterestTag } from '@/mock/guide'

export type { GuideSpot, InterestTag }

const USE_MOCK = true

/** 获取景点列表 */
export async function getSpots(
  scenicAreaId: number = DEFAULT_SCENIC_AREA_ID,
): Promise<GuideSpot[]> {
  if (!USE_MOCK) {
    // 契约（docs/05 第四节）：GET /spots?scenicAreaId=&keyword=&page=&pageSize= 返回分页对象
    // 后端 SpotController 尚未实现；契约 SpotInfo 到 UI GuideSpot 的字段映射（含地图 x/y）待就绪后补齐
    return request.get('/spots', { params: { scenicAreaId, page: 1, pageSize: 50 } })
  }
  return mockSpots
}

/** 获取景点详情 */
export async function getSpotById(id: number): Promise<GuideSpot | undefined> {
  if (!USE_MOCK) {
    return request.get(`/spots/${id}`)
  }
  return mockSpots.find((s) => s.id === id)
}
