import { defineStore } from 'pinia'
import type { LoginParams, UserInfo } from '@red-tour-project/common'
import {
  fetchMe,
  login as loginApi,
  logout as logoutApi,
  register as registerApi,
  type RegisterParams,
} from '@/api/auth'
import { ApiError } from '@/api/request'

/** token 存储键（与 api/request.ts 注入 Authorization 时读取的键保持一致） */
const TOKEN_KEY = 'rt_token'

/**
 * 登录用户状态
 * - token 持久化到 localStorage，api/request.ts 据此注入 Authorization
 * - user 为后端脱敏用户信息，刷新页面后可用 refreshMe() 恢复
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: null as UserInfo | null,
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    /** 展示名：优先昵称，其次用户名 */
    displayName: (state) => state.user?.nickname || state.user?.username || '游客',
  },
  actions: {
    /** 写入会话（登录 / 注册成功时调用） */
    setSession(result: UserInfo) {
      this.user = { ...result }
      if (result.token) {
        this.token = result.token
        localStorage.setItem(TOKEN_KEY, result.token)
      }
    },
    /** 登录 */
    async login(params: LoginParams) {
      const result = await loginApi(params)
      this.setSession(result)
      return result
    },
    /** 游客自助注册 */
    async register(params: RegisterParams) {
      const result = await registerApi(params)
      this.setSession(result)
      return result
    },
    /** 刷新页面后恢复用户信息；失败说明 token 已失效，直接清空会话 */
    async refreshMe() {
      if (!this.token) return null
      try {
        const result = await fetchMe()
        this.user = result
        return result
      } catch (err) {
        // token 失效（业务码 401，或 HTTP 401 已被拦截器清掉本地 token）时清空会话；
        // 网络异常等其它错误保留登录态，避免后端未启动时被误登出
        if (!localStorage.getItem(TOKEN_KEY) || (err instanceof ApiError && err.code === 401)) {
          this.clear()
        }
        return null
      }
    },
    /** 登出：先通知后端（写黑名单），无论成功失败都清空本地会话 */
    async logout() {
      try {
        if (this.token) await logoutApi()
      } catch {
        // 网络异常也要清本地，避免用户卡在已登录状态
      } finally {
        this.clear()
      }
    },
    /** 清空本地会话 */
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
    },
  },
})