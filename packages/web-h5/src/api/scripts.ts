/**
 * 剧本接口（对齐 docs/05 第十一节与后端 ScriptPublicController）
 * - GET /scripts            剧本列表（公开，分页，scenicAreaId 必传）
 * - GET /scripts/{id}       剧本详情（公开，含 roles / nodes 剧情节点树）
 * - POST /scripts/{id}/complete 通关解锁成就（需登录）
 *
 * 真实接口优先；后端未就绪 / 网络异常时降级本地 mock，并用 source 标明来源。
 * 说明：
 * - 后端剧本是「剧情节点 + 选项」分支结构（nodes），当前播放页仍是任务式流程（tasks）。
 *   getScriptById 会把 nodes 一并返回（ScriptStoryNode[]），播放页在映射出可播放任务前继续用 mock 兜底，
 *   待播放页改造为节点选择流程后直接切换即可。
 * - 成就徽章定义 / 互动任务 / 通关奖励仍是本地 mock：后端 /achievements 返回的是「我的已解锁成就」，
 *   语义不同；/interactive/tasks 后端暂无对应接口。
 */
import request from './request'
import { DEFAULT_SCENIC_AREA_ID } from './scenic'
import { getMyAchievements, type UserAchievement } from './profile'
import {
  mockScripts,
  mockAchievements,
  mockTasks,
  getScriptById as mockGetScriptById,
  type Script,
  type AchievementBadge,
  type InteractionTask,
  type ScriptTask,
  type ScriptRole,
  type ScriptNode,
  type ScriptTaskType,
} from '@/mock/interaction'

export type {
  Script,
  AchievementBadge,
  InteractionTask,
  ScriptTask,
  ScriptRole,
  ScriptNode,
  ScriptTaskType,
}

/** 数据来源：api=后端真实接口；fallback=本地兜底数据 */
export type ScriptDataSource = 'api' | 'fallback'

/** 后端剧情节点（分支结构） */
export interface ScriptStoryOption {
  text: string
  nextNodeId: number | null
}

export interface ScriptStoryNode {
  id: number
  sceneText: string
  options: ScriptStoryOption[]
}

export interface ScriptListResult {
  list: Script[]
  source: ScriptDataSource
}

export interface ScriptDetailResult {
  script?: Script
  /** 后端剧情节点树（节点式播放流程使用） */
  nodes: ScriptStoryNode[]
  startNodeId: number | null
  source: ScriptDataSource
}

const PAGE_SIZE = 50

interface ScriptRoleDto {
  id?: number | string
  name?: string
  avatar?: string
  skill?: string
  title?: string
  desc?: string
  color?: string
}

interface ScriptNodeDto {
  id?: number
  sceneText?: string
  text?: string
  options?: { text?: string; nextNodeId?: number | null }[]
}

interface ScriptDto {
  id: number
  title?: string | null
  description?: string | null
  coverImage?: string | null
  roles?: string | ScriptRoleDto[] | null
  nodes?: string | ScriptNodeDto[] | null
  startNodeId?: number | null
  achievements?: string | { id?: number | string }[] | null
  status?: number | null
  /** 后端暂未提供，兼容将来直接给任务数组 */
  tasks?: string | ScriptTask[] | null
}

/** 兼容后端返回数组或分页对象（PageResult.list） */
function toDtoList(payload: unknown): ScriptDto[] {
  if (Array.isArray(payload)) return payload as ScriptDto[]
  const page = payload as { list?: ScriptDto[] } | null
  return page?.list ?? []
}

/** roles / nodes / achievements 都是 JSON 字符串（或已经是数组） */
function parseJson<T>(value: string | T[] | null | undefined): T[] {
  if (!value) return []
  if (Array.isArray(value)) return value
  try {
    const parsed: unknown = JSON.parse(value)
    return Array.isArray(parsed) ? (parsed as T[]) : []
  } catch {
    return []
  }
}

function mapRoles(roles: ScriptRoleDto[]): ScriptRole[] {
  return roles.map((role, index) => ({
    id: String(role.id ?? index + 1),
    name: role.name ?? `角色${index + 1}`,
    title: role.skill || role.title || '剧中角色',
    desc: role.desc ?? '',
    color: role.color ?? '#c41e3a',
  }))
}

function mapNodes(nodes: ScriptNodeDto[]): ScriptStoryNode[] {
  return nodes.map((node, index) => ({
    id: Number(node.id ?? index + 1),
    sceneText: node.sceneText ?? node.text ?? '',
    options: (node.options ?? []).map((option) => ({
      text: option.text ?? '',
      nextNodeId: option.nextNodeId ?? null,
    })),
  }))
}

