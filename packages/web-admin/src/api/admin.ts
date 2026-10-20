import request from './request'
import type {
  PageQuery,
  PageResult,
  UserRole,
  ScenicAreaInfo,
  GuideRoute,
  SpotInfo,
} from '@red-tour-project/common'

/**
 * 归一化后端返回值：部分接口（/admin/scripts、/admin/achievements）实际返回分页对象
 * { list, total }，而前端按数组使用。这里统一取 list，避免调用方遍历时抛
 * "xxx.filter is not a function" 之类的渲染期错误。
 */
function toArray<T>(res: T[] | { list?: T[] } | null | undefined): T[] {
  if (Array.isArray(res)) return res
  const list = res?.list
  return Array.isArray(list) ? list : []
}

/** 剧本的 roles / nodes / achievements 在库里是 TEXT 列，接口可能返回 JSON 字符串 */
function parseJsonList<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  if (typeof value === 'string' && value.trim()) {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? (parsed as T[]) : []
    } catch {
      return []
    }
  }
  return []
}

// ===== 管理端用户管理接口（前缀 /admin，接口文档 14.1 节）=====

/** 管理端用户列表项（比 UserInfo 多 status 字段） */
export interface AdminUserItem {
  id: number
  username: string
  nickname?: string
  role: UserRole
  scenicAreaId?: number | null
  avatar?: string
  status: number // 0 禁用 / 1 启用
  createdAt?: string
  updatedAt?: string
}

/** 用户列表查询参数 */
export interface AdminUserQuery extends PageQuery {
  username?: string
  role?: UserRole
}

/** 新增/编辑用户请求体 */
export interface AdminUserPayload {
  username: string
  password?: string // 编辑时留空表示不修改
  nickname?: string
  role: UserRole
  scenicAreaId?: number | null
  status?: number
}

/** 用户列表（分页） */
export function getAdminUsers(params: AdminUserQuery) {
  return request.get('/admin/users', { params }) as Promise<PageResult<AdminUserItem>>
}

/** 新增用户 */
export function createAdminUser(data: AdminUserPayload) {
  return request.post('/admin/users', data) as Promise<AdminUserItem>
}

/** 编辑用户 */
export function updateAdminUser(id: number, data: AdminUserPayload) {
  return request.put(`/admin/users/${id}`, data) as Promise<AdminUserItem>
}

/** 删除用户（软删除） */
export function deleteAdminUser(id: number) {
  return request.delete(`/admin/users/${id}`) as Promise<null>
}

// ===== 管理端景区管理接口（接口文档 14.2 节）=====

/** 景区列表查询参数 */
export interface AdminScenicQuery extends PageQuery {
  keyword?: string
  scenicAreaId?: number
}

/** 新增/编辑景区请求体 */
export interface AdminScenicPayload {
  name: string
  intro?: string
  longitude?: number
  latitude?: number
  address?: string
  openHours?: string
  coverImage?: string
  status?: number
}

/** 景区列表（分页） */
export function getAdminScenicAreas(params: AdminScenicQuery) {
  return request.get('/admin/scenic-areas', { params }) as Promise<PageResult<ScenicAreaInfo>>
}

/** 新增景区 */
export function createAdminScenicArea(data: AdminScenicPayload) {
  return request.post('/admin/scenic-areas', data) as Promise<ScenicAreaInfo>
}

/** 编辑景区 */
export function updateAdminScenicArea(id: number, data: AdminScenicPayload) {
  return request.put(`/admin/scenic-areas/${id}`, data) as Promise<ScenicAreaInfo>
}

/** 删除景区 */
export function deleteAdminScenicArea(id: number) {
  return request.delete(`/admin/scenic-areas/${id}`) as Promise<null>
}

// ===== 管理端景点列表接口（供路线选择景点用）=====

/** 管理端景点列表项（比 SpotInfo 多 scenicAreaId） */
export interface AdminSpotItem extends SpotInfo {
  scenicAreaId: number
}

/** 按景区获取景点列表（不分页，供下拉选择用） */
export function getAdminSpots(scenicAreaId: number) {
  return request.get('/admin/spots', {
    params: { scenicAreaId },
  }) as Promise<AdminSpotItem[]>
}

