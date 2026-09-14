<script setup lang="ts">
// 游客移动端H5 首页
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { spots } from '@/mock/guide'

const router = useRouter()

// ===== 顶部 Banner 轮播 =====
interface BannerSlide {
  title: string
  subtitle: string
  /** 白色线性图标（24x24 stroke path 集合） */
  icon: string[]
  gradient: string
}

const banners: BannerSlide[] = [
  {
    title: '智能导览',
    subtitle: '一键开启红色研学之旅',
    gradient: 'linear-gradient(135deg, #c41e3a 0%, #8a1126 100%)',
    icon: [
      'M3 6l6-3 6 3 6-3v15l-6 3-6-3-6-3z',
      'M9 3v15',
      'M15 6v15',
      'M8 15l2.5-4 2.5 2 3-3.5',
    ],
  },
  {
    title: '红色剧本研学',
    subtitle: '沉浸式重温峥嵘岁月',
    gradient: 'linear-gradient(135deg, #ad1730 0%, #75101f 100%)',
    icon: [
      'M5 5h14',
      'M5 5a2 2 0 1 0 0.01 0z',
      'M19 5a2 2 0 1 0 0.01 0z',
      'M7 5v12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2V5',
      'M10 9h4',
      'M10 12h4',
      'M10 15h2',
    ],
  },
  {
    title: 'AI红色问答',
    subtitle: '探寻史料背后的故事',
    gradient: 'linear-gradient(135deg, #d92647 0%, #9e1629 100%)',
    icon: [
      'M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8z',
      'M8 11h.01M12 11h.01M16 11h.01',
    ],
  },
  {
    title: '打卡集章',
    subtitle: '把红色记忆带回家',
    gradient: 'linear-gradient(135deg, #bc1c37 0%, #6e0d1a 100%)',
    icon: [
      'M12 3a7 7 0 1 0 0 14 7 7 0 0 0 0-14z',
      'M12 6.9l1.05 2.12 2.34.34-1.7 1.65.4 2.34-2.09-1.1-2.09 1.1.4-2.34-1.7-1.65 2.34-.34z',
      'M9 17h6',
      'M10 17v3M14 17v3',
      'M8.5 21h7',
    ],
  },
]

const AUTOPLAY_MS = 3500
const current = ref(0)
const bannerEl = ref<HTMLElement | null>(null)
let timer: ReturnType<typeof setInterval> | undefined

// 拖拽状态（Pointer Events 同时兼容手指触摸与鼠标）
const dragging = ref(false)
const dragX = ref(0)
let startX = 0
let slideWidth = 1

function next() {
  current.value = (current.value + 1) % banners.length
}
function prev() {
  current.value = (current.value - 1 + banners.length) % banners.length
}
function go(i: number) {
  current.value = (i + banners.length) % banners.length
  restartAuto()
}
function startAuto() {
  stopAuto()
  timer = setInterval(next, AUTOPLAY_MS)
}
function stopAuto() {
  if (timer) clearInterval(timer)
}
function restartAuto() {
  startAuto()
}

function onPointerDown(e: PointerEvent) {
  stopAuto()
  dragging.value = true
  startX = e.clientX
  dragX.value = 0
  slideWidth = bannerEl.value?.clientWidth || 1
  bannerEl.value?.setPointerCapture?.(e.pointerId)
}
function onPointerMove(e: PointerEvent) {
  if (!dragging.value) return
  dragX.value = e.clientX - startX
}
function onPointerUp() {
  if (!dragging.value) return
  const threshold = Math.min(50, slideWidth * 0.18)
  const d = dragX.value
  dragging.value = false
  dragX.value = 0
  if (d <= -threshold) next()
  else if (d >= threshold) prev()
  startAuto()
}

