<script setup lang="ts">
// 触摸屏大屏 根组件：全屏布局 + 全局空闲定时器（60s 无操作回首页）+ kiosk 按键守卫（W-T-09）
import { onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useIdleTimer } from '@/composables/useIdleTimer'

const router = useRouter()

// 全局空闲定时器：60s 无操作自动返回首页
const idleTimer = useIdleTimer(60_000, () => {
  if (router.currentRoute.value.path !== '/') {
    router.push('/')
  }
})

// ===== kiosk 按键守卫（W-T-09）=====
// 浏览器内能拦的：退出全屏/刷新/开发者工具/打印/历史导航/缩放
// 系统级按键（Alt+F4、Alt+Tab、Ctrl+Alt+T 等）由树莓派 openbox 配置拦截，见 scripts/kiosk/
function isBlockedKey(e: KeyboardEvent): boolean {
  const key = e.key
  // 功能键：F1 帮助 / F3 查找 / F5 刷新 / F11 全屏切换 / F12 开发者工具
  if (['F1', 'F3', 'F5', 'F11', 'F12'].includes(key)) return true
  if (!e.ctrlKey && !e.metaKey) return false
  const k = key.toLowerCase()
  // r 刷新、p 打印、s 保存、u 查看源码、f 查找、w 关标签页、+/-/0 缩放、h 历史
  if (['r', 'p', 's', 'u', 'f', 'w', 'h', '=', '-', '0'].includes(k)) return true
  // 开发者工具：Ctrl+Shift+I / J / C
  if (e.shiftKey && ['i', 'j', 'c'].includes(k)) return true
  return false
}

function onKeydown(e: KeyboardEvent) {
  // Alt + 左右方向键（历史前进/后退）
  if (e.altKey && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
    e.preventDefault()
    return
  }
  if (isBlockedKey(e)) e.preventDefault()
}

function onContextMenu(e: MouseEvent) {
  e.preventDefault()
}

onMounted(() => {
  idleTimer.start()
  window.addEventListener('keydown', onKeydown, { capture: true })
  document.addEventListener('contextmenu', onContextMenu)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown, { capture: true })
  document.removeEventListener('contextmenu', onContextMenu)
})
</script>

<template>
  <div class="app-container">
    <router-view />
  </div>
</template>

<style lang="less" scoped>
.app-container {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background-color: #000;
  // kiosk 触摸屏：禁止长按选中文字/图片
  user-select: none;
  -webkit-user-select: none;
  -webkit-touch-callout: none;
}
</style>
