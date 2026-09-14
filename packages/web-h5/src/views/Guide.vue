<script setup lang="ts">
// 智能导览（H-06 个性化推荐 + H-04 路线浏览）
// Stage 状态机：preference 偏好采集 → generating AI 生成中 → result 推荐结果 → navigating 导航中
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RedButton } from '@red-tour-project/common'
import {
  recommendRoutes,
  routes,
  spots,
  type FitnessLevel,
  type GuideRoute,
  type InterestTag,
} from '@/mock/guide'

const route = useRoute()
const router = useRouter()

// ===== 默认视图 =====
const view = ref<'map' | 'list'>(
  route.query.view === 'list' ? 'list' : 'map',
)
/** AI 推荐面板是否展开（叠加在地图/列表下方，不替换主视图） */
const showAI = ref(false)

// ===== 偏好采集 =====
const INTEREST_OPTIONS: InterestTag[] = ['历史', '人物', '战役', '文物']
const FITNESS_OPTIONS: { key: FitnessLevel; label: string; desc: string }[] = [
  { key: 'easy', label: '轻松', desc: '约 1 小时 · 步行少' },
  { key: 'standard', label: '标准', desc: '约 1.5 小时 · 适度步行' },
  { key: 'deep', label: '深度', desc: '约 2 小时以上 · 徒步' },
]

const interests = ref<InterestTag[]>(['历史', '人物'])
const fitness = ref<FitnessLevel>('standard')
/** 可用时长（分钟） */
const duration = ref<number>(90)

function toggleInterest(tag: InterestTag) {
  const i = interests.value.indexOf(tag)
  if (i >= 0) interests.value.splice(i, 1)
  else interests.value.push(tag)
}

const canGenerate = computed(
  () => interests.value.length >= 1 && duration.value >= 30,
)

// ===== AI 推荐面板 =====
/** 选中的路线（推荐结果里） */
const selectedId = ref<number | null>(null)

function selectRoute(id: number) {
  selectedId.value = selectedId.value === id ? null : id
}

const selectedRoute = computed(() => {
  if (!selectedId.value) return null
  return (
    recommended.value.find((r) => r.id === selectedId.value) ??
    routes.find((r) => r.id === selectedId.value) ??
    null
  )
})

/** AI 加权评分后的推荐路线 */
const recommended = ref<(GuideRoute & { score: number; highlight: string })[]>([])

async function generate() {
  if (!canGenerate.value) return
  aiGenerating.value = true
  await new Promise((r) => setTimeout(r, 1200))
  recommended.value = recommendRoutes({
    interests: [...interests.value],
    fitness: fitness.value,
    duration: duration.value,
  })
  aiGenerating.value = false
  aiReady.value = true
}

const aiGenerating = ref(false)
const aiReady = ref(false)

/** 选定路线后进入导览进行中页（兼容 AI 推荐 / 地图 / 列表三种来源） */
function startGuide() {
  const id = selectedId.value
  if (!id) return
  router.push({
    path: '/guide/navigate',
    query: { routeId: String(id) },
  })
}

/** 从推荐结果返回偏好采集 */
function backToPreference() {
  aiReady.value = false
  selectedId.value = null
}

/** 关闭 AI 面板 */
function closeAI() {
  showAI.value = false
  aiReady.value = false
  selectedId.value = null
}

// ===== 排序（原 H-04 路线列表） =====
const sortType = ref<'recommend' | 'distance'>('recommend')
/** 地图定位景点（首页跳入会预设 spotId） */
const selectedSpotId = ref<number>(
  route.query.spotId ? Number(route.query.spotId) : spots[0].id,
)
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
</script>

