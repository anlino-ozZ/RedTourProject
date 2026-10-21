import axios, { type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

// 触摸屏大屏 axios 封装（无登录场景，不注入 token）
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
})

// 请求拦截器：触摸屏无登录，预留 token 注入位
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('rt_touch_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：解包 ApiResponse
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    if (res.code === 200 || res.code === 0) {
      return res.data
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // 后端 ApiResponse 里带了具体文案（如"语音识别服务暂不可用"），优先展示；
    // 直接用 error.message 只会弹出 "Request failed with status code 500" 这类无意义提示
    const message = (error.response?.data?.message as string) || error.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default request
