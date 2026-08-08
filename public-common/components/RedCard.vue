<script setup lang="ts">
/**
 * 全局公共卡片
 * 统一容器样式，可选标题与阴影
 */
interface Props {
  title?: string
  shadow?: 'always' | 'hover' | 'never'
}
withDefaults(defineProps<Props>(), {
  title: '',
  shadow: 'always',
})
</script>

<template>
  <div :class="['rt-card', `rt-card--${shadow}`]">
    <div v-if="title || $slots.header" class="rt-card__header">
      <slot name="header">
        <span class="rt-card__title">{{ title }}</span>
      </slot>
    </div>
    <div class="rt-card__body">
      <slot />
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '../style/variables.less';

.rt-card {
  background: @color-bg-card;
  border-radius: @radius-base;
  border: 1px solid @color-border;
  &--always {
    box-shadow: @shadow-card;
  }
  &--hover {
    transition: box-shadow 0.3s;
    &:hover {
      box-shadow: @shadow-hover;
    }
  }
  &--never {
    box-shadow: none;
  }
  &__header {
    padding: @spacing-md @spacing-lg;
    border-bottom: 1px solid @color-border;
  }
  &__title {
    font-size: @font-size-lg;
    font-weight: 600;
    color: @color-primary;
    border-left: 4px solid @color-primary;
    padding-left: @spacing-sm;
  }
  &__body {
    padding: @spacing-lg;
  }
}
</style>
