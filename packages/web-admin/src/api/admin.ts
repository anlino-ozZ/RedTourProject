import request from './request'
import type {
  PageQuery,
  PageResult,
  UserRole,
  ScenicAreaInfo,
  GuideRoute,
  SpotInfo,
} from '@red-tour-project/common'

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
  return request.get('/admin/product-orders', {
    params,
  }) as Promise<PageResult<AdminProductOrderItem>>
}

/** 订单详情 */
export function getAdminProductOrder(id: number) {
  return request.get(`/admin/product-orders/${id}`) as Promise<AdminProductOrderItem>
}

/** 流转订单状态（pending→contacted→closed） */
export function updateAdminProductOrderStatus(id: number, status: ProductOrderStatus) {
  return request.put(`/admin/product-orders/${id}/status`, { status }) as Promise<null>
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
