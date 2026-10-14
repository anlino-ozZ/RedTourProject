/**
 * 导览路线接口桩（后端 S-05 GuideRoute CRUD 尚未完成）
 * - GET /routes           路线列表（含 spot_ids JSON 数组、难度、上下架）
 * - GET /routes/{id}      路线详情
 * - recommendRoutes       客户端加权评分（纯前端逻辑，后端无对应接口）
 * 当前 USE_MOCK=true 返回 mock 数据；后端 ready 后改为 false 即可零改动切换
 */
import request from './request'
import {
  routes as mockRoutes,
  recommendRoutes as mockRecommend,
  type GuideRoute,
  type FitnessLevel,
  type InterestTag,
  type RecommendOrigin,
} from '@/mock/guide'

export type { GuideRoute, FitnessLevel, InterestTag, RecommendOrigin }

const USE_MOCK = true

/** 获取路线列表 */
export async function getRoutes(): Promise<GuideRoute[]> {
  if (!USE_MOCK) {
    return request.get('/routes', { params: { status: 'online' } })
  }
  return mockRoutes
}

/** 获取路线详情 */
export async function getRouteById(id: number): Promise<GuideRoute | undefined> {
  if (!USE_MOCK) {
    return request.get(`/routes/${id}`)
  }
  return mockRoutes.find((r) => r.id === id)
}

/**
 * AI 加权评分推荐（客户端逻辑）
 * 后端无对应接口，此函数始终在前端执行
 */
export function recommendRoutes(params: {
  interests: InterestTag[]
  fitness: FitnessLevel
  duration: number
  origin?: RecommendOrigin
}): (GuideRoute & { score: number; highlight: string })[] {
  return mockRecommend(params)
}
