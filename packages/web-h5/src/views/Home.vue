<script setup lang="ts">
// 游客移动端H5 首页
import { RedCard, RedButton } from '@red-tour-project/common'
import { useRouter } from 'vue-router'

const router = useRouter()

function startTour() {
  router.push('/guide')
}
</script>

<template>
  <div class="home">
    <!-- 顶部 Banner -->
    <div class="home__banner">
      <h1 class="home__banner-title">红色文旅</h1>
      <p class="home__banner-subtitle">智能导览 · 随时随地探索红色足迹</p>
    </div>

    <!-- 快捷入口 -->
    <div class="home__entries">
      <RedCard title="开始导览" shadow="hover">
        <p class="home__entry-desc">查看附近红色景点，聆听革命故事</p>
        <RedButton type="primary" size="medium" @click="startTour">立即出发</RedButton>
      </RedCard>

      <RedCard shadow="hover">
        <p class="home__entry-label">智能问答</p>
        <p class="home__entry-desc">向 AI 提问，了解党史文化</p>
        <RedButton type="ghost" size="medium" @click="router.push('/qa')">去提问</RedButton>
      </RedCard>
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.home {
  overflow-x: hidden; // 防止负 margin 导致的横向溢出
  
  &__banner {
    margin: -@spacing-sm -@spacing-md @spacing-md;
    padding: @spacing-xl @spacing-lg;
    background: linear-gradient(135deg, @color-primary 0%, @color-primary-active 100%);
    color: #fff;
    text-align: center;
    border-radius: 0 0 @radius-lg @radius-lg;
  }
  &__banner-title {
    font-size: 24px;
    font-weight: 700;
    letter-spacing: 2px;
    margin-bottom: @spacing-xs;
  }
  &__banner-subtitle {
    font-size: @font-size-sm;
    opacity: 0.9;
  }

  &__entries {
    display: flex;
    flex-direction: column;
    gap: @spacing-md;
  }
  &__entry-label {
    font-size: @font-size-lg;
    font-weight: 600;
    color: @color-text-primary;
    margin-bottom: @spacing-xs;
  }
  &__entry-desc {
    color: @color-text-secondary;
    font-size: @font-size-sm;
    margin-bottom: @spacing-md;
  }
}
</style>