/** 拖拽过程中让当前卡片跟手平移、相邻卡片跟手淡入；非拖拽时交由 CSS 淡入淡出 */
function slideStyle(i: number): Record<string, string> {
  if (!dragging.value || dragX.value === 0) return {}
  const d = dragX.value
  const w = slideWidth || 1
  if (i === current.value) {
    return {
      transform: `translate3d(${d}px, 0, 0)`,
      opacity: String(1 - Math.min(Math.abs(d) / w, 1) * 0.35),
      transition: 'none',
      zIndex: '3',
    }
  }
  const toNext = d < 0
  const neighbor = toNext
    ? (current.value + 1) % banners.length
    : (current.value - 1 + banners.length) % banners.length
  if (i === neighbor) {
    const progress = Math.min(Math.abs(d) / w, 1)
    return {
      opacity: String(progress),
      transform: `translate3d(${toNext ? d + w : d - w}px, 0, 0)`,
      transition: 'none',
      zIndex: '2',
    }
  }
  return { opacity: '0' }
}

onMounted(startAuto)
onBeforeUnmount(stopAuto)

// 热门景点（与导览地图共用 mock 数据，保证点击后定位到同一景点）
const hotSpots = spots

/** 跳转到导览地图视图；传入 spotId 时地图自动定位到该景点 */
function goToMap(spotId?: number) {
  router.push({
    path: '/guide',
    query: spotId ? { view: 'map', spotId: String(spotId) } : { view: 'map' },
  })
}

// ===== 首页功能入口四宫格（一行 4 个，见 Banner 下方） =====
interface QuickEntry {
  label: string
  path: string
  /** 图标背景色 */
  bg: string
  /** SVG path 描边 */
  icon: string[]
}

const quickEntries: QuickEntry[] = [
  {
    label: '智能导览',
    path: '/guide',
    bg: '#fde8ea',
    icon: [
      'M12 2a8 8 0 00-8 8c0 5 8 12 8 12s8-7 8-12a8 8 0 00-8-8z',
      'M12 11a1.5 1.5 0 100 3 1.5 1.5 0 000-3z',
    ],
  },
  {
    label: '智能问答',
    path: '/qa',
    bg: '#f6e8c5',
    icon: [
      'M21 15a2 2 0 01-2 2H8l-4 4V5a2 2 0 012-2h13a2 2 0 012 2z',
      'M8 10h8M8 14h5',
    ],
  },
  {
    label: '红色剧本',
    path: '/', // TODO 待剧本功能实现后替换
    bg: '#fdf3c4',
    icon: [
      'M5 5h14',
      'M5 5a2 2 0 100.01 0z',
      'M19 5a2 2 0 100.01 0z',
      'M7 5v12a2 2 0 002 2h6a2 2 0 002-2V5',
      'M10 9h4M10 12h4M10 15h2',
    ],
  },
  {
    label: '文创中心',
    path: '/products',
    bg: '#fde0e4',
    icon: [
      'M5 8h14l-1 12H6L5 8z',
      'M9 8V6a3 3 0 016 0v2',
    ],
  },
]

function goQuick(entry: QuickEntry) {
  if (entry.path === '/') {
    // 剧本研学入口暂未开发
    return
  }
  router.push(entry.path)
}

// ===== 大家都在问（热门问题列表） =====
interface HotQuestion {
  id: number
  text: string
  /** 热度标签文案 */
  heat: string
}

const hotQuestions: HotQuestion[] = [
  {
    id: 1,
    text: '1921年在这里发生了哪些重大历史转折？',
    heat: '🔥 热门',
  },
  {
    id: 2,
    text: '这件文物的材质和当时的生产背景是什么？',
    heat: '🔥 热门',
  },
]
</script>

