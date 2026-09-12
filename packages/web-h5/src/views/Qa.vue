<script setup lang="ts">
// 问答页占位（待后续任务开发）
import { RedCard } from '@red-tour-project/common'
</script>

<template>
  <div class="qa">
    <RedCard title="智能问答">
      <p class="qa__desc">向 AI 提问，了解党史与红色文化</p>
      <p class="qa__tip">💬 问答输入框与历史记录（待开发）</p>
    </RedCard>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.qa {
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
