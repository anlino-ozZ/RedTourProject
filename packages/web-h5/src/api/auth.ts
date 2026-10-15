/**
 * 鉴权接口（对齐后端 AuthController 与 docs/05 接口文档第二章）
 * - POST /auth/login     登录（无鉴权）
 * - POST /auth/register  游客自助注册（无鉴权，仅 tourist 角色）
 * - GET  /auth/me        当前登录用户（需 token）
 * - POST /auth/logout    登出（需 token，后端把 token 写入 Redis 黑名单）
 * 类型复用 public-common 的 LoginParams / UserInfo，避免各端重复定义；
 * 四个接口都带 silent: true —— 失败原因由页面内联展示，不弹全局错误提示
 */
import type { LoginParams, UserInfo } from '@red-tour-project/common'
import request from './request'

/** 注册请求参数（对应后端 RegisterRequest；nickname 可选，仅游客自助注册） */
export interface RegisterParams extends LoginParams {
  nickname?: string
}

/** 登录 / 注册成功返回：token + 脱敏用户信息（对应后端 LoginResult） */
export type AuthResult = UserInfo

/** 登录 */
export function login(params: LoginParams): Promise<AuthResult> {
  return request.post('/auth/login', params, { silent: true })
}

/** 游客自助注册（注册成功即返回 token，无需再登录） */
export function register(params: RegisterParams): Promise<AuthResult> {
  return request.post('/auth/register', params, { silent: true })
}

/** 获取当前登录用户信息（需登录，用于刷新页面后恢复用户信息） */
export function fetchMe(): Promise<AuthResult> {
  return request.get('/auth/me', { silent: true })
}

/** 登出：后端把当前 token 写入 Redis 黑名单，前端同时清除本地 token */
export function logout(): Promise<void> {
  return request.post('/auth/logout', undefined, { silent: true })
}