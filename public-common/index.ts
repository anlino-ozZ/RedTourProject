/**
 * @red-tour/common 统一入口
 * 三套前端（web-h5 / web-admin / web-touch）统一从此引入公共组件、类型、工具
 */
// 公共组件
export { default as RedButton } from './components/RedButton.vue'
export { default as RedDialog } from './components/RedDialog.vue'
export { default as RedCard } from './components/RedCard.vue'
export { default as RedNav } from './components/RedNav.vue'
export type { NavItem } from './components/RedNav.vue'

// 统一接口类型
export * from './api-types'

// 工具函数
export * from './utils/voice'
export * from './utils/storage'
export * from './utils/date'
