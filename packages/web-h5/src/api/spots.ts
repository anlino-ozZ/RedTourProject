/**
 * 景点接口（对齐 docs/05 第四节与后端 SpotPublicController）
 * - GET /spots            景点列表（分页，公开，仅上架）
 * - GET /spots/{id}       景点详情（公开）
 *
 * 真实接口优先；后端未就绪 / 网络异常 / 返回为空时降级本地 mock，并用 source 标明来源。
 * 后端 SpotResult 只返回经纬度、images 为 JSON 字符串，且不含 tag / interests / 地图 x-y，
 * 这里统一映射成 UI 使用的 GuideSpot：经纬度按当前列表做线性投影，标签与兴趣按名称/简介推断。
 */
import request from './request'
import { DEFAULT_SCENIC_AREA_ID } from './scenic'
import { spots as mockSpots, type GuideSpot, type InterestTag } from '@/mock/guide'

export type { GuideSpot, InterestTag }

/** 数据来源：api=后端真实接口；fallback=后端未就绪时的本地兜底数据 */
export type SpotDataSource = 'api' | 'fallback'

export interface SpotListResult {
  list: GuideSpot[]
  source: SpotDataSource
}

const PAGE_SIZE = 50

/** 后端景点返回结构 */
interface SpotDto {
  id: number
  name: string
  intro?: string | null
  images?: string | string[] | null
  longitude?: number | string | null
  latitude?: number | string | null
  audioUrl?: string | null
  status?: number | null
}

/** 兼容后端返回数组或分页对象（PageResult.list） */
function toDtoList(payload: unknown): SpotDto[] {
  if (Array.isArray(payload)) return payload as SpotDto[]
  const page = payload as { list?: SpotDto[] } | null
  return page?.list ?? []
}

/** images 是 JSON 字符串（或已经是数组） */
function parseImages(images: SpotDto['images']): string[] {
  if (!images) return []
  if (Array.isArray(images)) return images
  try {
    const parsed: unknown = JSON.parse(images)
    return Array.isArray(parsed)
      ? parsed.filter((item): item is string => typeof item === 'string')
      : []
  } catch {
    return [images]
  }
}

/** 名称/简介推断展示标签（后端不返回 tag） */
function inferTag(name: string, intro: string): string {
  const text = `${name}${intro}`
  if (/战役|战斗|哨口|会师/.test(text)) return '战役遗址'
  if (/故居|旧居|旧址/.test(text)) return '革命旧址'
  if (/博物馆|陈列馆|纪念馆/.test(text)) return '博物馆'
  if (/陵园|纪念碑|纪念地/.test(text)) return '纪念地'
  return '红色景点'
}

/** 名称/简介推断兴趣标签（推荐算法加权用，后端不返回 interests） */
function inferInterests(name: string, intro: string): InterestTag[] {
  const text = `${name}${intro}`
  const tags: InterestTag[] = []
  if (/战役|战斗|哨口|会师/.test(text)) tags.push('战役')
  if (/故居|旧居|旧址|人物|同志/.test(text)) tags.push('人物')
  if (/文物|博物馆|陈列馆|馆藏/.test(text)) tags.push('文物')
  if (/历史|斗争|会议/.test(text)) tags.push('历史')
  return tags.length ? tags : ['历史']
}

function toNumber(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === '') return null
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

/** 经纬度 → 地图百分比坐标（按当前列表 min/max 线性投影，单点落中心） */
function projectCoords(list: SpotDto[]): { x: number; y: number }[] {
  const lons = list.map((dto) => toNumber(dto.longitude)).filter((v): v is number => v !== null)
  const lats = list.map((dto) => toNumber(dto.latitude)).filter((v): v is number => v !== null)
  const minLon = Math.min(...lons)
  const maxLon = Math.max(...lons)
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  const spanLon = maxLon - minLon
  const spanLat = maxLat - minLat
  return list.map((dto) => {
    const lon = toNumber(dto.longitude)
    const lat = toNumber(dto.latitude)
    if (lon === null || lat === null) return { x: 50, y: 50 }
    // 只有一个点（跨度为 0）时落中心，避免除零
    const x = spanLon === 0 ? 50 : ((lon - minLon) / spanLon) * 76 + 12
    const y = spanLat === 0 ? 50 : ((maxLat - lat) / spanLat) * 76 + 12
    return { x: Math.round(x * 10) / 10, y: Math.round(y * 10) / 10 }
  })
}

function mapSpot(dto: SpotDto, coord: { x: number; y: number }): GuideSpot {
  const name = dto.name ?? ''
  const intro = dto.intro ?? ''
  return {
    id: dto.id,
    name,
    tag: inferTag(name, intro),
    desc: intro,
    image: parseImages(dto.images)[0],
    interests: inferInterests(name, intro),
    x: coord.x,
    y: coord.y,
    latitude: toNumber(dto.latitude) ?? undefined,
    longitude: toNumber(dto.longitude) ?? undefined,
  }
}

/** 获取景点列表（公开接口；后端未就绪时静默降级 mock） */
export async function getSpots(
  scenicAreaId: number = DEFAULT_SCENIC_AREA_ID,
): Promise<SpotListResult> {
  try {
    const payload: unknown = await request.get('/spots', {
      params: { scenicAreaId, page: 1, pageSize: PAGE_SIZE },
      silent: true,
    })
    const dtos = toDtoList(payload)
    const coords = projectCoords(dtos)
    return { list: dtos.map((dto, index) => mapSpot(dto, coords[index])), source: 'api' }
  } catch {
    // 后端接口未上线或网络异常：走下方兜底数据
  }
  return { list: mockSpots, source: 'fallback' }
}

/** 获取景点详情（后端未就绪时静默降级 mock） */
export async function getSpotById(id: number): Promise<GuideSpot | undefined> {
  try {
    const payload: unknown = await request.get(`/spots/${id}`, { silent: true })
    const dto = payload as SpotDto | null
    if (dto?.id) return mapSpot(dto, { x: 50, y: 50 })
  } catch {
    // 后端接口未上线或网络异常：走下方兜底数据
  }
  return mockSpots.find((spot) => spot.id === id)
}
