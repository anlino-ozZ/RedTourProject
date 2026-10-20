<script setup lang="ts">
/**
 * 管理后台内置轻量图标（线性 SVG，24x24）
 * 不引入额外图标库依赖，统一 currentColor 描边，随文字颜色变化
 */
import { computed } from 'vue'

const ICONS: Record<string, string[]> = {
  // 概览
  grid: [
    'M4 4h7v7H4z',
    'M13 4h7v7h-7z',
    'M4 13h7v7H4z',
    'M13 13h7v7h-7z',
  ],
  // 景点
  'map-pin': [
    'M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z',
    'M12 7a3 3 0 1 0 0 6 3 3 0 0 0 0-6',
  ],
  // 路线
  map: ['M9 3 1 6v15l8-3 6 3 8-3V3l-8 3z', 'M9 3v15', 'M15 6v15'],
  // 打卡点
  flag: ['M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z', 'M4 22v-7'],
  // 特产文创
  gift: [
    'M20 12v10H4V12',
    'M2 7h20v5H2z',
    'M12 22V7',
    'M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7z',
    'M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z',
  ],
  // 订单
  'file-text': [
    'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z',
    'M14 2v6h6',
    'M16 13H8',
    'M16 17H8',
    'M10 9H8',
  ],
  // 文物
  archive: ['M21 8v13H3V8', 'M1 3h22v5H1z', 'M10 12h4'],
  // 知识库
  book: ['M4 19.5A2.5 2.5 0 0 1 6.5 17H20', 'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'],
  // 剧本
  edit: ['M12 20h9', 'M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4z'],
  // 成就
  award: ['M12 15a6 6 0 1 0 0-12 6 6 0 0 0 0 12z', 'M8.21 13.89 7 23l5-3 5 3-1.21-9.12'],
  // 用户
  users: [
    'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2',
    'M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8',
    'M23 21v-2a4 4 0 0 0-3-3.87',
    'M16 3.13a4 4 0 0 1 0 7.75',
  ],
  // 数据统计
  chart: ['M12 20V10', 'M18 20V4', 'M6 20v-4'],
  // 设备监控
  monitor: ['M3 3h18v14H3z', 'M8 21h8', 'M12 17v4'],
  // 通用
  menu: ['M3 6h18', 'M3 12h18', 'M3 18h18'],
  user: ['M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2', 'M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8'],
  lock: ['M5 11h14v10H5z', 'M8 11V7a4 4 0 0 1 8 0v4'],
  'chevron-left': ['M15 18l-6-6 6-6'],
  'chevron-right': ['M9 18l6-6-6-6'],
  'chevron-down': ['M6 9l6 6 6-6'],
  logout: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'M16 17l5-5-5-5', 'M21 12H9'],
  arrowRight: ['M5 12h14', 'M13 6l6 6-6 6'],
  sparkles: ['M12 3v6', 'M12 15v6', 'M3 12h6', 'M15 12h6', 'M7 7l3 3', 'M14 14l3 3', 'M17 7l-3 3', 'M10 14l-3 3'],
}

interface Props {
  name: string
  size?: number | string
  strokeWidth?: number | string
}
const props = withDefaults(defineProps<Props>(), { size: 18, strokeWidth: 1.8 })

const paths = computed(() => ICONS[props.name] || ICONS.grid)
</script>

<template>
  <svg
    class="menu-icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="props.strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(d, idx) in paths" :key="idx" :d="d" />
  </svg>
</template>

<style scoped>
.menu-icon {
  display: block;
  flex: none;
}
</style>
