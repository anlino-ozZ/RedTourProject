import { createPinia } from 'pinia'
import { defineStore } from 'pinia'

// PC管理后台全局状态
const pinia = createPinia()
export default pinia

// 应用级状态占位
export const useAppStore = defineStore('app', {
  state: () => ({
    title: '红色文旅-管理后台',
  }),
})

export { pinia }
