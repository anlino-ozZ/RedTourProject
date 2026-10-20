import axios, { type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// PC管理后台 axios 封装
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
})

// 登录态本地存储 key（与 store/index.ts 保持一致，裸字符串存 token）
const TOKEN_KEY = 'rt_admin_token'
const USER_KEY = 'rt_admin_user'
const LOGIN_PATH = '/login'

/**
 * 401 统一处理：清除本地登录态
 * - 登录页上的 401 = 用户名/密码错误，只提示不跳转
 * - 其他页面的 401 = 登录过期，提示并携带 redirect 跳登录页
 */
function handleUnauthorized(message?: string) {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  const current = router.currentRoute.value
  if (current.path === LOGIN_PATH) {
    ElMessage.error(message || '用户名或密码错误')
  } else {
    ElMessage.warning('登录已过期，请重新登录')
    router.replace({ path: LOGIN_PATH, query: { redirect: current.fullPath } })
  }
}

// 请求拦截器：注入 token
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：解包 ApiResponse，401 清登录态并跳登录
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    if (res.code === 200 || res.code === 0) {
      return res.data
    }
    // 后端业务 401（HTTP 200 + body.code=401）：token 缺失/过期等
    if (res.code === 401) {
      handleUnauthorized(res.message)
      return Promise.reject(new Error(res.message || '未授权'))
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    if (error.response?.status === 401) {
      // 网关/代理层返回的真实 HTTP 401
      handleUnauthorized()
    } else {
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  },
)

export default request
