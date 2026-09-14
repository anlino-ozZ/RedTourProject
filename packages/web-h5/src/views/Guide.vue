<script setup lang="ts">
// 智能导览（H-04 地图 + 路线列表 + AI 推荐入口）
// AI 个性化推荐已拆到独立路由 /guide/recommend
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RedButton } from '@red-tour-project/common'
import { routes, spots, type GuideRoute } from '@/mock/guide'

const route = useRoute()
const router = useRouter()

const view = ref<'map' | 'list'>(
  route.query.view === 'list' ? 'list' : 'map',
)
const sortType = ref<'recommend' | 'distance'>('recommend')
const selectedSpotId = ref<number>(
  route.query.spotId ? Number(route.query.spotId) : spots[0].id,
)
const selectedId = ref<number | null>(null)

const selectedSpot = computed(
  () => spots.find((s) => s.id === selectedSpotId.value) ?? spots[0],
)
const spotRoutes = computed(() =>
  routes.filter((r) => r.spotIds.includes(selectedSpotId.value)),
)
const sortedRoutes = computed(() => {
  if (sortType.value === 'distance') {
    return [...routes].sort((a, b) => a.distance - b.distance)
  }
  return [...routes].sort(
    (a, b) => (b.tag === '推荐' ? 1 : 0) - (a.tag === '推荐' ? 1 : 0),
  )
})
const selectedRoute = computed(() => {
  if (!selectedId.value) return null
  return (
    routes.find((r) => r.id === selectedId.value) as GuideRoute | undefined
  ) ?? null
})
const hottestRouteId = computed(
  () => [...routes].sort((a, b) => b.visitCount - a.visitCount)[0]?.id,
)

function stars(difficulty: number): string {
  return '★'.repeat(difficulty) + '☆'.repeat(3 - difficulty)
}
function formatVisits(n: number): string {
  if (n >= 10000) return `${(n / 10000).toFixed(1)} 万人游览过`
  return `${n.toLocaleString()} 人游览过`
}

function selectRoute(id: number) {
  selectedId.value = selectedId.value === id ? null : id
}

function startGuide() {
  if (!selectedRoute.value) return
  router.push({
    path: '/guide/navigate',
    query: { routeId: String(selectedRoute.value.id) },
  })
}

function goRecommend() {
  router.push('/guide/recommend')
}
</script>

