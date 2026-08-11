<script setup lang="ts">
/**
 * H5 基础布局
 * 顶部可选 header + 中间可滚动内容区 + 底部 TabBar
 * 通过 slot 区分带/不带 TabBar 的页面
 */
import TabBar from '@/components/TabBar.vue'

interface Props {
  // 是否显示底部 TabBar（部分页面如登录可隐藏）
  showTabBar?: boolean
  // 顶部标题（留空则不显示 header）
  title?: string
}
withDefaults(defineProps<Props>(), {
  showTabBar: true,
  title: '',
})
</script>

<template>
  <div class="layout">
    <!-- 顶部标题栏 -->
    <header v-if="title" class="layout__header">
      <span class="layout__title">{{ title }}</span>
    </header>

    <!-- 内容区：可滚动 -->
    <main class="layout__main" :class="{ 'has-tabbar': showTabBar, 'has-header': title }">
      <slot />
    </main>

    <!-- 底部导航 -->
    <TabBar v-if="showTabBar" />
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: @color-bg-page;

  &__header {
    position: sticky;
    top: 0;
    z-index: 50;
    display: flex;
    align-items: center;
    justify-content: center;
    height: 48px;
    background: @color-primary;
    color: #fff;
    box-shadow: 0 2px 8px rgba(196, 30, 58, 0.2);
  }

  &__title {
    font-size: @font-size-lg;
    font-weight: 600;
    letter-spacing: 1px;
  }

  &__main {
    flex: 1;
    // 默认无 header 无 tabbar
    padding: @spacing-md;

    // 有底部 tabbar 时留出空间
    &.has-tabbar {
      padding-bottom: 72px; // 56px tabbar + 16px 间距
    }
    // 有顶部 header 时不需要额外顶部 padding
    &.has-header {
      padding-top: @spacing-sm;
    }
  }
}
</style>