// ===== 管理端导览路线管理接口（接口文档 14.3 节）=====

/** 管理端路线列表项（比 GuideRoute 多 scenicAreaId） */
export interface AdminRouteItem extends GuideRoute {
  scenicAreaId: number
}

/** 路线列表查询参数 */
export interface AdminRouteQuery extends PageQuery {
  keyword?: string
  scenicAreaId?: number
}

/** 新增/编辑路线请求体 */
export interface AdminRoutePayload {
  name: string
  scenicAreaId: number
  description?: string
  spotIds: number[]
  duration: number
  difficulty?: 'easy' | 'medium' | 'hard'
  status?: number
}

/** 路线列表（分页） */
export function getAdminRoutes(params: AdminRouteQuery) {
  return request.get('/admin/routes', { params }) as Promise<PageResult<AdminRouteItem>>
}

/** 新增路线 */
export function createAdminRoute(data: AdminRoutePayload) {
  return request.post('/admin/routes', data) as Promise<AdminRouteItem>
}

/** 编辑路线 */
export function updateAdminRoute(id: number, data: AdminRoutePayload) {
  return request.put(`/admin/routes/${id}`, data) as Promise<AdminRouteItem>
}

/** 删除路线 */
export function deleteAdminRoute(id: number) {
  return request.delete(`/admin/routes/${id}`) as Promise<null>
}

// ===== 管理端打卡点管理接口（接口文档 14.4 节）=====

/** 管理端打卡点列表项 */
export interface AdminCheckpointItem {
  id: number
  spotId: number
  spotName?: string // 后端联表返回
  scenicAreaId?: number // 后端联表返回
  longitude: number
  latitude: number
  radius: number // 打卡有效半径（米）
}

/** 打卡点列表查询参数 */
export interface AdminCheckpointQuery extends PageQuery {
  spotId?: number
  scenicAreaId?: number
}

/** 新增/编辑打卡点请求体 */
export interface AdminCheckpointPayload {
  spotId: number
  longitude: number
  latitude: number
  radius: number
}

/** 打卡点列表（分页） */
export function getAdminCheckpoints(params: AdminCheckpointQuery) {
  return request.get('/admin/checkpoints', { params }) as Promise<PageResult<AdminCheckpointItem>>
}

/** 新增打卡点 */
export function createAdminCheckpoint(data: AdminCheckpointPayload) {
  return request.post('/admin/checkpoints', data) as Promise<AdminCheckpointItem>
}

/** 编辑打卡点 */
export function updateAdminCheckpoint(id: number, data: AdminCheckpointPayload) {
  return request.put(`/admin/checkpoints/${id}`, data) as Promise<AdminCheckpointItem>
}

/** 删除打卡点 */
export function deleteAdminCheckpoint(id: number) {
  return request.delete(`/admin/checkpoints/${id}`) as Promise<null>
}

// ===== 管理端特产/文创管理接口（接口文档 14.5 节，表 product）=====

/** 产品分类：specialty 特产 / cultural 文创 */
export type ProductCategory = 'specialty' | 'cultural'

/** 管理端产品列表项（比 ProductInfo 多 scenicAreaId/category/status） */
export interface AdminProductItem {
  id: number
  scenicAreaId: number
  name: string
  price: number
  description?: string
  image?: string
  stock: number
  category: ProductCategory
  status: number // 0 下架 / 1 上架
}

/** 产品列表查询参数 */
export interface AdminProductQuery extends PageQuery {
  keyword?: string
  scenicAreaId?: number
  category?: ProductCategory
}

/** 新增/编辑产品请求体 */
export interface AdminProductPayload {
  name: string
  scenicAreaId: number
  price: number
  description?: string
  image?: string
  stock: number
  category: ProductCategory
  status?: number
}

/** 产品列表（分页） */
export function getAdminProducts(params: AdminProductQuery) {
  return request.get('/admin/products', { params }) as Promise<PageResult<AdminProductItem>>
}

/** 新增产品 */
export function createAdminProduct(data: AdminProductPayload) {
  return request.post('/admin/products', data) as Promise<AdminProductItem>
}

/** 编辑产品 */
export function updateAdminProduct(id: number, data: AdminProductPayload) {
  return request.put(`/admin/products/${id}`, data) as Promise<AdminProductItem>
}

