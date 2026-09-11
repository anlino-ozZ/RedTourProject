<script setup lang="ts">
// 智能导览（H-04，静态页面，数据为 mock，后续 H-07 接接口）
// 默认地图视图：景点定位 + 该景点包含的路线；列表视图：按定位/推荐排序并展示访问热度
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { RedButton } from '@red-tour-project/common'
import { routes, spots } from '@/mock/guide'

const route = useRoute()

/** 视图：map 地图（默认） / list 列表；首页跳入时通过 query.view 指定 */
const view = ref<'map' | 'list'>(route.query.view === 'list' ? 'list' : 'map')
/** 排序方式：recommend 推荐优先 / distance 距离优先 */
const sortType = ref<'recommend' | 'distance'>('recommend')
/** 当前选中路线 ID */
const selectedId = ref<number | null>(null)
/** 地图当前定位景点 ID；首页点击热门景点时通过 query.spotId 传入 */
const selectedSpotId = ref<number>(
  route.query.spotId ? Number(route.query.spotId) : spots[0].id,
)

/** 排序后的路线（距离优先按距离升序；推荐优先把"推荐"路线置顶） */
const sortedRoutes = computed(() => {
  if (sortType.value === 'distance') {
    return [...routes].sort((a, b) => a.distance - b.distance)
  }
  return [...routes].sort(
    (a, b) => (b.tag === '推荐' ? 1 : 0) - (a.tag === '推荐' ? 1 : 0),
  )
})

const selectedRoute = computed(
  () => routes.find((r) => r.id === selectedId.value) ?? null,
)

/** 当前定位景点 */
const selectedSpot = computed(
  () => spots.find((s) => s.id === selectedSpotId.value) ?? spots[0],
)

/** 当前定位景点包含的路线 */
const spotRoutes = computed(() =>
  routes.filter((r) => r.spotIds.includes(selectedSpotId.value)),
)

/** 访问热度最高的路线 ID（地图/列表共用"热门"标记） */
const hottestRouteId = computed(
  () => [...routes].sort((a, b) => b.visitCount - a.visitCount)[0]?.id,
)

/** 难度星级：★ 实心 + ☆ 空心，共 3 颗 */
function stars(difficulty: number): string {
  return '★'.repeat(difficulty) + '☆'.repeat(3 - difficulty)
}

/** 访问人数格式化：过万以"万"为单位 */
function formatVisits(n: number): string {
  if (n >= 10000) return `${(n / 10000).toFixed(1)} 万人游览过`
  return `${n.toLocaleString()} 人游览过`
}

function selectRoute(id: number) {
  selectedId.value = selectedId.value === id ? null : id
}

function startGuide() {
  if (!selectedRoute.value) return
  // TODO(H-后续): 跳转导览进行中页（路线详情 + TTS 讲解 + 打卡状态机）
  window.alert(`即将开始导览：${selectedRoute.value.name}`)
}
</script>

