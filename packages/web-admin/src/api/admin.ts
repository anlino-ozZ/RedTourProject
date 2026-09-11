import request from './request'
import type { PageQuery, PageResult, UserRole, ScenicAreaInfo } from '@red-tour-project/common'

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