/** 删除产品 */
export function deleteAdminProduct(id: number) {
  return request.delete(`/admin/products/${id}`) as Promise<null>
}

// ===== 管理端特产意向订单接口（接口文档 14.6 节，表 product_order）=====
// 意向订单由游客侧产生，管理端仅支持 列表/详情/状态流转，无新增、编辑、删除

/** 订单状态：pending 待处理 / contacted 已联系 / closed 已关闭 */
export type ProductOrderStatus = 'pending' | 'contacted' | 'closed'

/** 管理端订单列表项（productName/scenicArea* 由后端联表返回） */
export interface AdminProductOrderItem {
  id: number
  productId: number
  productName?: string
  productImage?: string
  scenicAreaId?: number
  scenicAreaName?: string
  userId?: number | null
  phone: string
  quantity: number
  remark?: string
  status: ProductOrderStatus
  createdAt: string
  updatedAt?: string
}

/** 订单列表查询参数 */
export interface AdminProductOrderQuery extends PageQuery {
  status?: ProductOrderStatus
  phone?: string
  scenicAreaId?: number
}

/** 订单列表（分页） */
export function getAdminProductOrders(params: AdminProductOrderQuery) {
  return request.get('/admin/orders', {
    params,
  }) as Promise<PageResult<AdminProductOrderItem>>
}

/** 订单详情 */
export function getAdminProductOrder(id: number) {
  return request.get(`/admin/orders/${id}`) as Promise<AdminProductOrderItem>
}

/** 流转订单状态（pending→contacted→closed）
 * 后端为 @RequestParam 接收 status/handleRemark，这里用 params 传参而非 JSON body
 */
export function updateAdminProductOrderStatus(
  id: number,
  status: ProductOrderStatus,
  handleRemark?: string,
) {
  return request.put(`/admin/orders/${id}/status`, null, {
    params: { status, handleRemark },
  }) as Promise<null>
}

// ===== 管理端文物管理接口（接口文档 14.7 节，表 artifact）=====

/** 管理端文物列表项 */
export interface AdminArtifactItem {
  id: number
  scenicAreaId: number
  name: string
  era?: string // 年代，如 1935年
  intro: string
  images?: string[] // 图片 URL 数组
  location?: string // 出土/收藏地点
  category?: string // 品类，如 武器 / 文献
  wikiRef?: string // 关联 Wiki 条目路径
  status: number // 0 下架 / 1 上架
}

/** 文物列表查询参数 */
export interface AdminArtifactQuery extends PageQuery {
  keyword?: string
  scenicAreaId?: number
  category?: string
}

/** 新增/编辑文物请求体 */
export interface AdminArtifactPayload {
  name: string
  scenicAreaId: number
  era?: string
  intro: string
  images?: string[]
  location?: string
  category?: string
  wikiRef?: string
  status?: number
}

/** 文物列表（分页） */
export function getAdminArtifacts(params: AdminArtifactQuery) {
  return request.get('/admin/artifacts', { params }) as Promise<PageResult<AdminArtifactItem>>
}

/** 新增文物 */
export function createAdminArtifact(data: AdminArtifactPayload) {
  return request.post('/admin/artifacts', data) as Promise<AdminArtifactItem>
}

/** 编辑文物 */
export function updateAdminArtifact(id: number, data: AdminArtifactPayload) {
  return request.put(`/admin/artifacts/${id}`, data) as Promise<AdminArtifactItem>
}

/** 删除文物 */
export function deleteAdminArtifact(id: number) {
  return request.delete(`/admin/artifacts/${id}`) as Promise<null>
}

// ===== 管理端成就定义接口（接口文档 14.13 节，表 achievement）=====
// 成就为全部景区公用的全局定义，无景区过滤；列表不分页（数据量小）

/** 触发类型：visit 游览 / pose 姿态 / script 剧本 / checkin 打卡 */
export type AchievementTriggerType = 'visit' | 'pose' | 'script' | 'checkin'

/** 成就定义项 */
export interface AdminAchievementItem {
  id: number
  title: string
  icon?: string
  description?: string
  triggerType: AchievementTriggerType
  /** JSON 触发条件，不同触发类型结构不同（visit 例：{ type: 'first_visit' }） */
  triggerCondition?: Record<string, unknown> | null
  createdAt: string
}

