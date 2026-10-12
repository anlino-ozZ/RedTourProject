/**
 * 每日签到（本地持久层）
 *
 * 每天首次签到 +10 积分（积分落库走 levelStorage），并展示当日革命先辈名言。
 * 记录连续签到天数与累计签到天数；账号体系接入后迁移服务端。
 * 沿用项目已有的 localStorage 持久化模式。
 */

export interface CheckInState {
  /** 最近一次签到日期 YYYY-MM-DD */
  lastDate: string
  /** 连续签到天数（断签归 1） */
  streak: number
  /** 累计签到天数 */
  totalDays: number
}

const KEY = 'rt_checkin'

export function todayStr(d = new Date()): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function yesterdayStr(): string {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  return todayStr(d)
}

export function getCheckIn(): CheckInState {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) {
      const s = JSON.parse(raw) as CheckInState
      if (s && typeof s.lastDate === 'string') return s
    }
  } catch {
    // 数据损坏时按未签到处理
  }
  return { lastDate: '', streak: 0, totalDays: 0 }
}

export function isCheckedToday(): boolean {
  return getCheckIn().lastDate === todayStr()
}

/** 签到：成功返回更新后的状态（含 isNew 标记），今日已签返回 null */
export function checkInToday(): (CheckInState & { isNew: true }) | null {
  const today = todayStr()
  const state = getCheckIn()
  if (state.lastDate === today) return null
  const next: CheckInState = {
    lastDate: today,
    streak: state.lastDate === yesterdayStr() ? state.streak + 1 : 1,
    totalDays: state.totalDays + 1,
  }
  localStorage.setItem(KEY, JSON.stringify(next))
  return { ...next, isNew: true }
}

/**
 * 革命先辈名言：按日期轮换，每天一句且固定
 */
export const REVOLUTION_QUOTES: { text: string; author: string }[] = [
  { text: '星星之火，可以燎原。', author: '毛泽东' },
  { text: '为中华之崛起而读书。', author: '周恩来' },
  { text: '红军不怕远征难，万水千山只等闲。', author: '毛泽东' },
  { text: '敌人只能砍下我们的头颅，决不能动摇我们的信仰。', author: '方志敏' },
  { text: '砍头不要紧，只要主义真。', author: '夏明翰' },
  { text: '试看将来的环球，必是赤旗的世界！', author: '李大钊' },
  { text: '恨不抗日死，留作今日羞。国破尚如此，我何惜此头。', author: '吉鸿昌' },
  { text: '雄关漫道真如铁，而今迈步从头越。', author: '毛泽东' },
  { text: '清贫，洁白朴素的生活，正是我们革命者能够战胜许多困难的地方。', author: '方志敏' },
  { text: '青年之字典，无"困难"之字；青年之口头，无"障碍"之语。', author: '李大钊' },
]

/** 当日名言：按日期确定性轮换（同一天恒定，跨天变化） */
export function quoteOfTheDay(): { text: string; author: string } {
  const d = todayStr()
  let hash = 0
  for (let i = 0; i < d.length; i++) hash = (hash * 31 + d.charCodeAt(i)) >>> 0
  return REVOLUTION_QUOTES[hash % REVOLUTION_QUOTES.length]
}
