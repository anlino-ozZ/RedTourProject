/**
 * 个人中心接口（对齐后端 ProfileController / UserAchievementController）
 * - GET /profile            当前用户概览（昵称、头像、游览/打卡/成就数）
 * - GET /profile/records    当前用户游览记录（分页）
 * - GET /achievements       当前用户已解锁成就
 *
 * 三个接口都需要登录；未登录或请求失败时返回 fallback，页面保留本地兜底展示。
 */
import request from './request'

export type ProfileDataSource = 'api' | 'fallback'

export interface ProfileOverview {
  nickname?: string | null
  avatar?: string | null
  totalVisits?: number | null
  totalCheckins?: number | null
  totalAchievements?: number | null
}

export interface VisitRecord {
  id: number
  userId?: number
  scenicAreaId?: number
  routeId?: number
  spotCount?: number
  duration?: number
  createdAt?: string | null
}

export interface UserAchievement {
  achievementId: number
  name?: string | null
  description?: string | null
  unlockedAt?: string | null
  unlockSource?: string | null
  scenicAreaId?: number | null
}

export interface ProfileOverviewResult {
  data?: ProfileOverview
  source: ProfileDataSource
}

export interface VisitRecordListResult {
  list: VisitRecord[]
  source: ProfileDataSource
}

export interface UserAchievementListResult {
  list: UserAchievement[]
  source: ProfileDataSource
}

/** 当前用户个人中心概览 */
export async function getProfileOverview(): Promise<ProfileOverviewResult> {
  try {
    const payload = (await request.get('/profile', { silent: true })) as ProfileOverview | null
    return { data: payload ?? undefined, source: 'api' }
  } catch {
    return { source: 'fallback' }
  }
}

/** 当前用户游览记录；接口成功返回空列表时也标记为 api */
export async function getVisitRecords(
  page = 1,
  pageSize = 50,
): Promise<VisitRecordListResult> {
  try {
    const payload = (await request.get('/profile/records', {
      params: { page, pageSize },
      silent: true,
    })) as { list?: VisitRecord[] } | VisitRecord[] | null
    if (Array.isArray(payload)) return { list: payload, source: 'api' }
    return { list: payload?.list ?? [], source: 'api' }
  } catch {
    return { list: [], source: 'fallback' }
  }
}

/** 当前用户已解锁成就；接口成功返回空列表时也标记为 api */
export async function getMyAchievements(): Promise<UserAchievementListResult> {
  try {
    const payload = (await request.get('/achievements', { silent: true })) as
      | UserAchievement[]
      | null
    return { list: Array.isArray(payload) ? payload : [], source: 'api' }
  } catch {
    return { list: [], source: 'fallback' }
  }
}