/** 新增/编辑成就请求体 */
export interface AdminAchievementPayload {
  title: string
  icon?: string
  description?: string
  triggerType: AchievementTriggerType
  triggerCondition?: Record<string, unknown> | null
}

/** 成就列表（后端返回分页对象，这里只取 list，页面侧按全量渲染） */
export async function getAdminAchievements(): Promise<AdminAchievementItem[]> {
  const res = await request.get('/admin/achievements')
  return toArray(res as AdminAchievementItem[] | { list?: AdminAchievementItem[] })
}

/** 新增成就 */
export function createAdminAchievement(data: AdminAchievementPayload) {
  return request.post('/admin/achievements', data) as Promise<AdminAchievementItem>
}

/** 编辑成就 */
export function updateAdminAchievement(id: number, data: AdminAchievementPayload) {
  return request.put(`/admin/achievements/${id}`, data) as Promise<AdminAchievementItem>
}

/** 删除成就（仅未被用户解锁记录引用的可删除，由后端校验） */
export function deleteAdminAchievement(id: number) {
  return request.delete(`/admin/achievements/${id}`) as Promise<null>
}

// ===== 管理端知识库（Wiki 条目）接口（接口文档 14.5 节）=====
// Wiki 条目为景区级素材：scenicAreaId 必传；上传素材后由后端编译为 Markdown 文件

/** 编译状态：pending 待编译 / compiling 编译中 / done 已完成 / failed 失败 */
export type WikiCompileStatus = 'pending' | 'compiling' | 'done' | 'failed'

/** Wiki 条目列表项（不含正文） */
export interface AdminWikiItem {
  id: number
  scenicAreaId: number
  title: string
  compileStatus: WikiCompileStatus
  filePath?: string // 编译后生成的文件路径，如 wiki/scenic_1/遵义会议.md
  tags?: string[] // 分类标签
  links?: string[] // 引用的 Wiki 条目路径数组
  updatedAt: string
}

/** Wiki 条目详情（含正文） */
export interface AdminWikiDetail extends AdminWikiItem {
  content?: string // Markdown 正文
}

/** Wiki 列表查询参数（scenicAreaId 必传） */
export interface AdminWikiQuery extends PageQuery {
  scenicAreaId: number
  keyword?: string
  compileStatus?: WikiCompileStatus
}

/** 上传素材并触发编译请求体 */
export interface AdminWikiUploadPayload {
  scenicAreaId: number
  title: string
  content: string // 素材文本内容（Markdown/纯文本）
  tags?: string[]
}

/** 编辑条目内容请求体 */
export interface AdminWikiEditPayload {
  title?: string
  content?: string
  tags?: string[]
}

/** Wiki 列表（分页，scenicAreaId 必传） */
export function getAdminWikiList(params: AdminWikiQuery) {
  return request.get('/admin/wiki', { params }) as Promise<PageResult<AdminWikiItem>>
}

/** 上传素材并触发编译 */
export function uploadAdminWiki(data: AdminWikiUploadPayload) {
  return request.post('/admin/wiki/upload', data) as Promise<AdminWikiItem>
}

/** 条目详情（含正文） */
export function getAdminWikiDetail(id: number) {
  return request.get(`/admin/wiki/${id}`) as Promise<AdminWikiDetail>
}

/** 编辑条目内容 */
export function updateAdminWiki(id: number, data: AdminWikiEditPayload) {
  return request.put(`/admin/wiki/${id}`, data) as Promise<AdminWikiItem>
}

/** 重新编译 */
export function compileAdminWiki(id: number) {
  return request.post(`/admin/wiki/${id}/compile`) as Promise<AdminWikiItem>
}

/** 删除条目 */
export function deleteAdminWiki(id: number) {
  return request.delete(`/admin/wiki/${id}`) as Promise<null>
}

// ===== 管理端剧本管理接口（接口文档 14.8 节，表 scripts）=====
// 剧本由角色列表 + 剧情节点树组成；游客在节点做出选择跳转下一节点，完成剧本解锁成就

