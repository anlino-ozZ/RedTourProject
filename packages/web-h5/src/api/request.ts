import axios, { type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

// 单次请求可传 silent: true 关闭全局错误提示，改由页面自行展示（如登录表单内联报错）
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}

/** 业务错误：携带后端 ApiResponse.code，便于调用方区分「登录失效」等场景 */
export class ApiError extends Error {
  readonly code: number

  constructor(message: string, code: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

// 游客移动端H5 axios 封装
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
})

// 请求拦截器：注入 token
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('rt_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：解包 ApiResponse，401 跳登录
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    if (res.code === 200 || res.code === 0) {
      return res.data
    }
    const message = res.message || '请求失败'
    // silent 请求由调用方自行展示错误（如登录表单内联提示），不弹全局提示
    if (!response.config.silent) {
      ElMessage.error(message)
    }
    return Promise.reject(new ApiError(message, res.code))
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('rt_token')
      ElMessage.warning('登录已过期，请重新登录')
      // 后续接入登录页后在此跳转
    } else if (!error.config?.silent) {
      // config.silent = true 的请求（如后台轮询）失败时不弹全局错误提示
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  },
)

export default request
