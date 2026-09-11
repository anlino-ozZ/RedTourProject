import request from './request'
import type { LoginParams, UserInfo } from '@red-tour-project/common'

// PC管理后台 鉴权接口（与接口文档第二章对齐，基址 /api/v1）

/** 登录获取 JWT（POST /auth/login），返回用户信息 + token */
export function authLogin(data: LoginParams) {
  return request.post('/auth/login', data) as Promise<UserInfo>
}

/** 获取当前登录用户信息（GET /auth/me） */
export function authMe() {
  return request.get('/auth/me') as Promise<UserInfo>
}

/** 登出（POST /auth/logout），后端将 token 加入黑名单 */
export function authLogout() {
  return request.post('/auth/logout') as Promise<null>
}
