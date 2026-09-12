<script setup lang="ts">
// 触摸屏大屏 根组件：全屏布局 + 全局空闲定时器（60s 无操作回首页）
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useIdleTimer } from '@/composables/useIdleTimer'

const router = useRouter()

// 全局空闲定时器：60s 无操作自动返回首页
const idleTimer = useIdleTimer(60_000, () => {
  if (router.currentRoute.value.path !== '/') {
    router.push('/')
  }
})

onMounted(() => {
  idleTimer.start()
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
}
</style>
