/**
 * 景点接口桩（后端 S-04 Spot CRUD 尚未完成）
 * - GET /spots            景点列表（分页、按名称模糊查、上下架筛选）
 * - GET /spots/{id}       景点详情
 * 当前 USE_MOCK=true 返回 mock 数据；后端 ready 后改为 false 即可零改动切换
 */
import request from './request'
import { spots as mockSpots, type GuideSpot, type InterestTag } from '@/mock/guide'

export type { GuideSpot, InterestTag }

const USE_MOCK = true

/** 获取景点列表 */
export async function getSpots(): Promise<GuideSpot[]> {
  if (!USE_MOCK) {
    return request.get('/spots', { params: { status: 'online' } })
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
