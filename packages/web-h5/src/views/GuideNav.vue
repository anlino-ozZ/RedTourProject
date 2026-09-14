<script setup lang="ts">
/**
 * 导览进行中页（离线地图 + 实时导航 + GPS 讲解）
 * - 从 Guide.vue 选定路线后进入，通过 query.routeId 传入路线
 * - mock 定位每 3 秒前进一点，模拟"正在前往"；到达景点自动弹出讲解卡
 * - 支持打卡印章、语音讲解播放控制、步行/驾车模式切换
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RedButton, RedDialog } from '@red-tour-project/common'
import {
  routes,
  spots,
  type GuideRoute,
  type GuideSpot,
} from '@/mock/guide'

const route = useRoute()
const router = useRouter()

const routeId = Number(route.query.routeId) || 1
const currentRoute = computed<GuideRoute>(
  () => routes.find((r) => r.id === routeId) ?? routes[0],
)

/** 该路线上的景点列表（按顺序） */
const routeSpots = computed<GuideSpot[]>(() =>
  currentRoute.value.spotIds
    .map((id) => spots.find((s) => s.id === id))
    .filter((s): s is GuideSpot => !!s),
)

// ===== 导航状态机 =====
/** 当前正在前往的景点索引（0 表示第一个） */
const currentIndex = ref(0)
/** 当前定位进度 0~1，表示从当前景点到下一景点的进度 */
const progress = ref(0)
/** 是否已开始导航 */
const navigating = ref(false)
/** 当前选中景点的打卡状态 */
const stampMap = ref<Record<number, boolean>>({})
/** GPS 触发的讲解弹窗 */
const lectureVisible = ref(false)

const currentSpot = computed(() => routeSpots.value[currentIndex.value])
const nextSpot = computed(() => routeSpots.value[currentIndex.value + 1] ?? null)
const remainingCount = computed(
  () => routeSpots.value.length - currentIndex.value - 1,
)

// 当前位置在地图上的坐标（起点=上一景点；未开始时=第一个景点）
const currentPos = computed(() => {
  const fromSp =
    currentIndex.value > 0 ? routeSpots.value[currentIndex.value - 1] : routeSpots.value[0]
  const toSp = routeSpots.value[currentIndex.value]
  if (!fromSp || !toSp) return { x: 50, y: 50 }
  const t = progress.value
  return {
    x: fromSp.x + (toSp.x - fromSp.x) * t,
    y: fromSp.y + (toSp.y - fromSp.y) * t,
  }
})

// ===== 模拟导航定时器 =====
let timer: ReturnType<typeof setInterval> | undefined

function startNav() {
  if (navigating.value) return
  navigating.value = true
  startTicker()
}
function pauseNav() {
  navigating.value = false
  stopTicker()
}
function stopTicker() {
  if (timer) clearInterval(timer)
  timer = undefined
}
function startTicker() {
  stopTicker()
  timer = setInterval(() => {
    if (!navigating.value) return
    progress.value += 0.015
    if (progress.value >= 1) {
      progress.value = 0
      // 到达当前景点：打卡 + 弹出讲解
      stampMap.value[currentSpot.value!.id] = true
      lectureVisible.value = true
      stopTicker()
    }
  }, 300)
}
function nextStop() {
  lectureVisible.value = false
  if (currentIndex.value >= routeSpots.value.length - 1) {
    // 整条路线完成
    router.push('/guide')
    return
  }
  currentIndex.value += 1
  progress.value = 0
  startTicker()
}
onBeforeUnmount(stopTicker)

// ===== 模式切换 =====
type TransMode = 'walk' | 'drive'
const mode = ref<TransMode>('walk')

// ===== 语音讲解 mock =====
const playing = ref(false)
function toggleAudio() {
  playing.value = !playing.value
  if (playing.value) {
    // mock：播放 3 秒后自动结束
    window.setTimeout(() => {
      playing.value = false
    }, 3000)
  }
}

// ===== 距离 & 时长（mock 计算） =====
const mockDistance = ref(450) // 米
const mockMinutes = ref(6)

/** 地图上景点间的连线坐标串（L + M） */
const pathD = computed(() => {
  const coords = routeSpots.value.map((s) => `${s.x},${s.y}`)
  if (coords.length === 0) return ''
  return 'M' + coords.join(' L')
})
</script>

