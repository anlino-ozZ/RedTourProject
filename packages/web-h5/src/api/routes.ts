/**
 * 导览路线接口（对齐 docs/05 第五节与后端 GuideRoutePublicController）
 * - GET /routes      路线列表（分页，公开，仅上架）
 * - GET /routes/{id} 路线详情（公开）
 *
 * 真实接口优先；后端未就绪 / 网络异常 / 返回为空时降级本地 mock，并用 source 标明来源。
 * 后端 GuideRouteResult 的 spotIds 是 JSON 字符串、difficulty 是 easy/medium/hard，
 * 且不返回 spotCount / distance / visitCount / tag / interests，这里统一映射与推导。
 */
import request from './request'
import { DEFAULT_SCENIC_AREA_ID } from './scenic'
import {
  routes as mockRoutes,
  recommendRoutes as mockRecommend,
  type FitnessLevel,
  type GuideRoute,
  type GuideSpot,
  type InterestTag,
  type RecommendOrigin,
} from '@/mock/guide'

export type { GuideRoute, FitnessLevel, InterestTag, RecommendOrigin }

/** 数据来源：api=后端真实接口；fallback=后端未就绪时的本地兜底数据 */
export type RouteDataSource = 'api' | 'fallback'

export interface RouteListResult {
  list: GuideRoute[]
  source: RouteDataSource
}

const PAGE_SIZE = 50

/** 后端路线返回结构 */
interface RouteDto {
  id: number
  name: string
  description?: string | null
  spotIds?: string | number[] | null
  duration?: number | null
  difficulty?: string | null
  status?: number | null
}

/** 兼容后端返回数组或分页对象（PageResult.list） */
function toDtoList(payload: unknown): RouteDto[] {
  if (Array.isArray(payload)) return payload as RouteDto[]
  const page = payload as { list?: RouteDto[] } | null
  return page?.list ?? []
}

/** spotIds 是 JSON 字符串（或已经是数组） */
function parseSpotIds(spotIds: RouteDto['spotIds']): number[] {
  if (!spotIds) return []
  const toNumbers = (values: unknown[]): number[] =>
    values.map(Number).filter((num) => Number.isFinite(num))
  if (Array.isArray(spotIds)) return toNumbers(spotIds)
  try {
    const parsed: unknown = JSON.parse(spotIds)
    return Array.isArray(parsed) ? toNumbers(parsed) : []
  } catch {
    return []
  }
}

/** easy/medium/hard → 1/2/3（UI 星级用） */
const DIFFICULTY_MAP: Record<string, number> = { easy: 1, medium: 2, hard: 3 }

/** 难度 → 卡片角标文案（后端不返回 tag） */
const TAG_BY_DIFFICULTY: Record<number, string> = { 1: '轻松', 2: '推荐', 3: '挑战' }

function mapRoute(dto: RouteDto): GuideRoute {
  const spotIds = parseSpotIds(dto.spotIds)
  const difficulty = DIFFICULTY_MAP[String(dto.difficulty ?? '').toLowerCase()] ?? 2
  return {
    id: dto.id,
    name: dto.name,
    desc: dto.description ?? '',
    difficulty,
    duration: dto.duration ?? 0,
    spotCount: spotIds.length,
    // 后端暂不返回距离/人气/兴趣标签：置 0，页面按「无数据」展示
    distance: 0,
    tag: TAG_BY_DIFFICULTY[difficulty],
    visitCount: 0,
    spotIds,
    interests: [],
  }
}

/** 获取路线列表（公开接口；后端未就绪时静默降级 mock） */
export async function getRoutes(
  scenicAreaId: number = DEFAULT_SCENIC_AREA_ID,
): Promise<RouteListResult> {
  try {
    const payload: unknown = await request.get('/routes', {
      params: { scenicAreaId, page: 1, pageSize: PAGE_SIZE },
      silent: true,
    })
    const dtos = toDtoList(payload)
    return { list: dtos.map(mapRoute), source: 'api' }
  } catch {
    // 后端接口未上线或网络异常：走下方兜底数据
  }
  return { list: mockRoutes, source: 'fallback' }
}

/** 获取路线详情（后端未就绪时静默降级 mock） */
export async function getRouteById(id: number): Promise<GuideRoute | undefined> {
  try {
    const payload: unknown = await request.get(`/routes/${id}`, { silent: true })
    const dto = payload as RouteDto | null
    if (dto?.id) return mapRoute(dto)
  } catch {
    // 后端接口未上线或网络异常：走下方兜底数据
  }
  return mockRoutes.find((route) => route.id === id)
}

/**
 * AI 加权评分推荐（纯前端逻辑）
 * 传入已加载的真实路线/景点即可用同一套算法评分；不传则用 mock。
 * 真实路线没有 interests 时，用其途经景点的兴趣标签合并补全。
 */
export function recommendRoutes(
  params: {
    interests: InterestTag[]
    fitness: FitnessLevel
    duration: number
    origin?: RecommendOrigin
  },
  data?: { routes?: GuideRoute[]; spots?: GuideSpot[] },
): (GuideRoute & { score: number; highlight: string })[] {
  const spotsById = new Map((data?.spots ?? []).map((spot) => [spot.id, spot]))
  const routes = data?.routes?.map((route) => {
    if (route.interests.length) return route
    const inferred = Array.from(
      new Set(route.spotIds.flatMap((id) => spotsById.get(id)?.interests ?? [])),
    ).slice(0, 3)
    return { ...route, interests: inferred }
  })
  return mockRecommend({ ...params, routes, spots: data?.spots })
}