/** 剧本角色 */
export interface ScriptRole {
  id?: number
  name: string
  avatar?: string // 头像图片 URL
  skill?: string // 技能描述
}

/** 剧情选项：选项文本 + 跳转节点 ID */
export interface ScriptNodeOption {
  text: string
  /** 跳转目标节点 ID；为空表示剧终 */
  nextNodeId?: number | null
}

/** 剧情节点 */
export interface ScriptNode {
  id: number
  sceneText: string // 场景描述文本
  options: ScriptNodeOption[]
}

/** 剧本（列表即全量字段，管理端无单独详情接口） */
export interface AdminScriptItem {
  id: number
  scenicAreaId: number
  title: string
  description?: string
  coverImage?: string
  roles: ScriptRole[]
  nodes: ScriptNode[]
  achievements?: { id: number }[]
  status: number // 0 下线 / 1 上线
}

/** 新增/编辑剧本请求体 */
export interface AdminScriptPayload {
  scenicAreaId: number
  title: string
  description?: string
  coverImage?: string
  roles: ScriptRole[]
  nodes: ScriptNode[]
  achievements?: { id: number }[]
  status: number
}

/** 剧本列表（scenicAreaId 必传）
 * 后端返回分页对象，这里只取 list；并把 roles/nodes/achievements 统一解析为数组
 * （TEXT 列可能返回 JSON 字符串，直接 .map 会在渲染期抛错）
 */
export async function getAdminScripts(scenicAreaId: number): Promise<AdminScriptItem[]> {
  const res = await request.get('/admin/scripts', { params: { scenicAreaId } })
  return toArray(res as AdminScriptItem[] | { list?: AdminScriptItem[] }).map((item) => ({
    ...item,
    roles: parseJsonList<ScriptRole>(item.roles),
    nodes: parseJsonList<ScriptNode>(item.nodes),
    achievements: parseJsonList<{ id: number }>(item.achievements),
  }))
}

/** 新增剧本 */
export function createAdminScript(data: AdminScriptPayload) {
  return request.post('/admin/scripts', data) as Promise<AdminScriptItem>
}

/** 编辑剧本 */
export function updateAdminScript(id: number, data: AdminScriptPayload) {
  return request.put(`/admin/scripts/${id}`, data) as Promise<AdminScriptItem>
}

/** 删除剧本 */
export function deleteAdminScript(id: number) {
  return request.delete(`/admin/scripts/${id}`) as Promise<null>
}

// ===== 管理端数据统计接口（接口文档 14.9 节，PRD 5.7 用户分析）=====

/** 统计通用查询参数 */
export interface AnalyticsQuery {
  /** 景区 ID；普管必传（管辖景区），超管不传=全部景区 */
  scenicAreaId?: number
  /** 起始日期 YYYY-MM-DD */
  startDate?: string
  /** 结束日期 YYYY-MM-DD */
  endDate?: string
}

/** 游客访问趋势（按日聚合） */
export interface VisitTrendData {
  dates: string[]
  series: number[]
}

/** 热门景点排行项 */
export interface HotSpotItem {
  spotId: number
  spotName: string
  visitCount: number
}

/** 热门问题统计项（文档未给出响应结构，沿用 hot-spots 的名称+数量模式） */
export interface HotQuestionItem {
  questionId?: number
  question: string
  askCount: number
}

/** 特产点击率统计项 */
export interface ProductClickItem {
  productId: number
  productName: string
  clickCount: number
}

/** 游客访问趋势（按日聚合） */
export function getVisitTrend(params: AnalyticsQuery) {
  return request.get('/admin/analytics/visits', { params }) as Promise<VisitTrendData>
}

/** 热门景点排行 */
export function getHotSpots(params: AnalyticsQuery) {
  return request.get('/admin/analytics/hot-spots', { params }) as Promise<HotSpotItem[]>
}

/** 热门问题统计 */
export function getHotQuestions(params: AnalyticsQuery) {
  return request.get('/admin/analytics/hot-questions', { params }) as Promise<HotQuestionItem[]>
}

/** 特产点击率统计 */
export function getProductClicks(params: AnalyticsQuery) {
  return request.get('/admin/analytics/products', { params }) as Promise<ProductClickItem[]>
}
