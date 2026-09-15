/**
 * AI 问答本地会话历史（localStorage 持久化）
 * 后续接入账号体系后可迁移到服务端
 */

export interface QaMessage {
  role: 'ai' | 'user'
  content: string
  /** AI 回答的引用来源 */
  source?: string
  time: number
}

export interface QaSession {
  id: string
  /** 会话标题：取第一条用户提问 */
  title: string
  messages: QaMessage[]
  updatedAt: number
}

const STORAGE_KEY = 'red-tour:qa-sessions'
const MAX_SESSIONS = 50

function readAll(): QaSession[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const list = JSON.parse(raw) as QaSession[]
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

function writeAll(list: QaSession[]) {
  // 超出上限时只保留最近的 MAX_SESSIONS 条
  const trimmed = list
    .slice()
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, MAX_SESSIONS)
  localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed))
}

/** 获取全部会话（按更新时间倒序） */
export function listSessions(): QaSession[] {
  return readAll().sort((a, b) => b.updatedAt - a.updatedAt)
}

/** 按 id 获取单个会话 */
export function getSession(id: string): QaSession | null {
  return readAll().find((s) => s.id === id) ?? null
}

/** 新建或更新会话（已存在则覆盖） */
export function saveSession(session: QaSession) {
  const list = readAll()
  const idx = list.findIndex((s) => s.id === session.id)
  if (idx >= 0) {
    list[idx] = session
  } else {
    list.push(session)
  }
  writeAll(list)
}

/** 删除单个会话 */
export function deleteSession(id: string) {
  writeAll(readAll().filter((s) => s.id !== id))
}

/** 清空全部历史 */
export function clearSessions() {
  localStorage.removeItem(STORAGE_KEY)
}

/** 生成会话 id */
export function createSessionId(): string {
  return `s_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}
