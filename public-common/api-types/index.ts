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

export interface RegisterParams {
  username: string
  password: string
  nickname?: string
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
  wikiSummary?: string // 详情页返回：关联 Wiki 条目摘要文本
}

export interface GuideRoute {
  id: number
  name: string
  description?: string
  spotIds: number[]
  duration: number // 预计时长（分钟）
  difficulty?: 'easy' | 'medium' | 'hard'
  status?: 0 | 1 // 0 下线 / 1 上线（管理端使用）
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
  audioUrl?: string // useVoice=true 时返回：TTS 语音播报文件地址
}

// ===== 语音识别 STT（A-11）=====
/** POST /voice/stt 响应：音频转写文本（multipart 上传，字段名 audio） */
export interface SttResult {
  text: string
}

// ===== 姿态识别（CV）=====
export interface PoseTriggerContent {
  title: string // 讲解标题（如：军礼的由来）
  audioUrl?: string // 讲解音频地址（触摸屏/树莓派播放）
  wikiRef?: string // 关联 Wiki 条目
}

export interface PoseResult {
  action: 'salute' | 'mill' | 'wave' | 'unknown' // 敬礼 / 推磨 / 挥手 / 未知
  confidence: number
  keypoints?: number[][] // 关节点坐标
  timestamp: number
  triggerContent?: PoseTriggerContent | null // 置信度达标时推送的触发讲解内容
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
  description?: string
  unlocked: boolean
  unlockedAt?: string // 解锁时间（ISO 字符串，未解锁为 null/undefined）
}

// ===== 硬件 / 设备状态 =====
export interface DeviceStatus {
  deviceId: string
  scenicAreaId: number // 绑定景区 ID
  online: boolean
  cpuTemp?: number
  memoryUsage?: number
  diskUsage?: number
  hailoStatus?: string
  cameraConnected?: boolean
  speakerConnected?: boolean
  lastHeartbeat: string // ISO 时间字符串，如 2026-08-08T15:00:25
}

// ===== 景区 =====
export interface ScenicAreaInfo {
  id: number
  name: string
  intro: string
  longitude?: number
  latitude?: number
  address?: string
  openHours?: string
  coverImage?: string
  status?: number
}

// ===== 文物 =====
export interface ArtifactInfo {
  id: number
  name: string
  era?: string
  intro: string
  images?: string[]
  location?: string
  category?: string
  wikiRef?: string
  wikiSummary?: string
}

// ===== 剧本 =====
export interface ScriptRole {
  id: number
  name: string
  avatar: string
  skill?: string
}

export interface ScriptNode {
  id: number
  sceneText: string
  options: { text: string; nextNodeId: number }[]
}

export interface ScriptInfo {
  id: number
  title: string
  description?: string
  coverImage?: string
  roles: ScriptRole[]
  startNodeId?: number
  nodes?: ScriptNode[]
  achievements?: Achievement[]
}

// ===== 导览打卡 =====
export interface CheckinRequest {
  spotId: number
  longitude: number
  latitude: number
}

export interface CheckinResult {
  spotId: number
  checked: boolean
  progress: string
  achievementUnlocked: Achievement | null
}

export interface RouteCompleteResult {
  routeId: number
  completed: boolean
  duration: number
  achievements: Achievement[]
}

// ===== 推荐问题 =====
export interface RecommendationQuestion {
  id: number
  question: string
}

// ===== 个人中心 =====
export interface ProfileInfo {
  nickname: string
  avatar?: string
  totalVisits: number
  totalCheckins: number
  totalAchievements: number
}

export interface VisitRecord {
  id: number
  scenicAreaId: number
  scenicAreaName: string
  routeName?: string
  spotCount: number
  duration: number
  createdAt: string
}

// ===== 特产订单 =====
export interface ProductOrderResult {
  orderId: number
  status: string
  createdAt: string
}

// ===== 姿态单次识别结果（结构与流式 PoseResult 一致）=====
export type PoseRecognizeResult = PoseResult

// ===== 姿态成就扫码领取（W-T-07）=====
/** 触摸屏二维码携带的临时 token 解码后的载荷（由后端签发存 Redis，5 分钟有效） */
export interface PoseClaimTokenPayload {
  jti: string // token 唯一标识（Redis key）
  action: string // 触发动作：salute / mill / wave
  scenicAreaId: number
  deviceId: string
  iat: number // 签发时间（毫秒）
  exp: number // 过期时间（毫秒）
}

/** POST /achievements/claim 请求体 */
export interface AchievementClaimParams {
  token: string
}

/** POST /achievements/claim 响应体 */
export interface AchievementClaimResult {
  achievement: Achievement
  unlockedAt: string // 本次解锁时间（ISO）
  firstUnlock: boolean // true=首次解锁，false=此前已领取（幂等）
}

// ===== 知识库 Wiki =====
export interface WikiEntry {
  id: number
  title: string
  filePath?: string // .md 素材文件路径
  tags?: string[] // 标签（快捷信息页按 tag 取条目：scenic-intro / open-hours / facility）
  links?: string[] // [[wiki-links]] 引用的其他条目
  compileStatus?: 'pending' | 'done' | 'failed' // LLM 编译状态
  updatedAt?: string // ISO 时间字符串
  content?: string // 详情接口返回：Markdown 正文（设施条目存 JSON 点位数组）
}