<template>
  <div class="guide-nav">
    <!-- 顶部离线模式标识 -->
    <div class="guide-nav__offline">
      <span>📡 离线地图模式 · 无需网络即可导航</span>
    </div>

    <!-- 模拟地图 -->
    <div class="guide-nav__map">
      <!-- 装饰 -->
      <span class="guide-nav__map-road"></span>
      <span class="guide-nav__map-lake"></span>
      <span class="guide-nav__map-chip">{{ currentRoute.name }}</span>

      <!-- 路线虚线 -->
      <svg class="guide-nav__svg" viewBox="0 0 100 100" preserveAspectRatio="none">
        <path :d="pathD" stroke="#c41e3a" stroke-width="0.8" stroke-dasharray="2 1" fill="none" />
      </svg>

      <!-- 景点 pin（已完成=绿色对勾，进行中=红色脉冲） -->
      <button
        v-for="(sp, i) in routeSpots"
        :key="sp.id"
        type="button"
        class="guide-nav__pin"
        :class="{
          'is-done': !!stampMap[sp.id],
          'is-current': i === currentIndex && navigating,
        }"
        :style="{ left: `${sp.x}%`, top: `${sp.y}%` }"
        @click="currentIndex = i; progress = 0"
      >
        <span class="guide-nav__pin-label">
          {{ i + 1 }}.{{ sp.name }}
        </span>
        <span v-if="stampMap[sp.id]" class="guide-nav__pin-done">✓</span>
        <span v-else class="guide-nav__pin-dot"></span>
      </button>

      <!-- 我的位置（实时蓝点） -->
      <span
        class="guide-nav__me"
        :style="{ left: `${currentPos.x}%`, top: `${currentPos.y}%` }"
      >
        <span class="guide-nav__me-dot"></span>
      </span>
    </div>

    <!-- 路线进度条 -->
    <div class="guide-nav__progress">
      <span
        v-for="i in routeSpots.length"
        :key="i"
        class="guide-nav__progress-step"
        :class="{
          'is-done': i - 1 < currentIndex || (i - 1 === currentIndex && !!stampMap[routeSpots[i - 1].id]),
          'is-active': i - 1 === currentIndex,
        }"
      ></span>
    </div>

    <!-- 正在前往卡片 -->
    <div class="guide-nav__card">
      <div class="guide-nav__card-left">
        <div class="guide-nav__status">
          <span class="guide-nav__status-dot"></span>
          <span>{{ navigating ? '正在前往' : '即将前往' }}</span>
        </div>
        <h2 class="guide-nav__spot-name">{{ currentSpot?.name }}</h2>
        <p class="guide-nav__spot-meta">
          距您 {{ mockDistance }} 米 · 约 {{ mockMinutes }} 分钟
          <span v-if="nextSpot" class="guide-nav__spot-next">
            · 下一个 {{ nextSpot.name }}
          </span>
        </p>
      </div>
      <div class="guide-nav__card-right">
        <RedButton
          v-if="!navigating"
          type="primary"
          size="large"
          @click="startNav"
        >
          开始导航
        </RedButton>
        <RedButton
          v-else
          type="ghost"
          size="large"
          @click="pauseNav"
        >
          暂停
        </RedButton>
      </div>
    </div>

    <!-- 打卡印章 + 语音讲解 + 下个景点（横向三栏卡片） -->
    <div class="guide-nav__toolbar">
      <div class="guide-nav__tool">
        <div class="guide-nav__tool-top">
          <span class="guide-nav__tool-icon">{{ stampMap[currentSpot?.id ?? -1] ? '✅' : '🔖' }}</span>
          <span class="guide-nav__tool-title">打卡印章</span>
          <span v-if="currentSpot" class="guide-nav__tool-badge">{{ currentSpot.name.slice(0, 4) }}</span>
        </div>
        <button
          class="guide-nav__tool-sub"
          :class="{ 'is-done': stampMap[currentSpot?.id ?? -1] }"
          @click="
            currentSpot && (stampMap[currentSpot.id] = !stampMap[currentSpot.id])
          "
        >
          {{ stampMap[currentSpot?.id ?? -1] ? '已解锁' : '点击打卡' }}
        </button>
      </div>

      <div class="guide-nav__tool">
        <div class="guide-nav__tool-top">
          <span class="guide-nav__tool-icon">🎧</span>
          <span class="guide-nav__tool-title">语音讲解</span>
        </div>
        <button
          class="guide-nav__tool-sub"
          :class="{ 'is-playing': playing }"
          @click="toggleAudio"
        >
          {{ playing ? '播放中...' : '点击播放' }}
        </button>
      </div>

      <div class="guide-nav__tool">
        <div class="guide-nav__tool-top">
          <span class="guide-nav__tool-icon">→</span>
          <span class="guide-nav__tool-title">下个景点</span>
        </div>
        <button
          v-if="nextSpot"
          class="guide-nav__tool-sub"
          @click="currentIndex++"
        >
          {{ nextSpot.name }} · {{ mode === 'walk' ? '步行' : '驾车' }}
        </button>
        <span v-else class="guide-nav__tool-sub guide-nav__tool-sub--muted">终点</span>
      </div>
    </div>

    <!-- 返回 & 完成 -->
    <div class="guide-nav__bottom">
      <button class="guide-nav__back" @click="router.back()">← 退出导览</button>
      <button
        v-if="remainingCount === 0"
        class="guide-nav__finish"
        @click="router.push('/guide')"
      >
        🎉 路线完成
      </button>
    </div>

    <!-- ===== GPS 到达讲解弹窗 ===== -->
    <RedDialog
      :visible="lectureVisible"
      :title="`到达 · ${currentSpot?.name ?? ''}`"
      width="min(420px, 92vw)"
      @update:visible="(v: boolean) => (lectureVisible = v)"
    >
      <template #default>
        <div class="lecture">
          <p class="lecture__desc">{{ currentSpot?.desc }}</p>
          <div v-if="currentSpot?.audioLen" class="lecture__audio">
            <span>🎧 语音讲解</span>
            <span class="lecture__audio-len">
              约 {{ Math.floor(currentSpot.audioLen / 60) }} 分
              {{ currentSpot.audioLen % 60 }} 秒
            </span>
            <button class="lecture__play" @click="toggleAudio">
              {{ playing ? '暂停' : '播放' }}
            </button>
          </div>
          <p class="lecture__tip">
            您已自动打卡成功 ✅ · 继续前进将前往
            <strong>{{ nextSpot?.name ?? '终点' }}</strong>
          </p>
        </div>
      </template>
      <template #footer>
        <RedButton type="primary" size="medium" @click="nextStop">
          {{ remainingCount === 0 ? '完成导览' : '前往下一个景点 →' }}
        </RedButton>
      </template>
    </RedDialog>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.guide-nav {
  padding-bottom: 180px;

  // 离线标识
  &__offline {
    text-align: center;
    padding: 6px 0;
    background: fade(@color-primary, 6%);
    font-size: @font-size-sm;
    color: @color-primary;
    font-weight: 500;
    border-radius: @radius-base;
    margin-bottom: @spacing-sm;
  }

  // 地图
  &__map {
    position: relative;
    height: 280px;
    border-radius: @radius-lg;
    overflow: hidden;
    background:
      linear-gradient(rgba(255, 255, 255, 0.5) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 255, 255, 0.5) 1px, transparent 1px),
      linear-gradient(135deg, #e4efdc 0%, #d3e4c8 100%);
    background-size: 28px 28px, 28px 28px, 100% 100%;
    box-shadow: @shadow-card;
    margin-bottom: @spacing-md;
  }
  &__svg {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
  }
  &__map-chip {
    position: absolute;
    top: @spacing-sm;
    left: @spacing-sm;
    z-index: 2;
    padding: 3px 10px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.9);
    color: @color-text-regular;
    font-size: @font-size-sm;
  }
  &__map-road {
    position: absolute;
    width: 140%;
    height: 6px;
    top: 52%;
    left: -20%;
    background: rgba(255, 255, 255, 0.85);
    transform: rotate(-10deg);
  }
  &__map-lake {
    position: absolute;
    width: 90px;
    height: 56px;
    right: 6%;
    bottom: 6%;
    background: rgba(135, 188, 230, 0.55);
    border-radius: 46% 54% 60% 40% / 50% 44% 56% 50%;
  }

  // 景点 pin
  &__pin {
    position: absolute;
    transform: translate(-50%, -100%);
    padding: 0;
    border: none;
    background: transparent;
    z-index: 4;
    cursor: pointer;
    margin-bottom: -4px;

    &-dot {
      width: 16px;
      height: 16px;
      border-radius: 50% 50% 50% 0;
      transform: rotate(-45deg);
      background: #fff;
      border: 3px solid @color-primary;
      display: block;
    }
    &.is-current .guide-nav__pin-dot {
      background: @color-primary;
      animation: nav-pin-pulse 1.8s ease-out infinite;
    }
    &.is-done .guide-nav__pin-dot {
      background: #52c41a;
      border-color: #389e0d;
    }
    &-done {
      width: 16px;
      height: 16px;
      line-height: 12px;
      text-align: center;
      border-radius: 50%;
      background: #52c41a;
      color: #fff;
      font-size: 10px;
      border: 3px solid #fff;
      display: block;
    }
    &-label {
      display: inline-block;
      margin-top: 4px;
      padding: 2px 8px;
      border-radius: @radius-sm;
      background: rgba(255, 255, 255, 0.92);
      color: @color-text-regular;
      font-size: 11px;
      white-space: nowrap;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
    }
  }
  @keyframes nav-pin-pulse {
    0% { box-shadow: 0 0 0 0 rgba(196, 30, 58, 0.4); }
    70% { box-shadow: 0 0 0 10px rgba(196, 30, 58, 0); }
    100% { box-shadow: 0 0 0 0 rgba(196, 30, 58, 0); }
  }

  // 我的位置
  &__me {
    position: absolute;
    z-index: 5;
    transform: translate(-50%, -50%);
    pointer-events: none;
    &-dot {
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: #2f7cf6;
      border: 3px solid #fff;
      box-shadow: 0 0 0 4px rgba(47, 124, 246, 0.3);
    }
  }

  // 进度条
  &__progress {
    display: flex;
    justify-content: center;
    gap: 6px;
    margin-bottom: @spacing-md;
    &-step {
      width: 24px;
      height: 4px;
      border-radius: 2px;
      background: @color-border;
      transition: background 0.2s;
      &.is-done { background: @color-primary; }
      &.is-active {
        background: @color-primary;
        width: 32px;
      }
    }
  }

  // 正在前往卡片
  &__card {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: @spacing-md;
    padding: @spacing-md;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: @shadow-card;
    margin-bottom: @spacing-md;
  }
  &__card-left { flex: 1; min-width: 0; }
  &__status {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 2px 8px;
    border-radius: 10px;
    background: fade(@color-primary, 10%);
    color: @color-primary;
    font-size: @font-size-sm;
    font-weight: 500;
    margin-bottom: 4px;
  }
  &__status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: @color-primary;
    animation: nav-dot-blink 1.2s ease-in-out infinite;
  }
  @keyframes nav-dot-blink {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.3; }
  }
  &__spot-name {
    margin: 0 0 4px;
    font-size: 18px;
    font-weight: 700;
    color: @color-text-primary;
  }
  &__spot-meta {
    margin: 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    line-height: 1.5;
  }
  &__spot-next {
    color: @color-text-regular;
  }

  // 操作区：横向三栏卡片（打卡印章 / 语音讲解 / 下个景点）
  &__toolbar {
    display: flex;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: @shadow-card;
    margin-bottom: @spacing-md;
    overflow: hidden;
    border: 1px solid @color-border;
  }
  &__tool {
    flex: 1;
    padding: @spacing-sm @spacing-md;
    border-right: 1px solid @color-border;
    display: flex;
    flex-direction: column;
    gap: 6px;

    &:last-child { border-right: none; }
  }
  &__tool-top {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  &__tool-icon {
    font-size: 16px;
    line-height: 1;
  }
  &__tool-title {
    font-size: 13px;
    font-weight: 600;
    color: @color-text-primary;
  }
  &__tool-badge {
    margin-left: auto;
    padding: 1px 6px;
    border-radius: 8px;
    background: fade(@color-primary, 10%);
    color: @color-primary;
    font-size: 10px;
    font-weight: 500;
  }
  &__tool-sub {
    padding: 0;
    border: none;
    background: transparent;
    color: @color-text-secondary;
    font-size: 12px;
    text-align: left;
    cursor: pointer;
    font-weight: 500;
    display: inline-flex;
    align-items: center;
    gap: 3px;

    &.is-done { color: #52c41a; }
    &.is-playing { color: @color-primary; }
    &--muted { color: @color-text-secondary; cursor: default; }
  }

  &__bottom {
    position: fixed;
    left: 0;
    right: 0;
    bottom: calc(56px + 18px + 4px);
    padding: @spacing-sm @spacing-md;
    background: fade(@color-bg-card, 96%);
    box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.06);
    display: flex;
    justify-content: space-between;
    align-items: center;
    z-index: 90;
  }
  &__back {
    border: none;
    background: transparent;
    color: @color-text-secondary;
    font-size: @font-size-sm;
    cursor: pointer;
  }
  &__finish {
    border: none;
    background: #52c41a;
    color: #fff;
    padding: 8px 20px;
    border-radius: @radius-base;
    font-size: @font-size-base;
    font-weight: 600;
    cursor: pointer;
  }
}

// ===== 讲解弹窗 =====
.lecture {
  &__desc {
    margin: 0 0 @spacing-md;
    font-size: @font-size-base;
    color: @color-text-regular;
    line-height: 1.7;
  }
  &__audio {
    display: flex;
    align-items: center;
    gap: @spacing-md;
    padding: @spacing-md;
    background: @color-primary-light;
    border-radius: @radius-base;
    margin-bottom: @spacing-md;
    font-size: @font-size-sm;
  }
  &__audio-len {
    color: @color-text-secondary;
  }
  &__play {
    margin-left: auto;
    border: none;
    background: @color-primary;
    color: #fff;
    padding: 6px 14px;
    border-radius: @radius-base;
    font-size: @font-size-sm;
    cursor: pointer;
  }
  &__tip {
    margin: 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }
}
</style>
