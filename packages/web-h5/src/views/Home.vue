<script setup lang="ts">
// 游客移动端H5 占位首页
import { RedCard, RedButton } from '@red-tour-project/common'

function handleClick() {
  console.log('开始红色之旅')
}
</script>

<template>
  <div class="home">
    <RedCard title="欢迎使用红色文旅智能导览系统">
      <p class="home__desc">游客移动端 · 随时随地探索红色足迹</p>
      <div class="home__actions">
        <RedButton type="primary" @click="handleClick">开始红色之旅</RedButton>
        <RedButton type="ghost">查看附近景点</RedButton>
      </div>
    </RedCard>
  </div>
</template>

<style lang="less" scoped>
.home {
  padding: 16px;
  &__desc {
    color: #8c8c8c;
    font-size: 14px;
    margin-bottom: 16px;
  }
  &__actions {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
}
</style>