<template>
  <div class="home">
    <!-- 顶部 Banner 轮播：自动播放 3.5s + 手指/鼠标拖拽 + 圆点指示 + 循环 + 淡入淡出 -->
    <div
      ref="bannerEl"
      class="home__banner"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
    >
      <div
        v-for="(slide, i) in banners"
        :key="i"
        class="home__banner-slide"
        :class="{ 'is-active': current === i }"
        :style="[{ background: slide.gradient }, slideStyle(i)]"
      >
        <!-- 装饰元素 -->
        <span class="home__banner-deco home__banner-deco--ring"></span>
        <span class="home__banner-deco home__banner-deco--star">★</span>

        <div class="home__banner-inner">
          <span class="home__banner-icon">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path v-for="(d, j) in slide.icon" :key="j" :d="d" />
            </svg>
          </span>
          <h2 class="home__banner-title">{{ slide.title }}</h2>
          <p class="home__banner-subtitle">{{ slide.subtitle }}</p>
        </div>
      </div>

      <!-- 底部小圆点分页指示器 -->
      <div class="home__banner-dots">
        <button
          v-for="(slide, i) in banners"
          :key="i"
          type="button"
          class="home__banner-dot"
          :class="{ 'is-active': current === i }"
          :aria-label="`第 ${i + 1} 张：${slide.title}`"
          @pointerdown.stop
          @click="go(i)"
        ></button>
      </div>
    </div>

    <!-- 功能入口四宫格（一行 4 个） -->
    <div class="home__quick">
      <div
        v-for="entry in quickEntries"
        :key="entry.label"
        class="home__quick-item"
        @click="goQuick(entry)"
      >
        <span class="home__quick-icon" :style="{ background: entry.bg }">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="#c41e3a"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path v-for="(d, j) in entry.icon" :key="j" :d="d" />
          </svg>
        </span>
        <span class="home__quick-label">{{ entry.label }}</span>
      </div>
    </div>

    <!-- 热门景点横向滚动 -->
    <div class="home__section">
      <div class="home__section-head">
        <span class="home__section-title">热门景点</span>
        <span class="home__section-more" @click="goToMap()">地图查看 ›</span>
      </div>
      <div class="home__spots-scroll">
        <div
          v-for="spot in hotSpots"
          :key="spot.id"
          class="home__spot-item"
          @click="goToMap(spot.id)"
        >
          <div class="home__spot-img">{{ spot.name.charAt(0) }}</div>
          <span class="home__spot-name">{{ spot.name }}</span>
          <span class="home__spot-tag">{{ spot.tag }}</span>
        </div>
      </div>
    </div>

    <!-- 大家都在问（热门问题列表） -->
    <div class="home__section">
      <div class="home__section-head">
        <span class="home__section-title">大家都在问</span>
      </div>
      <div class="home__qa-list">
        <div
          v-for="q in hotQuestions"
          :key="q.id"
          class="home__qa-item"
          @click="router.push('/qa')"
        >
          <span class="home__qa-q">?</span>
          <span class="home__qa-text">{{ q.text }}</span>
          <span class="home__qa-heat">{{ q.heat }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.home {
  overflow-x: hidden; // 防止负 margin 导致的横向溢出

  // ===== 顶部 Banner 轮播 =====
  &__banner {
    position: relative;
    margin: -@spacing-sm -@spacing-md @spacing-md;
    height: 172px;
    overflow: hidden;
    border-radius: 0 0 @radius-lg @radius-lg;
    touch-action: pan-y; // 纵向滚动交给页面，横向由轮播接管
    user-select: none;
    cursor: grab;
    &:active {
      cursor: grabbing;
    }
  }

  &__banner-slide {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    opacity: 0;
    transform: translate3d(0, 0, 0);
    transition: opacity 0.6s ease;
    z-index: 1;

    &.is-active {
      opacity: 1;
      z-index: 2;
    }
  }

  &__banner-inner {
    position: relative;
    z-index: 2;
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    padding: 0 @spacing-lg;
  }

  &__banner-icon {
    width: 52px;
    height: 52px;
    margin-bottom: @spacing-sm;
    border-radius: 50%;
    border: 1.5px solid rgba(255, 255, 255, 0.55);
    background: rgba(255, 255, 255, 0.1);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);

    svg {
      width: 27px;
      height: 27px;
    }
  }

  &__banner-title {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
    letter-spacing: 3px;
    text-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
  }

  &__banner-subtitle {
    margin: 6px 0 0;
    font-size: 13px;
    letter-spacing: 1px;
    opacity: 0.92;
  }

  // 背景装饰：虚线圆环 + 暗五角星
  &__banner-deco {
    position: absolute;
    z-index: 1;
    pointer-events: none;

    &--ring {
      width: 190px;
      height: 190px;
      right: -66px;
      top: -78px;
      border-radius: 50%;
      border: 1.5px dashed rgba(255, 255, 255, 0.2);
    }
    &--star {
      font-size: 130px;
      line-height: 1;
      left: -18px;
      bottom: -52px;
      color: rgba(255, 255, 255, 0.07);
      transform: rotate(-12deg);
    }
  }

  // 底部小圆点分页指示器
  &__banner-dots {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 10px;
    z-index: 4;
    display: flex;
    justify-content: center;
    gap: 6px;
  }

  &__banner-dot {
    width: 6px;
    height: 6px;
    padding: 0;
    border: none;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.5);
    cursor: pointer;
    transition: width 0.3s ease, background 0.3s ease;

    &.is-active {
      width: 18px;
      border-radius: 3px;
      background: #fff;
    }
  }

  // 通用区块
  &__section {
    margin-bottom: @spacing-md;
  }
  &__section-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: @spacing-sm;
  }
  &__section-title {
    font-size: @font-size-lg;
    font-weight: 600;
    color: @color-text-primary;
    border-left: 3px solid @color-primary;
    padding-left: @spacing-sm;
  }
  &__section-more {
    font-size: @font-size-sm;
    color: @color-primary;
    cursor: pointer;
  }

  // 热门景点横向滚动
  &__spots-scroll {
    display: flex;
    gap: @spacing-sm;
    overflow-x: auto;
    padding-bottom: @spacing-sm;
    -webkit-overflow-scrolling: touch;
    &::-webkit-scrollbar {
      display: none;
    }
  }
  &__spot-item {
    flex-shrink: 0;
    width: 120px;
    cursor: pointer;
    text-align: center;
  }
  &__spot-img {
    width: 120px;
    height: 90px;
    border-radius: @radius-base;
    background: linear-gradient(135deg, @color-primary 0%, @color-primary-active 100%);
    margin-bottom: @spacing-sm;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 24px;
    font-weight: 700;
    opacity: 0.85;
  }
  &__spot-name {
    display: block;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
    margin-bottom: 2px;
    text-align: center;
  }
  &__spot-tag {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    text-align: center;
  }

  // 功能入口四宫格（一行 4 个）
  &__quick {
    display: flex;
    justify-content: space-between;
    margin: @spacing-sm 0 @spacing-md;
    padding: 0;
    gap: 8px;
  }

  &__quick-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    flex: 1;
    cursor: pointer;
  }

  &__quick-icon {
    width: 60px;
    height: 60px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: transform 0.15s;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);

    svg {
      width: 30px;
      height: 30px;
    }

    &:active {
      transform: scale(0.94);
    }
  }

  &__quick-label {
    font-size: @font-size-sm;
    color: @color-text-primary;
    font-weight: 500;
  }

  // 大家都在问
  &__qa-list {
    display: flex;
    flex-direction: column;
    gap: @spacing-sm;
  }

  &__qa-item {
    display: flex;
    align-items: center;
    gap: @spacing-md;
    padding: @spacing-md;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: @shadow-card;
    cursor: pointer;
    transition: transform 0.1s;

    &:active {
      transform: scale(0.99);
    }
  }

  &__qa-q {
    flex-shrink: 0;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: @color-primary-light;
    color: @color-primary;
    font-size: 18px;
    font-weight: 700;
    line-height: 32px;
    text-align: center;
  }

  &__qa-text {
    flex: 1;
    font-size: @font-size-base;
    color: @color-text-primary;
    line-height: 1.5;
    // 单行省略
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__qa-heat {
    flex-shrink: 0;
    font-size: @font-size-sm;
    color: #e0552b;
    font-weight: 600;
  }
}
</style>
