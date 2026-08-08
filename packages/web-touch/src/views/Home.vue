<script setup lang="ts">
// 触摸屏大屏 占位首页：超大字号 + 超大按钮
import { RedCard, RedButton } from '@red-tour/common'

function handleClick() {
  console.log('开始触摸导览')
}
</script>

<template>
  <div class="home">
    <RedCard shadow="always">
      <h1 class="home__title">红色文旅智能导览</h1>
      <p class="home__desc">触摸屏大屏端 · 沉浸式红色文化体验</p>
      <div class="home__actions">
        <RedButton type="primary" size="large" @click="handleClick">开始导览</RedButton>
        <RedButton type="ghost" size="large">景点列表</RedButton>
      </div>
    </RedCard>
  </div>
</template>

<style lang="less" scoped>
.home {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;

  :deep(.rt-card) {
    max-width: 900px;
    width: 100%;
  }
  &__title {
    font-size: 3rem;
    color: #c41e3a;
    font-weight: 700;
    text-align: center;
    margin-bottom: 16px;
  }
  &__desc {
    font-size: 1.5rem;
    color: #8c8c8c;
    text-align: center;
    margin-bottom: 48px;
  }
  &__actions {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 24px;
  }
}
</style>
