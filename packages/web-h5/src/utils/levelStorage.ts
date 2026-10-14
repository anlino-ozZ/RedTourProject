/**
 * 用户等级 / 积分（本地持久层）
 *
 * 等级由累计积分驱动，积分来源：
 * - 通关沉浸式剧本：+30（ScriptPlay 通关时落库）
 * - 导览打卡景点：+5（GuideNav 到达景点时落库）
 * - 红色答题：+10（答题模块开发中）
 *
 * 账号体系接入后，积分与等级将迁移到服务端结算，本模块届时只作本地缓存。
 * 沿用项目已有的 localStorage 持久化模式。
 */

export interface LevelRule {
  level: number
  name: string
  /** 达到该等级所需累计积分 */
  need: number
  /** 等级权益说明 */
  perk: string
}

export const LEVEL_RULES: LevelRule[] = [
  { level: 1, name: '红色萌芽', need: 0, perk: '解锁智能导览与红色剧本' },
  { level: 2, name: '红色学员', need: 50, perk: '问答历史收藏与导出' },
  { level: 3, name: '红色行者', need: 120, perk: '导览推荐支持出发位置偏好' },
  { level: 4, name: '红色讲师', need: 250, perk: '专属头像框（即将上线）' },
  { level: 5, name: '红色星火', need: 420, perk: '特产订单专属优惠（即将上线）' },
  { level: 6, name: '红色燎原', need: 650, perk: '专属研学路线提前解锁' },
  { level: 7, name: '筑梦先锋', need: 950, perk: '年度红色研学荣誉认证' },
]

const POINTS_KEY = 'rt_level_points'
// 演示初始积分：让新用户打开就有进度感（接入后端后由服务端积分替代）
const INITIAL_POINTS = 168

export function getPoints(): number {
  const raw = localStorage.getItem(POINTS_KEY)
  const n = raw == null ? NaN : Number(raw)
  return Number.isFinite(n) ? n : INITIAL_POINTS
}

/** 增加积分（负数可用于扣减），返回新的累计值 */
export function addPoints(delta: number): number {
  const total = Math.max(0, getPoints() + Math.floor(delta))
  localStorage.setItem(POINTS_KEY, String(total))
  return total
}

export interface LevelInfo {
  level: number
  levelName: string
  /** 当前累计积分 */
  points: number
  /** 下一等级所需累计积分，满级为 null */
  nextNeed: number | null
  nextName: string | null
  /** 升级进度 0~1，满级为 1 */
  progress: number
  /** 距下一等级还差积分，满级为 0 */
  remaining: number
}

/** 按累计积分解析等级信息 */
export function getLevelInfo(points = getPoints()): LevelInfo {
  let idx = 0
  for (let i = 0; i < LEVEL_RULES.length; i++) {
    if (points >= LEVEL_RULES[i].need) idx = i
  }
  const cur = LEVEL_RULES[idx]
  const next = idx + 1 < LEVEL_RULES.length ? LEVEL_RULES[idx + 1] : null
  const floor = cur.need
  const progress =
    next ? Math.min(1, Math.max(0, (points - floor) / (next.need - floor))) : 1
  return {
    level: cur.level,
    levelName: cur.name,
    points,
    nextNeed: next ? next.need : null,
    nextName: next ? next.name : null,
    progress,
    remaining: next ? Math.max(0, next.need - points) : 0,
  }
}
