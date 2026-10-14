/**
 * 成就徽章解锁状态本地持久层（沿用 profileStorage 的 localStorage 模式）
 * 键值：{ [badgeId]: earnedAt('YYYY-MM-DD HH:mm') }
 * 后续接入成就接口后可整体替换为服务端数据
 */

const STORAGE_KEY = 'rt_achievements'

export type UnlockedBadgeMap = Record<string, string>

/** 读取所有已解锁徽章（异常时返回空表，静默降级） */
export function getUnlockedBadges(): UnlockedBadgeMap {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? (parsed as UnlockedBadgeMap) : {}
  } catch {
    return {}
  }
}

function pad(n: number) {
  return n < 10 ? `0${n}` : `${n}`
}

function formatNow(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * 解锁徽章；返回 earnedAt（首次解锁为当前时间，已解锁返回原时间）
 * isNew 表示是否本次新解锁（用于决定是否播放解锁动画）
 */
export function unlockBadge(badgeId: string): { earnedAt: string; isNew: boolean } {
  const map = getUnlockedBadges()
  if (map[badgeId]) return { earnedAt: map[badgeId], isNew: false }
  const earnedAt = formatNow()
  map[badgeId] = earnedAt
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(map))
  } catch {
    // 存储失败不影响解锁流程
  }
  return { earnedAt, isNew: true }
}