<template>
  <div class="guide">
    <!-- 视图切换 -->
    <div class="guide__switch">
      <button
        class="guide__switch-item"
        :class="{ 'is-active': view === 'map' }"
        @click="view = 'map'"
      >
        地图
      </button>
      <button
        class="guide__switch-item"
        :class="{ 'is-active': view === 'list' }"
        @click="view = 'list'"
      >
        路线列表
      </button>
    </div>

    <!-- 地图视图 -->
    <template v-if="view === 'map'">
      <div class="guide__map-canvas">
        <span class="guide__map-road guide__map-road--1"></span>
        <span class="guide__map-road guide__map-road--2"></span>
        <span class="guide__map-lake"></span>
        <span class="guide__map-chip">井冈山革命根据地 · 景区示意图</span>
        <span class="guide__map-me">
          <span class="guide__map-me-dot"></span>
          <span class="guide__map-me-label">我的位置</span>
        </span>
        <button
          v-for="spot in spots"
          :key="spot.id"
          type="button"
          class="guide__map-pin"
          :class="{ 'is-active': selectedSpotId === spot.id }"
          :style="{ left: `${spot.x}%`, top: `${spot.y}%` }"
          @click="selectedSpotId = spot.id"
        >
          <span class="guide__map-pin-dot"></span>
          <span class="guide__map-pin-name">{{ spot.name }}</span>
        </button>
      </div>

      <div class="guide__spot-panel">
        <div class="guide__spot-head">
          <h3 class="guide__spot-name">
            {{ selectedSpot.name }}
            <span class="guide__spot-tag">{{ selectedSpot.tag }}</span>
          </h3>
          <p class="guide__spot-desc">{{ selectedSpot.desc }}</p>
        </div>

        <p class="guide__spot-sub">
          该景点包含的导览路线（{{ spotRoutes.length }}）
        </p>

        <div
          v-for="item in spotRoutes"
          :key="item.id"
          class="guide__spot-route"
          :class="{ 'is-selected': selectedId === item.id }"
        >
          <div class="guide__spot-route-main" @click="selectRoute(item.id)">
            <div class="guide__spot-route-head">
              <h4 class="guide__spot-route-name">{{ item.name }}</h4>
              <span v-if="item.id === hottestRouteId" class="guide__spot-route-hot">🔥 热门</span>
            </div>
            <div class="guide__spot-route-meta">
              <span>{{ stars(item.difficulty) }}</span>
              <span>约 {{ item.duration }} 分钟</span>
              <span>距您 {{ item.distance }}km</span>
            </div>
            <p class="guide__spot-route-visits">{{ formatVisits(item.visitCount) }}</p>
          </div>
          <button
            class="guide__spot-route-go"
            @click.stop="selectedId = item.id; startGuide()"
          >
            开始导览
          </button>
          <span v-if="selectedId === item.id" class="guide__spot-route-check">✓</span>
        </div>

        <div v-if="spotRoutes.length === 0" class="guide__spot-empty">
          暂无途经该景点的导览路线
        </div>
      </div>
    </template>

    <!-- 路线列表视图 -->
    <template v-else>
      <div class="guide__sort">
        <span class="guide__sort-label">排序</span>
        <button
          class="guide__sort-item"
          :class="{ 'is-active': sortType === 'recommend' }"
          @click="sortType = 'recommend'"
        >
          推荐优先
        </button>
        <button
          class="guide__sort-item"
          :class="{ 'is-active': sortType === 'distance' }"
          @click="sortType = 'distance'"
        >
          距离优先
        </button>
      </div>

      <p class="guide__hint">请选择一条导览路线（点击卡片选中）</p>

      <div
        v-for="item in sortedRoutes"
        :key="item.id"
        class="guide__card"
        :class="{ 'is-selected': selectedId === item.id }"
      >
        <span v-if="selectedId === item.id" class="guide__card-check">✓</span>
        <div class="guide__card-main" @click="selectRoute(item.id)">
          <div class="guide__card-head">
            <h3 class="guide__card-name">{{ item.name }}</h3>
            <span class="guide__card-tag">{{ item.tag }}</span>
            <span v-if="item.id === hottestRouteId" class="guide__card-hot">🔥 热门</span>
          </div>
          <p class="guide__card-desc">{{ item.desc }}</p>
          <div class="guide__card-meta">
            <span class="guide__card-stars">{{ stars(item.difficulty) }}</span>
            <span class="guide__card-duration">约 {{ item.duration }} 分钟</span>
            <span class="guide__card-spots">{{ item.spotCount }} 个景点</span>
            <span class="guide__card-distance">距您 {{ item.distance }}km</span>
          </div>
          <p class="guide__card-visits">🔥 {{ formatVisits(item.visitCount) }}</p>
        </div>
        <button
          class="guide__card-go"
          @click.stop="selectedId = item.id; startGuide()"
        >
          开始导览
        </button>
      </div>
    </template>

    <!-- ===== AI 推荐独立入口（路由跳转，不展开面板） ===== -->
    <button class="guide__ai-entry" @click="goRecommend">
      <span class="guide__ai-entry-icon">🤖</span>
      <span class="guide__ai-entry-text">AI 为我推荐专属路线</span>
      <span class="guide__ai-entry-arrow">→</span>
    </button>

    <!-- 底部固定操作栏（地图/列表选中路线后） -->
    <div class="guide__footer">
      <RedButton
        type="primary"
        size="large"
        :disabled="!selectedRoute"
        @click="startGuide"
      >
        {{ selectedRoute ? '开始导览' : '请先选择路线' }}
      </RedButton>
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.guide {
  &__switch {
    display: flex;
    background: @color-border;
    border-radius: @radius-base;
    padding: 4px;
    margin-bottom: @spacing-md;
  }
  &__switch-item {
    flex: 1;
    height: 36px;
    border: none;
    border-radius: @radius-base;
    background: transparent;
    color: @color-text-regular;
    font-size: @font-size-base;
    transition: all 0.2s;

    &.is-active {
      background: @color-bg-card;
      color: @color-primary;
      font-weight: 600;
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
    }
  }

  // 模拟地图
  &__map-canvas {
    position: relative;
    height: 264px;
    border-radius: @radius-lg;
    overflow: hidden;
    margin-bottom: @spacing-md;
    background:
      linear-gradient(rgba(255, 255, 255, 0.5) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 255, 255, 0.5) 1px, transparent 1px),
      linear-gradient(135deg, #e4efdc 0%, #d3e4c8 100%);
    background-size: 28px 28px, 28px 28px, 100% 100%;
    box-shadow: @shadow-card;
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
    &--2 { top: 30%; transform: rotate(18deg); width: 80%; left: 20%; }
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
  &__map-me {
    position: absolute;
    left: 48%;
    top: 82%;
    z-index: 3;
    transform: translate(-50%, -50%);
  }
  &__map-me-dot {
    display: block;
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: #2f7cf6;
    border: 3px solid #fff;
    box-shadow: 0 0 0 4px rgba(47, 124, 246, 0.3);
    margin: 0 auto;
  }
  &__map-me-label {
    display: block;
    margin-top: 2px;
    padding: 1px 6px;
    border-radius: @radius-sm;
    background: rgba(255, 255, 255, 0.92);
    color: @color-text-regular;
    font-size: 11px;
    white-space: nowrap;
    text-align: center;
  }
  &__map-pin {
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
    &.is-active .guide__map-pin-dot {
      background: @color-primary;
      animation: guide-pin-pulse 1.8s ease-out infinite;
    }
    &-name {
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
  @keyframes guide-pin-pulse {
    0% { box-shadow: 0 0 0 0 rgba(196, 30, 58, 0.4); }
    70% { box-shadow: 0 0 0 10px rgba(196, 30, 58, 0); }
    100% { box-shadow: 0 0 0 0 rgba(196, 30, 58, 0); }
  }

  // 景点信息面板
  &__spot-panel {
    background: @color-bg-card;
    border-radius: @radius-lg;
    padding: @spacing-md;
    box-shadow: @shadow-card;
    margin-bottom: @spacing-md;
  }
  &__spot-head {
    margin-bottom: @spacing-sm;
  }
  &__spot-name {
    margin: 0 0 4px;
    font-size: 18px;
    font-weight: 700;
    color: @color-text-primary;
  }
  &__spot-tag {
    margin-left: @spacing-sm;
    padding: 2px 8px;
    border-radius: 4px;
    background: fade(@color-primary, 10%);
    color: @color-primary;
    font-size: @font-size-sm;
    font-weight: 500;
  }
  &__spot-desc {
    margin: 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    line-height: 1.6;
  }
  &__spot-sub {
    margin: @spacing-sm 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }
  &__spot-route {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    padding: @spacing-sm;
    margin-bottom: @spacing-sm;
    border: 1.5px solid @color-border;
    border-radius: @radius-base;
    transition: border-color 0.2s;

    &.is-selected {
      border-color: @color-primary;
      background: @color-primary-light;
    }
  }
  &__spot-route-main {
    flex: 1;
    min-width: 0;
    cursor: pointer;
  }
  &__spot-route-head {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    margin-bottom: 2px;
  }
  &__spot-route-name {
    margin: 0;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
  }
  &__spot-route-hot {
    font-size: 11px;
    color: #e8601c;
  }
  &__spot-route-meta {
    display: flex;
    gap: @spacing-md;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }
  &__spot-route-visits {
    margin: 4px 0 0;
    font-size: @font-size-sm;
    color: #e8601c;
  }
  &__spot-route-go {
    flex-shrink: 0;
    align-self: center;
    padding: 6px 14px;
    border: none;
    border-radius: @radius-base;
    background: @color-primary;
    color: #fff;
    font-size: @font-size-sm;
    font-weight: 600;
    cursor: pointer;
  }
  &__spot-route-check {
    flex-shrink: 0;
    width: 22px;
    height: 22px;
    line-height: 22px;
    text-align: center;
    border-radius: 50%;
    background: @color-primary;
    color: #fff;
    font-size: 13px;
    font-weight: bold;
  }
  &__spot-empty {
    padding: @spacing-lg 0;
    text-align: center;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  // 排序 + 列表卡片
  &__sort {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    margin-bottom: @spacing-sm;
  }
  &__sort-label {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }
  &__sort-item {
    padding: 4px 12px;
    border: 1px solid @color-border;
    border-radius: 14px;
    background: transparent;
    font-size: @font-size-sm;
    color: @color-text-regular;
    cursor: pointer;

    &.is-active {
      border-color: @color-primary;
      color: @color-primary;
      background: fade(@color-primary, 6%);
    }
  }
  &__hint {
    margin: 0 0 @spacing-md;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__card {
    position: relative;
    display: flex;
    align-items: stretch;
    gap: @spacing-md;
    background: @color-bg-card;
    border-radius: @radius-lg;
    padding: @spacing-md;
    margin-bottom: @spacing-md;
    border: 2px solid transparent;
    box-shadow: @shadow-card;
    cursor: pointer;
    transition: border-color 0.2s, box-shadow 0.2s, transform 0.15s;

    &:active { transform: scale(0.99); }
    &.is-selected {
      border-color: @color-primary;
      box-shadow: 0 4px 14px rgba(196, 30, 58, 0.18);
    }
  }
  &__card-go {
    flex-shrink: 0;
    align-self: center;
    height: 36px;
    padding: 0 16px;
    border: none;
    border-radius: @radius-base;
    background: @color-primary;
    color: #fff;
    font-size: @font-size-sm;
    font-weight: 600;
    cursor: pointer;
  }
  &__card-check {
    position: absolute;
    top: 10px;
    right: 10px;
    width: 22px;
    height: 22px;
    line-height: 22px;
    text-align: center;
    border-radius: 50%;
    background: @color-primary;
    color: @color-bg-card;
    font-size: 13px;
    font-weight: bold;
  }
  &__card-main {
    flex: 1;
    min-width: 0;
    cursor: pointer;
  }
  &__card-head {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    margin-bottom: 4px;
  }
  &__card-name {
    margin: 0;
    font-size: @font-size-lg;
    font-weight: 600;
    color: @color-text-primary;
  }
  &__card-tag {
    padding: 2px 8px;
    border-radius: 4px;
    background: fade(@color-primary, 10%);
    color: @color-primary;
    font-size: 11px;
    font-weight: 500;
  }
  &__card-hot {
    font-size: 11px;
    color: #e8601c;
  }
  &__card-desc {
    margin: 0 0 6px;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    line-height: 1.5;
  }
  &__card-meta {
    display: flex;
    flex-wrap: wrap;
    gap: @spacing-md;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    margin-bottom: 4px;
  }
  &__card-stars {
    color: #f5a623;
    letter-spacing: 2px;
  }
  &__card-visits {
    margin: 0;
    font-size: @font-size-sm;
    color: #e8601c;
  }

  // AI 推荐独立入口（路由跳转）
  &__ai-entry {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    width: 100%;
    padding: @spacing-md;
    margin-bottom: @spacing-md;
    border: 1.5px dashed @color-primary;
    border-radius: @radius-lg;
    background: fade(@color-primary, 4%);
    cursor: pointer;
    transition: background 0.2s;

    &:active { background: fade(@color-primary, 10%); }
  }
  &__ai-entry-icon { font-size: 22px; }
  &__ai-entry-text {
    flex: 1;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-primary;
    text-align: left;
  }
  &__ai-entry-arrow {
    font-size: 16px;
    color: @color-primary;
    transform: translateY(-2px);
  }

  // 底部固定操作栏
  &__footer {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 56px; // TabBar 顶部之上
    padding: @spacing-sm @spacing-md;
    background: fade(@color-bg-card, 96%);
    box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.06);
    z-index: 90;

    :deep(.rt-red-btn) {
      width: 100%;
      white-space: nowrap;
    }
  }
}
</style>