function mapScript(dto: ScriptDto): { script: Script; nodes: ScriptStoryNode[]; startNodeId: number | null } {
  const roles = mapRoles(parseJson<ScriptRoleDto>(dto.roles))
  const nodes = mapNodes(parseJson<ScriptNodeDto>(dto.nodes))
  const achievements = parseJson<{ id?: number | string }>(dto.achievements)
  return {
    script: {
      id: String(dto.id),
      title: dto.title ?? '未命名剧本',
      subtitle: '沉浸式红色剧本',
      color: '#c41e3a',
      intro: dto.description ?? '',
      roles: roles.length
        ? roles
        : [{ id: 'r1', name: '红军战士', title: '剧中角色', desc: '', color: '#c41e3a' }],
      tasks: parseJson<ScriptTask>(dto.tasks),
      ending: '',
      reward: {
        points: 0,
        badgeId: achievements[0]?.id != null ? String(achievements[0].id) : '',
      },
      locked: dto.status === 0,
    },
    nodes,
    startNodeId: dto.startNodeId ?? null,
  }
}

/** 获取剧本列表（公开接口；后端未就绪时静默降级 mock） */
export async function getScripts(
  scenicAreaId: number = DEFAULT_SCENIC_AREA_ID,
): Promise<ScriptListResult> {
  try {
    const payload: unknown = await request.get('/scripts', {
      params: { scenicAreaId, page: 1, pageSize: PAGE_SIZE },
      silent: true,
    })
    const dtos = toDtoList(payload)
    const list = dtos.map((dto) => mapScript(dto).script)
    const playable = list.filter((script) => script.tasks.length > 0)
    // 后端当前只有「剧情节点」式剧本，播放页尚不支持节点流程。
    // 此时若继续展示后端列表，会出现「0 个任务 · 0 积分」，且点进去实际播放的是 mock 剧本。
    // 因此列表与播放页统一回退到任务式 mock，保证任务数、积分、通关徽章完全一致。
    if (!playable.length) return { list: mockScripts, source: 'fallback' }
    return { list: playable, source: 'api' }
  } catch {
    // 后端接口未上线或网络异常：走下方兜底数据
  }
  return { list: mockScripts, source: 'fallback' }
}

/** 获取剧本详情（公开接口；后端未就绪时静默降级 mock） */
export async function getScriptById(id?: string): Promise<ScriptDetailResult> {
  if (id) {
    try {
      const payload: unknown = await request.get(`/scripts/${id}`, { silent: true })
      const dto = payload as ScriptDto | null
      if (dto?.id != null) return { ...mapScript(dto), source: 'api' }
    } catch {
      // 后端接口未上线或网络异常：走下方兜底数据
    }
  }
  return { script: mockGetScriptById(id), nodes: [], startNodeId: null, source: 'fallback' }
}

function formatAchievementTime(value?: string | null): string | undefined {
  if (!value) return undefined
  return String(value).replace('T', ' ').slice(0, 16)
}

function toAchievementBadge(item: UserAchievement): AchievementBadge {
  return {
    id: String(item.achievementId),
    name: item.name ?? `成就 ${item.achievementId}`,
    earned: true,
    color: '#d4af37',
    desc: item.description ?? '',
    earnedAt: formatAchievementTime(item.unlockedAt),
  }
}

/** 获取成就徽章列表：已登录时以真实已解锁成就为准，未登录/失败时保留本地兜底 */
export async function getAchievements(): Promise<AchievementBadge[]> {
  if (!localStorage.getItem('rt_token')) return mockAchievements
  const result = await getMyAchievements()
  if (result.source !== 'api') return mockAchievements
  const earned = result.list.map(toAchievementBadge)
  const catalog: AchievementBadge[] = mockAchievements.map((badge) => {
    const hit = earned.find((item) => item.id === badge.id || item.name === badge.name)
    return hit ?? { ...badge, earned: false, earnedAt: undefined, color: undefined }
  })
  for (const badge of earned) {
    if (!catalog.some((item) => item.id === badge.id || item.name === badge.name)) {
      catalog.push(badge)
    }
  }
  return catalog
}

/** 获取互动任务列表（后端暂无对应接口，暂用本地数据） */
export async function getTasks(): Promise<InteractionTask[]> {
  return mockTasks
}
