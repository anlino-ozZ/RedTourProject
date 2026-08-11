<script setup lang="ts">
/**
 * H5 底部导航栏
 * 四个 Tab：首页 / 导览 / 问答 / 我的
 * 路由驱动高亮，点击跳转对应路由
 */
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'

interface TabItem {
  title: string
  path: string
  // 激活态图标
  iconActive: string
  // 默认图标
  icon: string
}

// SVG 图标用 path 描边形式，激活态填充主色
const tabs: TabItem[] = [
  {
    title: '首页',
    path: '/',
    icon:
      'M3 12l9-9 9 9M5 10v10a1 1 0 001 1h12a1 1 0 001-1V10',
    iconActive:
      'M3 12l9-9 9 9M5 10v10a1 1 0 001 1h3v-6h6v6h3a1 1 0 001-1V10',
  },
  {
    title: '导览',
    path: '/guide',
    icon:
      'M12 2a8 8 0 00-8 8c0 5 8 12 8 12s8-7 8-12a8 8 0 00-8-8zM12 11a1.5 1.5 0 100 3 1.5 1.5 0 000-3z',
    iconActive:
      'M12 2a8 8 0 00-8 8c0 5 8 12 8 12s8-7 8-12a8 8 0 00-8-8zM12 11a1.5 1.5 0 100 3 1.5 1.5 0 000-3z',
  },
  {
    title: '问答',
    path: '/qa',
    icon:
      'M21 15a2 2 0 01-2 2H8l-4 4V5a2 2 0 012-2h13a2 2 0 012 2z',
    iconActive:
      'M21 15a2 2 0 01-2 2H8l-4 4V5a2 2 0 012-2h13a2 2 0 012 2z',
  },
  {
    title: '我的',
    path: '/profile',
    icon:
      'M12 12a4 4 0 100-8 4 4 0 000 8zM4 20c0-4 4-6 8-6s8 2 8 6',
    iconActive:
      'M12 12a4 4 0 100-8 4 4 0 000 8zM4 20c0-4 4-6 8-6s8 2 8 6',
  },
]

const route = useRoute()
const router = useRouter()

// 当前激活的 Tab：首页用精确匹配，其余用前缀匹配（方便子路由高亮）
const activePath = computed(() => {
  const path = route.path
  if (path === '/') return '/'
  // 匹配最长前缀
  const matched = tabs
    .filter((t) => t.path !== '/' && path.startsWith(t.path))
    .sort((a, b) => b.path.length - a.path.length)[0]
  return matched?.path ?? '/'
})

function onTab(item: TabItem) {
  if (route.path === item.path) return
  router.push(item.path)
}
</script>

<template>
  <nav class="tab-bar">
    <div
      v-for="item in tabs"
      :key="item.path"
      class="tab-bar__item"
      :class="{ 'is-active': activePath === item.path }"
      @click="onTab(item)"
    >
      <!-- 默认态：描边图标 -->
      <svg
        v-if="activePath !== item.path"
        class="tab-bar__icon"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path :d="item.icon" />
      </svg>
      <!-- 激活态：填充图标 -->
      <svg
        v-else
        class="tab-bar__icon"
        viewBox="0 0 24 24"
        fill="currentColor"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path :d="item.iconActive" />
      </svg>
      <span class="tab-bar__label">{{ item.title }}</span>
    </div>
  </nav>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.tab-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  height: 56px;
  background: @color-bg-card;
  border-top: 1px solid @color-border;
  // 适配 iOS 安全区
  padding-bottom: env(safe-area-inset-bottom);
  z-index: 100;

  &__item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    color: @color-text-secondary;
    cursor: pointer;
    transition: color 0.2s;

    &:active {
      background: @color-primary-light;
    }
    &.is-active {
      color: @color-primary;
    }
  }

  &__icon {
    width: 22px;
    height: 22px;
  }

  &__label {
    font-size: @font-size-sm;
    line-height: 1;
  }
}
</style>
