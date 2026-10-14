/**
 * 剧本与成就接口桩（后端 S-10/S-11/S-16/S-18 尚未完成）
 * - GET /scripts              剧本列表（nodes JSON 剧情节点树 + achievements 可解锁徽章 + 上下架）
 * - GET /scripts/{id}         剧本详情
 * - GET /achievements         成就徽章列表
 * - GET /interactive/tasks    互动任务列表
 * - POST /scripts/{id}/finish 剧本通关解锁成就（幂等写入 UserAchievement）
 * 当前 USE_MOCK=true 返回 mock 数据；后端 ready 后改为 false 即可零改动切换
 */
import request from './request'
import {
  mockScripts,
  mockAchievements,
  mockTasks,
  getScriptById as mockGetScriptById,
  getBadgeById as mockGetBadgeById,
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

const USE_MOCK = true

/** 获取剧本列表 */
export async function getScripts(): Promise<Script[]> {
  if (!USE_MOCK) {
    return request.get('/scripts', { params: { status: 'online' } })
  }
  return mockScripts
}

/** 获取剧本详情（排除 locked） */
export async function getScriptById(id?: string): Promise<Script | undefined> {
  if (!USE_MOCK) {
    if (!id) return undefined
    return request.get(`/scripts/${id}`)
  }
  return mockGetScriptById(id)
}

/** 获取成就徽章列表 */
export async function getAchievements(): Promise<AchievementBadge[]> {
  if (!USE_MOCK) {
    return request.get('/achievements')
  }
  return mockAchievements
}

/** 按 ID 获取徽章 */
export async function getBadgeById(id: string): Promise<AchievementBadge | undefined> {
  if (!USE_MOCK) {
    return request.get(`/achievements/${id}`)
  }
  return mockGetBadgeById(id)
}

/** 获取互动任务列表 */
export async function getTasks(): Promise<InteractionTask[]> {
  if (!USE_MOCK) {
    return request.get('/interactive/tasks')
  }
  return mockTasks
}

/** 剧本通关：解锁成就（幂等） */
export async function finishScript(
  scriptId: string,
  params: { score: number; badgeId: string },
): Promise<{ badgeId: string; isNew: boolean }> {
  if (!USE_MOCK) {
    return request.post(`/scripts/${scriptId}/finish`, params)
  }
  // mock：通关逻辑由前端 achievementStorage 处理，此处仅占位
  return { badgeId: params.badgeId, isNew: false }
}