<template>
  <div class="guide">
    <!-- 视图切换（地图视图优先） -->
    <div class="guide__switch">
      <button
        class="guide__switch-item"
        :class="{ 'is-active': view === 'map' }"
        @click="view = 'map'"
      >
        地图视图
      </button>
      <button
        class="guide__switch-item"
        :class="{ 'is-active': view === 'list' }"
        @click="view = 'list'"
      >
        路线列表
      </button>
    </div>

    <!-- 地图视图：景点定位 + 该景点包含的路线 -->
    <template v-if="view === 'map'">
      <div class="guide__map-canvas">
        <!-- 模拟地图装饰：绿地网格、道路、水域 -->
        <span class="guide__map-road guide__map-road--1"></span>
        <span class="guide__map-road guide__map-road--2"></span>
        <span class="guide__map-lake"></span>
        <span class="guide__map-chip">井冈山革命根据地 · 景区示意图</span>
        <!-- 我的位置（模拟定位） -->
        <span class="guide__map-me" style="left: 48%; top: 82%">
          <span class="guide__map-me-dot"></span>
          <span class="guide__map-me-label">我的位置</span>
        </span>
        <!-- 景点定位点 -->
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

      <!-- 当前定位景点信息及其包含的路线 -->
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
          @click="selectRoute(item.id)"
        >
          <div class="guide__spot-route-main">
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
          <span v-if="selectedId === item.id" class="guide__spot-route-check">✓</span>
        </div>

        <div v-if="spotRoutes.length === 0" class="guide__spot-empty">
          暂无途经该景点的导览路线
        </div>
      </div>
    </template>

    <!-- 路线列表视图 -->
    <template v-else>
      <!-- 排序切换 -->
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
        @click="selectRoute(item.id)"
      >
        <span v-if="selectedId === item.id" class="guide__card-check">✓</span>
        <div class="guide__card-main">
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
          <!-- 基于定位的访问热度 -->
          <p class="guide__card-visits">🔥 {{ formatVisits(item.visitCount) }}</p>
        </div>
      </div>
    </template>

    <!-- 底部操作栏（位于 TabBar 凸起按钮上方） -->
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
  padding-bottom: 132px; // 为底部固定操作栏 + TabBar 凸起按钮留空

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

  // ===== 模拟地图 =====
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
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  }

  // 模拟道路
  &__map-road {
    position: absolute;
    background: rgba(255, 255, 255, 0.85);

    &--1 {
      width: 140%;
      height: 6px;
      top: 40%;
      left: -20%;
      transform: rotate(-16deg);
    }
    &--2 {
      width: 6px;
      height: 140%;
      top: -20%;
      left: 64%;
      transform: rotate(12deg);
    }
  }

  // 模拟水域
  &__map-lake {
    position: absolute;
    width: 90px;
    height: 56px;
    right: 8%;
    bottom: 10%;
    background: rgba(135, 188, 230, 0.55);
    border-radius: 46% 54% 60% 40% / 50% 44% 56% 50%;
  }

  // 我的位置
  &__map-me {
    position: absolute;
    z-index: 3;
    transform: translate(-50%, -50%);
    display: flex;
    flex-direction: column;
    align-items: center;
    pointer-events: none;
  }
  &__map-me-dot {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: #2f7cf6;
    border: 3px solid #fff;
    box-shadow: 0 0 0 4px rgba(47, 124, 246, 0.25);
  }
  &__map-me-label {
    margin-top: 2px;
    padding: 1px 6px;
    border-radius: @radius-sm;
    background: rgba(47, 124, 246, 0.9);
    color: #fff;
    font-size: 10px;
    white-space: nowrap;
  }

  // 景点定位点
  &__map-pin {
    position: absolute;
    z-index: 4;
    transform: translate(-50%, -100%);
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 0;
    border: none;
    background: transparent;
    cursor: pointer;

    // 底部锚点对准坐标
    margin-bottom: -6px;
  }
  &__map-pin-dot {
    width: 18px;
    height: 18px;
    border-radius: 50% 50% 50% 0;
    transform: rotate(-45deg);
    background: #fff;
    border: 3px solid @color-primary;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.18);
    transition: all 0.2s;
  }
  &__map-pin-name {
    margin-top: 4px;
    padding: 2px 8px;
    border-radius: @radius-sm;
    background: rgba(255, 255, 255, 0.92);
    color: @color-text-regular;
    font-size: 11px;
    white-space: nowrap;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  }
  &__map-pin.is-active {
    z-index: 5;

    .guide__map-pin-dot {
      width: 24px;
      height: 24px;
      background: @color-primary;
      border-color: #fff;
      box-shadow: 0 0 0 6px rgba(196, 30, 58, 0.18);
      animation: guide-pin-pulse 1.8s ease-out infinite;
    }
    .guide__map-pin-name {
      background: @color-primary;
      color: #fff;
      font-weight: 600;
    }
  }

  @keyframes guide-pin-pulse {
    0% {
      box-shadow: 0 0 0 4px rgba(196, 30, 58, 0.25);
    }
    70% {
      box-shadow: 0 0 0 10px rgba(196, 30, 58, 0);
    }
    100% {
      box-shadow: 0 0 0 4px rgba(196, 30, 58, 0);
    }
  }

  // ===== 定位景点面板 =====
  &__spot-panel {
    background: @color-bg-card;
    border-radius: @radius-lg;
    padding: @spacing-md;
    box-shadow: @shadow-card;
  }
  &__spot-head {
    padding-bottom: @spacing-sm;
    border-bottom: 1px dashed @color-border;
    margin-bottom: @spacing-sm;
  }
  &__spot-name {
    margin: 0 0 @spacing-xs;
    font-size: @font-size-lg;
    font-weight: 700;
    color: @color-text-primary;
  }
  &__spot-tag {
    display: inline-block;
    margin-left: @spacing-xs;
    padding: 2px 8px;
    border-radius: @radius-sm;
    background: fade(@color-primary, 10%);
    color: @color-primary;
    font-size: @font-size-sm;
    font-weight: 400;
    vertical-align: 2px;
  }
  &__spot-desc {
    margin: 0;
    font-size: @font-size-sm;
    color: @color-text-regular;
    line-height: 1.6;
  }
  &__spot-sub {
    margin: 0 0 @spacing-sm;
    font-size: @font-size-sm;
    font-weight: 600;
    color: @color-text-primary;
  }
  &__spot-route {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: @spacing-sm;
    padding: @spacing-sm @spacing-md;
    margin-bottom: @spacing-sm;
    border: 1.5px solid @color-border;
    border-radius: @radius-base;
    background: @color-bg-card;
    cursor: pointer;
    transition: border-color 0.2s, background 0.2s;

    &:last-child {
      margin-bottom: 0;
    }
    &:active {
      background: @color-primary-light;
    }
    &.is-selected {
      border-color: @color-primary;
      background: @color-primary-light;
    }
  }
  &__spot-route-head {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    margin-bottom: 4px;
  }
  &__spot-route-name {
    margin: 0;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
  }
  &__spot-route-hot {
    flex-shrink: 0;
    padding: 1px 6px;
    border-radius: @radius-sm;
    background: #fff1e8;
    color: #e8601c;
    font-size: 10px;
    font-weight: 600;
  }
  &__spot-route-meta {
    display: flex;
    flex-wrap: wrap;
    gap: @spacing-sm;
    font-size: @font-size-sm;
    color: @color-text-secondary;

    span:first-child {
      color: #f5a623;
      letter-spacing: 2px;
    }
  }
  &__spot-route-visits {
    margin: 4px 0 0;
    font-size: @font-size-sm;
    color: #e8601c;
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

  // ===== 列表视图 =====
  // 排序切换
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
    height: 28px;
    padding: 0 12px;
    border: 1px solid @color-border;
    border-radius: 14px;
    background: @color-bg-card;
    color: @color-text-regular;
    font-size: @font-size-sm;
    transition: all 0.2s;

    &.is-active {
      border-color: @color-primary;
      background: @color-primary;
      color: #fff;
    }
  }

  &__hint {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    margin: 0 0 @spacing-sm;
  }

  // 路线卡片
  &__card {
    position: relative;
    display: flex;
    background: @color-bg-card;
    border-radius: @radius-lg;
    padding: @spacing-md;
    margin-bottom: @spacing-md;
    border: 2px solid transparent;
    box-shadow: @shadow-card;
    cursor: pointer;
    transition: border-color 0.2s, box-shadow 0.2s, transform 0.15s;

    &:active {
      transform: scale(0.99);
    }

    &.is-selected {
      border-color: @color-primary;
      box-shadow: 0 4px 14px rgba(196, 30, 58, 0.18);
    }
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

  &__card-head {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    padding-right: 28px; // 避开对勾
  }

  &__card-name {
    margin: 0;
    font-size: @font-size-lg;
    font-weight: 600;
    color: @color-text-primary;
  }

  &__card-tag {
    flex-shrink: 0;
    padding: 2px 8px;
    border-radius: @radius-base;
    background: fade(@color-primary, 10%);
    color: @color-primary;
    font-size: @font-size-sm;
  }

  &__card-hot {
    flex-shrink: 0;
    padding: 2px 8px;
    border-radius: @radius-base;
    background: #fff1e8;
    color: #e8601c;
    font-size: @font-size-sm;
    font-weight: 600;
  }

  &__card-desc {
    margin: @spacing-xs 0 @spacing-sm;
    font-size: @font-size-sm;
    color: @color-text-regular;
    line-height: 1.5;
  }

  &__card-meta {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: @spacing-md;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__card-stars {
    color: #f5a623;
    letter-spacing: 2px;
  }

  &__card-duration,
  &__card-spots,
  &__card-distance {
    &::before {
      content: '·';
      margin-right: @spacing-md;
      color: @color-text-secondary;
    }
  }

  // 访问热度（基于定位的游客数据）
  &__card-visits {
    margin: @spacing-sm 0 0;
    font-size: @font-size-sm;
    font-weight: 600;
    color: #e8601c;
  }

  // 底部固定操作栏
  &__footer {
    position: fixed;
    left: 0;
    right: 0;
    bottom: calc(56px + 18px); // TabBar 高度 + 问答凸起按钮高度
    padding: @spacing-sm @spacing-md;
    background: fade(@color-bg-card, 96%);
    box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.06);
    z-index: 90;

    // RedButton 无 block 属性，这里强制撑满，保证选中前后按钮尺寸一致
    :deep(.rt-red-btn) {
      width: 100%;
      white-space: nowrap;
    }
  }
}
</style>
