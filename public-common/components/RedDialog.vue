<script setup lang="ts">
/**
 * 全局公共弹窗
 * 基于 v-model:visible 双向绑定，统一样式
 */
interface Props {
  visible: boolean
  title?: string
  width?: string
}
const props = withDefaults(defineProps<Props>(), {
  title: '',
  width: '480px',
})
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void }>()

function close() {
  emit('update:visible', false)
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="rt-dialog-mask" @click.self="close">
      <div class="rt-dialog" :style="{ width }">
        <div class="rt-dialog__header">
          <span class="rt-dialog__title">{{ title }}</span>
          <span class="rt-dialog__close" @click="close">×</span>
        </div>
        <div class="rt-dialog__body">
          <slot />
        </div>
        <div v-if="$slots.footer" class="rt-dialog__footer">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style lang="less" scoped>
@import '../style/variables.less';

.rt-dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}
.rt-dialog {
  background: #fff;
  border-radius: @radius-lg;
  box-shadow: @shadow-card;
  overflow: hidden;
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: @spacing-md @spacing-lg;
    border-bottom: 1px solid @color-border;
  }
  &__title {
    font-size: @font-size-lg;
    font-weight: 600;
    color: @color-primary;
  }
  &__close {
    font-size: 22px;
    cursor: pointer;
    color: @color-text-secondary;
  }
  &__body {
    padding: @spacing-lg;
  }
  &__footer {
    padding: @spacing-md @spacing-lg;
    border-top: 1px solid @color-border;
    text-align: right;
  }
}
</style>
