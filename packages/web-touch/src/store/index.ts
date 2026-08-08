import { createPinia } from 'pinia'
import { defineStore } from 'pinia'

// 触摸屏大屏全局状态
const pinia = createPinia()
export default pinia

// 应用级状态占位
export const useAppStore = defineStore('app', {
  state: () => ({
    title: '红色文旅-触摸屏',
  }),
})

export { pinia }
