<script setup lang="ts">
// 触摸屏大屏 根组件：全屏布局
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
  background-color: #f5f5f7;
}
</style>
