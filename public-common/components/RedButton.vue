<script setup lang="ts">
/**
 * 全局公共红色按钮
 * 三套前端统一使用，保证视觉一致
 */
import { computed } from 'vue'

interface Props {
  type?: 'primary' | 'ghost' | 'text' // 主色 / 幽灵 / 文本
  size?: 'small' | 'medium' | 'large'
  disabled?: boolean
  loading?: boolean
}
const props = withDefaults(defineProps<Props>(), {
  type: 'primary',
  size: 'medium',
  disabled: false,
  loading: false,
})
const emit = defineEmits<{ (e: 'click', ev: MouseEvent): void }>()

const classes = computed(() => [
  'rt-red-btn',
  `rt-red-btn--${props.type}`,
  `rt-red-btn--${props.size}`,
  { 'is-disabled': props.disabled || props.loading },
])

function onClick(ev: MouseEvent) {
  if (props.disabled || props.loading) return
  emit('click', ev)
}
</script>

<template>
  <button :class="classes" :disabled="disabled || loading" @click="onClick">
    <span v-if="loading" class="rt-red-btn__loading"></span>
    <slot />
  </button>
</template>

<style lang="less" scoped>
@import '../style/variables.less';

.rt-red-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: @spacing-xs;
  border: 1px solid transparent;
  border-radius: @radius-base;
  font-size: @font-size-base;
  transition: all 0.2s ease;
  user-select: none;

  &--small {
    padding: 6px 14px;
    font-size: @font-size-sm;
  }
  &--medium {
    padding: 10px 20px;
  }
  &--large {
    padding: 14px 28px;
    font-size: @font-size-lg;
  }

  &--primary {
    background-color: @color-primary;
    color: #fff;
    &:hover {
      background-color: @color-primary-hover;
    }
    &:active {
      background-color: @color-primary-active;
    }
  }
  &--ghost {
    background-color: transparent;
    color: @color-primary;
    border-color: @color-primary;
    &:hover {
      background-color: @color-primary-light;
    }
  }
  &--text {
    background-color: transparent;
    color: @color-primary;
    &:hover {
      color: @color-primary-hover;
    }
  }

  &.is-disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
  &__loading {
    width: 14px;
    height: 14px;
    border: 2px solid currentColor;
    border-top-color: transparent;
    border-radius: 50%;
    animation: rt-spin 0.6s linear infinite;
  }
}
@keyframes rt-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
