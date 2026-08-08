<script setup lang="ts">
/**
 * 全局公共导航
 * 三套前端共用顶部导航，支持路由高亮
 */
import { useRoute } from 'vue-router'
import { computed } from 'vue'

export interface NavItem {
  title: string
  path: string
  icon?: string
}
interface Props {
  items: NavItem[]
  title?: string
}
const props = withDefaults(defineProps<Props>(), { title: '红色文旅' })
const route = useRoute()
const activePath = computed(() => route.path)
</script>

<template>
  <nav class="rt-nav">
    <div class="rt-nav__brand">{{ title }}</div>
    <ul class="rt-nav__list">
      <li
        v-for="item in props.items"
        :key="item.path"
        :class="['rt-nav__item', { 'is-active': activePath.startsWith(item.path) }]"
      >
        <router-link :to="item.path">{{ item.title }}</router-link>
      </li>
    </ul>
  </nav>
</template>

<style lang="less" scoped>
@import '../style/variables.less';

.rt-nav {
  display: flex;
  align-items: center;
  height: 56px;
  padding: 0 @spacing-lg;
  background: @color-primary;
  color: #fff;
  &__brand {
    font-size: 18px;
    font-weight: 700;
    margin-right: @spacing-xl;
    letter-spacing: 1px;
  }
  &__list {
    display: flex;
    gap: @spacing-md;
  }
  &__item a {
    color: rgba(255, 255, 255, 0.85);
    font-size: @font-size-base;
    padding: 6px 12px;
    border-radius: @radius-sm;
    transition: all 0.2s;
    &:hover {
      color: #fff;
      background: rgba(255, 255, 255, 0.15);
    }
  }
  &__item.is-active a {
    color: #fff;
    background: @color-primary-active;
    font-weight: 600;
  }
}
</style>
