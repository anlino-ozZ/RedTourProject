import { createPinia } from 'pinia'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { authLogin, authLogout, authMe } from '@/api/auth'
import type { LoginParams, UserInfo } from '@red-tour-project/common'

// PC管理后台全局状态
const pinia = createPinia()
export default pinia

// 应用级状态占位
export const useAppStore = defineStore('app', {
  state: () => ({
    title: '红色文旅-管理后台',
  }),
})

// ===== 登录态本地存储 key =====
// token 为裸字符串存储，与 api/request.ts 请求拦截器读取方式保持一致
const TOKEN_KEY = 'rt_admin_token'
const USER_KEY = 'rt_admin_user'

/** 管理端允许登录的角色（游客账号禁止进入后台） */
const ADMIN_ROLES = ['super_admin', 'admin']

function readStoredUser(): UserInfo | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserInfo) : null
  } catch {
    return null
  }
}

/** 登录用户状态：token + 用户信息，刷新页面后从 localStorage 恢复 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: readStoredUser() as UserInfo | null,
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    role: (state) => state.userInfo?.role ?? '',
    isSuperAdmin: (state) => state.userInfo?.role === 'super_admin',
    displayName: (state) => state.userInfo?.nickname || state.userInfo?.username || '',
  },
  actions: {
    /** 写入本地持久化 */
    persist() {
      localStorage.setItem(TOKEN_KEY, this.token)
      if (this.userInfo) {
        localStorage.setItem(USER_KEY, JSON.stringify(this.userInfo))
      }
    },
    /** 登录：调接口 → 角色校验 → 持久化 token 与用户信息 */
    async login(params: LoginParams) {
      const data = await authLogin(params)
      if (!ADMIN_ROLES.includes(data.role)) {
        // 游客/touch 账号无权进入管理后台，不落登录态
        ElMessage.error('该账号无管理后台访问权限，请使用管理员账号登录')
        throw new Error('该账号无管理后台访问权限')
      }
      this.token = data.token || ''
      this.userInfo = data
      this.persist()
    },
    /** 拉取当前用户信息（/auth/me），用于本地用户信息缺失时恢复 */
    async fetchProfile() {
      const data = await authMe()
      this.userInfo = { ...data, token: this.token }
      this.persist()
    },
    /** 登出：通知后端加黑 token（失败不阻塞），再清本地登录态 */
    async logout() {
      try {
        await authLogout()
      } catch {
        // 后端不可用时本地照常清理
      }
      this.clearAuth()
    },
    /** 清除本地登录态（401 拦截器与登出共用） */
    clearAuth() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})

export { pinia }
