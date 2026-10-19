<script setup lang="ts">
// 游客移动端H5 首页
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getSpots, type GuideSpot } from '@/api/spots'
import { getRecommendations } from '@/api/qa'
import { applyCoverFallback, hideBrokenImage } from '@/utils/imageFallback'

const router = useRouter()

// ===== 顶部 Banner 轮播 =====
interface BannerSlide {
  title: string
  subtitle: string
  /** 文旅宣传实景照片；加载失败时以 gradient 兜底 */
  image: string
  gradient: string
}

const banners: BannerSlide[] = [
  {
    title: '智能导览',
    subtitle: '一键开启红色研学之旅',
    gradient: 'linear-gradient(135deg, #c41e3a 0%, #8a1126 100%)',
    image:
      'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=tourism%20promotional%20photo%20of%20Jinggangshan%20mountains%20China%2C%20sea%20of%20clouds%20over%20lush%20green%20peaks%20at%20sunrise%2C%20Huangyangjie%20viewpoint%2C%20majestic%20landscape%2C%20travel%20poster%20quality&image_size=landscape_16_9',
  },
  {
    title: '红色剧本研学',
    subtitle: '沉浸式重温峥嵘岁月',
    gradient: 'linear-gradient(135deg, #ad1730 0%, #75101f 100%)',
    image:
      'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=tourism%20promotional%20photo%20of%20the%20Red%20Boat%20on%20South%20Lake%20Jiaxing%20China%2C%20traditional%20wooden%20boat%20on%20calm%20misty%20water%2C%20historic%20site%2C%20warm%20morning%20light%2C%20travel%20poster%20quality&image_size=landscape_16_9',
  },
  {
    title: 'AI红色问答',
    subtitle: '探寻史料背后的故事',
    gradient: 'linear-gradient(135deg, #d92647 0%, #9e1629 100%)',
    image:
      'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=tourism%20promotional%20photo%20of%20Yanan%20Pagoda%20Hill%20at%20dusk%2C%20illuminated%20ancient%20pagoda%20on%20the%20hill%2C%20red%20tourism%20landmark%20in%20Shaanxi%20China%2C%20travel%20poster%20quality&image_size=landscape_16_9',
  },
  {
    title: '打卡集章',
    subtitle: '把红色记忆带回家',
    gradient: 'linear-gradient(135deg, #bc1c37 0%, #6e0d1a 100%)',
    image:
      'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=tourism%20promotional%20photo%20of%20Zunyi%20Conference%20Site%2C%20historic%20grey%20brick%20building%20with%20courtyard%2C%20red%20tourism%20landmark%20in%20Guizhou%20China%2C%20blue%20sky%2C%20travel%20poster%20quality&image_size=landscape_16_9',
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

onMounted(() => {
  startAuto()
  refreshHotQuestions()
  qaTimer = setInterval(refreshHotQuestions, QA_REFRESH_MS)
})
onBeforeUnmount(() => {
  stopAuto()
  if (qaTimer) clearInterval(qaTimer)
})

// 热门景点（与导览地图共用数据源，保证点击后定位到同一景点）
const hotSpots = ref<GuideSpot[]>([])
// 首页热门景点：与导览/推荐共用默认景区，保证景点 ID 和跳转定位一致
getSpots()
  .then(({ list }) => (hotSpots.value = list))

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
    path: '/interactive/scripts',
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
  router.push(entry.path)
}

// ===== 大家都在问（热门问题列表） =====
interface HotQuestion {
  id: number
  text: string
  /** 热度标签文案 */
  heat: string
}

// 接口不可用时的兜底展示
const hotQuestions = ref<HotQuestion[]>([
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
])

/** 推荐问题自动刷新间隔（30s），与问答页推荐接口同源 */
const QA_REFRESH_MS = 30000
let qaTimer: ReturnType<typeof setInterval> | undefined

/** 拉取后端推荐问题；失败或为空时保留当前列表静默兜底 */
async function refreshHotQuestions() {
  try {
    const list = await getRecommendations(undefined, { silent: true })
    if (list.length) {
      hotQuestions.value = list
        .slice(0, 3)
        .map((text, i) => ({ id: i + 1, text, heat: '🔥 热门' }))
    }
  } catch {
    // 后端未启动/网络异常时忽略，继续展示现有列表
  }
}
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
        <!-- 实景照片 + 深色蒙层（保证文字可读） -->
        <img class="home__banner-photo" :src="slide.image" :alt="slide.title" @error="applyCoverFallback" />
        <span class="home__banner-scrim"></span>

        <div class="home__banner-inner">
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
          <div class="home__spot-img">
            {{ spot.name.charAt(0) }}
            <img
              v-if="spot.image"
              :src="spot.image"
              :alt="spot.name"
              @error="hideBrokenImage"
              loading="lazy"
            />
          </div>
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
          @click="router.push({ path: '/qa', query: { q: q.text } })"
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

  // 首屏四个区块错峰淡入上浮（Banner → 四宫格 → 热门景点 → 大家都在问）
  > * {
    animation: home-rise 0.45s cubic-bezier(0.22, 0.8, 0.36, 1) both;
  }
  > *:nth-child(2) {
    animation-delay: 0.06s;
  }
  > *:nth-child(3) {
    animation-delay: 0.12s;
  }
  > *:nth-child(4) {
    animation-delay: 0.18s;
  }

  @media (prefers-reduced-motion: reduce) {
    > * {
      animation: none;
    }
  }

  // ===== 顶部 Banner 轮播（圆角卡片） =====
  &__banner {
    position: relative;
    margin: 0 0 @spacing-md;
    height: 180px;
    overflow: hidden;
    border-radius: @radius-lg;
    box-shadow: 0 6px 20px rgba(196, 30, 58, 0.18);
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

  // 实景照片（铺满）与深色渐变蒙层
  &__banner-photo {
    position: absolute;
    inset: 0;
    z-index: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    pointer-events: none;
  }

  &__banner-scrim {
    position: absolute;
    inset: 0;
    z-index: 1;
    background: linear-gradient(
      180deg,
      rgba(0, 0, 0, 0.15) 0%,
      rgba(0, 0, 0, 0.12) 45%,
      rgba(0, 0, 0, 0.55) 100%
    );
    pointer-events: none;
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

  &__banner-title {
    margin: 0;
    font-size: 24px;
    font-weight: 700;
    letter-spacing: 3px;
    text-shadow: 0 2px 10px rgba(0, 0, 0, 0.45);
  }

  &__banner-subtitle {
    margin: 6px 0 0;
    font-size: 13px;
    letter-spacing: 1px;
    opacity: 0.95;
    text-shadow: 0 1px 6px rgba(0, 0, 0, 0.4);
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
    transition: transform 0.15s;

    &:active {
      transform: scale(0.96);
    }
  }
  &__spot-img {
    position: relative;
    width: 120px;
    height: 90px;
    border-radius: @radius-lg;
    background: linear-gradient(135deg, @color-primary 0%, @color-primary-active 100%);
    margin-bottom: @spacing-sm;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 24px;
    font-weight: 700;
    opacity: 0.85;
    overflow: hidden;
    box-shadow: @shadow-card;

    // 实景照片覆盖在渐变兜底之上；加载失败时保留首字符占位
    img {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
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

  // 功能入口四宫格（一行 4 个，白底卡片容器）
  &__quick {
    display: flex;
    justify-content: space-between;
    margin: 0 0 @spacing-md;
    padding: @spacing-md @spacing-sm;
    gap: 8px;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: @shadow-card;
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

// 首屏区块入场：下移 14px + 透明 → 归位
@keyframes home-rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
