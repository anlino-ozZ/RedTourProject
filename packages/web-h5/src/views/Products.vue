<script setup lang="ts">
// 特产中心页占位（待后续任务开发）
import { RedCard } from '@red-tour-project/common'
</script>

<template>
  <div class="products">
    <RedCard title="特产中心">
      <p class="products__desc">红色景区特色产品 · 文创好物</p>
      <p class="products__tip">🛍️ 商品列表与下单流程（待开发）</p>
    </RedCard>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.products {
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
