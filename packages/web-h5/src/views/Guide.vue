<script setup lang="ts">
// 导览页占位（待后续任务开发）
import { RedCard } from '@red-tour-project/common'
</script>

<template>
  <div class="guide">
    <RedCard title="红色导览">
      <p class="guide__desc">探索红色足迹，聆听革命故事</p>
      <p class="guide__tip">📍 景点列表与地图导览（待开发）</p>
    </RedCard>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.guide {
  &__desc {
    color: @color-text-regular;
    font-size: @font-size-base;
    margin-bottom: @spacing-sm;
  }
  &__tip {
    color: @color-text-secondary;
    font-size: @font-size-sm;
  }
}
</style>
