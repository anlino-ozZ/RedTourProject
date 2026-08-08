/**
 * 前后端 AI 统一 TS 接口类型定义
 * 三套前端与后端 / AI 引擎共用，保证数据结构一致
 */

// ===== 通用响应 =====
/** 统一接口响应结构 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页查询结果 */
export interface PageResult<T = unknown> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

/** 分页查询参数 */
export interface PageQuery {
  page: number
  pageSize: number
  keyword?: string
}

// ===== 用户与权限 =====
export type UserRole = 'super_admin' | 'admin' | 'tourist' | 'touch'

export interface UserInfo {
  id: number
  username: string
  nickname?: string
  role: UserRole
  avatar?: string
  scenicAreaId?: number // 普通管理员管辖景区
  token?: string
}

export interface LoginParams {
  username: string
  password: string
}

// ===== 景点与导览 =====
export interface SpotInfo {
  id: number
  name: string
  intro: string
  images: string[]
  longitude?: number
  latitude?: number
  audioUrl?: string
  wikiRef?: string // 关联 LLM Wiki 条目
}

export interface GuideRoute {
  id: number
  name: string
  description?: string
  spotIds: number[]
  duration: number // 预计时长（分钟）
  difficulty?: 'easy' | 'medium' | 'hard'
}

// ===== 智能问答 =====
export interface AskRequest {
  question: string
  scenicAreaId?: number
  useVoice?: boolean
}

export interface AskResult {
  question: string
  answer: string
  sources: string[] // 引用的 Wiki 条目
  durationMs: number
}

// ===== 姿态识别（CV）=====
export interface PoseResult {
  action: 'salute' | 'mill' | 'wave' | 'unknown' // 敬礼 / 推磨 / 挥手 / 未知
  confidence: number
  keypoints?: number[][] // 关节点坐标
  timestamp: number
}

// ===== 特产 / 文创 =====
export interface ProductInfo {
  id: number
  name: string
  price: number
  description: string
  image: string
  stock: number
}

// ===== 剧本杀 / 成就 =====
export interface Achievement {
  id: number
  title: string
  icon: string
  unlocked: boolean
}

// ===== 硬件 / 设备状态 =====
export interface DeviceStatus {
  deviceId: string
  online: boolean
  cpuTemp?: number
  memoryUsage?: number
  lastHeartbeat: number
}
