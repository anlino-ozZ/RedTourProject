<script setup lang="ts">
// 互动中心占位页（待开发）
</script>

<template>
  <div class="interactive">
    <div class="interactive__icon">🛠️</div>
    <h2 class="interactive__title">互动中心</h2>
    <p class="interactive__text">功能正在紧锣密鼓开发中，敬请期待～</p>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.interactive {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120px 32px;

  &__icon {
    font-size: 56px;
    margin-bottom: @spacing-lg;
    opacity: 0.8;
  }

  &__title {
    margin: 0 0 @spacing-sm;
    font-size: 20px;
    font-weight: 700;
    color: @color-text-primary;
  }

  &__text {
    margin: 0;
    font-size: @font-size-base;
    color: @color-text-secondary;
    text-align: center;
  }
}
</style>