<template>
  <div class="guide">
    <!-- ===== 默认视图：地图 / 路线列表 ===== -->
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
          <!-- 直接开始导览按钮，不必先选中再点底部 -->
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
          <!-- 基于定位的访问热度 -->
          <p class="guide__card-visits">🔥 {{ formatVisits(item.visitCount) }}</p>
        </div>
        <!-- 直接开始导览按钮 -->
        <button
          class="guide__card-go"
          @click.stop="selectedId = item.id; startGuide()"
        >
          开始导览
        </button>
      </div>
    </template>

    <!-- ===== AI 个性化导览面板（可展开，位于地图/列表下方） ===== -->
    <!-- 折叠状态下的入口按钮 -->
    <button
      v-if="!showAI"
      class="guide__ai-entry"
      @click="showAI = true"
    >
      <span class="guide__ai-entry-icon">🤖</span>
      <span class="guide__ai-entry-text">AI 为我推荐专属路线</span>
      <span class="guide__ai-entry-arrow">↓</span>
    </button>

    <!-- 展开：偏好采集 + AI 生成 + 推荐结果 -->
    <template v-if="showAI">
      <div class="guide__ai-panel">
        <div class="guide__ai-panel-head">
          <h2 class="guide__pref-title">🎯 AI 个性化导览</h2>
          <button class="guide__ai-close" @click="closeAI">×</button>
        </div>
        <p v-if="!aiReady" class="guide__pref-sub">
          告诉 AI 您的兴趣和体力，为您推荐 2-3 条专属路线
        </p>

        <!-- 偏好采集（未生成时显示） -->
        <template v-if="!aiReady">
          <div class="guide__pref-block">
            <h3 class="guide__pref-label">您感兴趣的主题</h3>
            <div class="guide__pref-chips">
              <button
                v-for="tag in INTEREST_OPTIONS"
                :key="tag"
                type="button"
                class="guide__pref-chip"
                :class="{ 'is-active': interests.includes(tag) }"
                @click="toggleInterest(tag)"
              >
                {{ tag }}
              </button>
            </div>
          </div>

          <div class="guide__pref-block">
            <h3 class="guide__pref-label">体力等级</h3>
            <div class="guide__pref-fitness">
              <button
                v-for="opt in FITNESS_OPTIONS"
                :key="opt.key"
                type="button"
                class="guide__pref-fit"
                :class="{ 'is-active': fitness === opt.key }"
                @click="fitness = opt.key"
              >
                <span class="guide__pref-fit-label">{{ opt.label }}</span>
                <span class="guide__pref-fit-desc">{{ opt.desc }}</span>
              </button>
            </div>
          </div>

          <div class="guide__pref-block">
            <div class="guide__pref-label-row">
              <h3 class="guide__pref-label">可用时长</h3>
              <span class="guide__pref-duration">{{ duration }} 分钟</span>
            </div>
            <input
              type="range"
              class="guide__pref-range"
              min="30"
              max="180"
              step="15"
              v-model.number="duration"
            />
            <div class="guide__pref-range-ticks">
              <span>30 分</span>
              <span>1 小时</span>
              <span>2 小时</span>
              <span>3 小时</span>
            </div>
          </div>

          <RedButton
            type="primary"
            size="large"
            block
            :disabled="!canGenerate || aiGenerating"
            @click="generate"
          >
            {{ aiGenerating ? '🤖 AI 正在分析...' : '🤖 AI 为我推荐路线' }}
          </RedButton>
        </template>

        <!-- AI 生成中 -->
        <template v-if="aiGenerating">
          <div class="guide__generating">
            <div class="guide__ai-dots">
              <span></span>
              <span></span>
              <span></span>
            </div>
            <h3 class="guide__ai-title">AI 正在为您推荐路线...</h3>
            <p class="guide__ai-sub">分析兴趣偏好 · 匹配体力等级 · 结合景区热度</p>
          </div>
        </template>

        <!-- AI 推荐结果 -->
        <template v-if="aiReady">
          <div class="guide__pref-head">
            <h2 class="guide__pref-title">✨ 为您推荐 {{ recommended.length }} 条路线</h2>
            <p class="guide__pref-sub">
              {{ FITNESS_OPTIONS.find((f) => f.key === fitness)?.label }} ·
              约 {{ duration }} 分钟 · 兴趣：{{ interests.join(' / ') }}
            </p>
            <button class="guide__pref-edit" @click="backToPreference">重选偏好</button>
          </div>

          <div
            v-for="r in recommended"
            :key="r.id"
            class="guide__rec-card"
            :class="{ 'is-selected': selectedId === r.id }"
            @click="selectRoute(r.id)"
          >
            <span v-if="selectedId === r.id" class="guide__rec-check">✓</span>
            <div class="guide__rec-score">
              <span class="guide__rec-score-num">{{ r.score }}</span>
              <span class="guide__rec-score-label">匹配分</span>
            </div>
            <div class="guide__rec-info">
              <h3 class="guide__rec-name">{{ r.name }}</h3>
              <p class="guide__rec-highlight">{{ r.highlight }}</p>
              <div class="guide__rec-meta">
                <span>{{ stars(r.difficulty) }}</span>
                <span>约 {{ r.duration }} 分钟</span>
                <span>{{ r.spotCount }} 个景点</span>
              </div>
              <p class="guide__rec-desc">{{ r.desc }}</p>
            </div>
          </div>

          <!-- AI 推荐路线的"开始导览"按钮 -->
          <div class="guide__ai-panel-actions">
            <RedButton
              type="primary"
              size="large"
              block
              :disabled="!selectedId"
              @click="startGuide"
            >
              {{ selectedRoute ? `开始导览：${selectedRoute.name}` : '请选择一条推荐路线' }}
            </RedButton>
          </div>
        </template>
      </div>
    </template>

    <!-- 底部操作栏（仅地图/列表视图显示；AI 展开时由面板内按钮接管） -->
    <div v-if="!showAI" class="guide__footer">
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
  // padding-bottom 由 Layout.has-tabbar 统一处理（140px，含凸起 CTA），这里不再重复

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

    &:active {
      transform: scale(0.99);
    }

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

  // ===== AI 偏好采集 & 推荐 =====
  &__pref {
    padding: @spacing-md 0 @spacing-lg;
  }
  &__pref-head {
    padding-bottom: @spacing-lg;
  }
  &__pref-title {
    margin: 0 0 @spacing-xs;
    font-size: 22px;
    font-weight: 700;
    color: @color-text-primary;
  }
  &__pref-sub {
    margin: 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    line-height: 1.6;
  }
  &__pref-edit {
    margin-top: @spacing-sm;
    padding: 0;
    border: none;
    background: transparent;
    color: @color-primary;
    font-size: @font-size-sm;
    cursor: pointer;
  }
  &__pref-block {
    margin-bottom: @spacing-lg;
  }
  &__pref-label {
    margin: 0 0 @spacing-sm;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
  }
  &__pref-label-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: @spacing-sm;
  }
  &__pref-duration {
    font-size: @font-size-sm;
    color: @color-primary;
    font-weight: 600;
  }

  // 兴趣标签 chips
  &__pref-chips {
    display: flex;
    flex-wrap: wrap;
    gap: @spacing-sm;
  }
  &__pref-chip {
    height: 36px;
    padding: 0 18px;
    border: 1.5px solid @color-border;
    border-radius: 18px;
    background: @color-bg-card;
    color: @color-text-regular;
    font-size: @font-size-base;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;

    &.is-active {
      border-color: @color-primary;
      background: @color-primary;
      color: #fff;
      font-weight: 600;
    }
  }

  // 体力等级卡片
  &__pref-fitness {
    display: flex;
    gap: @spacing-sm;
  }
  &__pref-fit {
    flex: 1;
    padding: @spacing-sm @spacing-md;
    border: 1.5px solid @color-border;
    border-radius: @radius-base;
    background: @color-bg-card;
    text-align: left;
    cursor: pointer;
    transition: all 0.2s;

    &.is-active {
      border-color: @color-primary;
      background: fade(@color-primary, 8%);
    }
  }
  &__pref-fit-label {
    display: block;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
    margin-bottom: 2px;
  }
  &__pref-fit-desc {
    display: block;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  // 时长 slider
  &__pref-range {
    width: 100%;
    accent-color: @color-primary;
  }
  &__pref-range-ticks {
    display: flex;
    justify-content: space-between;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    margin-top: 4px;
  }

  // AI 生成中
  &__generating {
    padding: 100px 0;
    text-align: center;
  }
  &__ai-dots {
    display: flex;
    justify-content: center;
    gap: 10px;
    margin-bottom: @spacing-lg;
    span {
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: @color-primary;
      animation: guide-ai-pulse 1.2s ease-in-out infinite;

      &:nth-child(2) { animation-delay: 0.2s; }
      &:nth-child(3) { animation-delay: 0.4s; }
    }
  }
  @keyframes guide-ai-pulse {
    0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
    40% { transform: scale(1); opacity: 1; }
  }
  &__ai-title {
    margin: 0 0 @spacing-xs;
    font-size: 18px;
    color: @color-text-primary;
  }
  &__ai-sub {
    margin: 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  // 推荐路线卡片
  &__rec-card {
    position: relative;
    display: flex;
    gap: @spacing-md;
    padding: @spacing-md;
    margin-bottom: @spacing-md;
    border: 2px solid @color-border;
    border-radius: @radius-lg;
    background: @color-bg-card;
    box-shadow: @shadow-card;
    cursor: pointer;
    transition: border-color 0.2s, box-shadow 0.2s, transform 0.15s;

    &.is-selected {
      border-color: @color-primary;
      background: @color-primary-light;
      box-shadow: 0 4px 14px rgba(196, 30, 58, 0.18);
    }
    &:active { transform: scale(0.99); }
  }
  &__rec-check {
    position: absolute;
    top: 10px;
    right: 10px;
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
  &__rec-score {
    flex-shrink: 0;
    width: 56px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    border-right: 1px dashed @color-border;
    padding-right: @spacing-md;
  }
  &__rec-score-num {
    font-size: 28px;
    font-weight: 700;
    color: @color-primary;
    line-height: 1;
  }
  &__rec-score-label {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    margin-top: 4px;
  }
  &__rec-info {
    flex: 1;
    min-width: 0;
  }
  &__rec-name {
    margin: 0 0 4px;
    font-size: @font-size-lg;
    font-weight: 600;
    color: @color-text-primary;
  }
  &__rec-highlight {
    margin: 0 0 6px;
    font-size: @font-size-sm;
    color: #e0552b;
    font-weight: 600;
    line-height: 1.5;
  }
  &__rec-meta {
    display: flex;
    flex-wrap: wrap;
    gap: @spacing-md;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    margin-bottom: 6px;

    span:first-child {
      color: #f5a623;
      letter-spacing: 2px;
    }
  }
  &__rec-desc {
    margin: 0;
    font-size: @font-size-sm;
    color: @color-text-regular;
    line-height: 1.5;
  }

  &__skip {
    display: block;
    width: 100%;
    padding: 12px 0;
    border: none;
    background: transparent;
    color: @color-text-secondary;
    font-size: @font-size-sm;
    cursor: pointer;
    text-align: center;

    &--center {
      margin-bottom: @spacing-sm;
    }
  }

  // ===== AI 入口（折叠态） =====
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

  // ===== AI 面板（展开态） =====
  &__ai-panel {
    margin-bottom: @spacing-md;
    padding: @spacing-md @spacing-md calc(@spacing-md + 8px);
    border-radius: @radius-lg;
    background: @color-bg-card;
    box-shadow: @shadow-card;
  }
  &__ai-panel-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    margin-bottom: @spacing-sm;
  }
  &__ai-close {
    width: 28px;
    height: 28px;
    border: none;
    border-radius: 50%;
    background: @color-border;
    color: @color-text-secondary;
    font-size: 18px;
    cursor: pointer;
    line-height: 26px;
    text-align: center;
  }
  &__ai-panel-actions {
    margin: @spacing-lg 0 @spacing-sm;
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
