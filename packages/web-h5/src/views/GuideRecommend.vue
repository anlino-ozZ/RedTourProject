<script setup lang="ts">
/**
 * AI 个性化导览推荐页（独立路由）
 * - 偏好采集（兴趣标签 / 体力等级 / 可用时长）
 * - AI 加权评分 → 生成 2-3 条推荐路线
 * - 选定后跳转到 /guide/navigate?routeId=xx
 */
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { RedButton } from '@red-tour-project/common'
import { ElMessage } from 'element-plus'
import {
  recommendRoutes,
  getRoutes,
  type FitnessLevel,
  type GuideRoute,
  type InterestTag,
  type RecommendOrigin,
} from '@/api/routes'
import { getSpots, type GuideSpot } from '@/api/spots'
import GuideIcon from '@/components/GuideIcon.vue'
import { playCorrectSound, playScoreSound } from '@/utils/sound'
import { useGeolocation } from '@/composables/useGeolocation'

const router = useRouter()

// ===== 偏好采集 =====
const INTEREST_OPTIONS: InterestTag[] = ['历史', '人物', '战役', '文物']
const FITNESS_OPTIONS: { key: FitnessLevel; label: string; desc: string }[] = [
  { key: 'easy', label: '轻松', desc: '约 1 小时 · 步行少' },
  { key: 'standard', label: '标准', desc: '约 1.5 小时 · 适度步行' },
  { key: 'deep', label: '深度', desc: '约 2 小时以上 · 徒步' },
]

const interests = ref<InterestTag[]>(['历史', '人物'])
const fitness = ref<FitnessLevel>('standard')
const duration = ref<number>(90)

// ===== 出发位置采集 =====
type OriginMode = 'none' | 'geo' | 'manual'
const originMode = ref<OriginMode>('none')
const {
  position: gpsPosition,
  status: geoStatus,
  errorMessage: geoError,
  start: locate,
} = useGeolocation({ immediate: false })
const originSpotId = ref<number | null>(null)

const origin = computed<RecommendOrigin | undefined>(() => {
  if (originMode.value === 'geo' && geoStatus.value === 'ok' && gpsPosition.value) {
    return {
      type: 'geo',
      latitude: gpsPosition.value.latitude,
      longitude: gpsPosition.value.longitude,
    }
  }
  if (originMode.value === 'manual' && originSpotId.value != null) {
    return { type: 'spot', spotId: originSpotId.value }
  }
  return undefined
})
/** 景点列表（手动选择出发点用） */
const spots = ref<GuideSpot[]>([])
/** 路线列表 */
const routes = ref<GuideRoute[]>([])
getSpots().then(({ list }) => (spots.value = list))
getRoutes().then(({ list }) => (routes.value = list))

const originSpot = computed(() => spots.value.find((s) => s.id === originSpotId.value) ?? null)

/** 实时定位：失败时保留定位面板并展示具体原因，用户可切手动选择 */
function useLocation() {
  originMode.value = 'geo'
  locate()
}

watch(geoStatus, (status) => {
  if (status === 'ok') playScoreSound()
  if (status === 'fail') {
    ElMessage.warning(geoError.value || '定位失败，可手动选择出发景点')
  }
})

function toggleInterest(tag: InterestTag) {
  const i = interests.value.indexOf(tag)
  if (i >= 0) interests.value.splice(i, 1)
  else interests.value.push(tag)
}

const canGenerate = computed(
  () => interests.value.length >= 1 && duration.value >= 30,
)

// ===== AI 推荐 =====
const aiGenerating = ref(false)
const aiReady = ref(false)
const recommended = ref<(GuideRoute & { score: number; highlight: string })[]>([])
const selectedId = ref<number | null>(null)

function selectRoute(id: number) {
  selectedId.value = selectedId.value === id ? null : id
}

const selectedRoute = computed(() => {
  if (!selectedId.value) return null
  return (
    recommended.value.find((r) => r.id === selectedId.value) ??
    routes.value.find((r) => r.id === selectedId.value) ??
    null
  )
})

async function generate() {
  if (!canGenerate.value) return
  aiGenerating.value = true
  await new Promise((r) => setTimeout(r, 1200))
  recommended.value = recommendRoutes(
    {
      interests: [...interests.value],
      fitness: fitness.value,
      duration: duration.value,
      origin: origin.value ? { ...origin.value } : undefined,
    },
    { routes: routes.value, spots: spots.value },
  )
  aiGenerating.value = false
  aiReady.value = true
  // AI 路线生成完成：上行琶音正反馈（上下文已在点击"生成"时解锁）
  playCorrectSound()
}

function startGuide() {
  if (!selectedRoute.value) return
  router.push({
    path: '/guide/navigate',
    query: { routeId: String(selectedRoute.value.id) },
  })
}

function backToPreference() {
  aiReady.value = false
  selectedId.value = null
}

function stars(difficulty: number): string {
  return '★'.repeat(difficulty) + '☆'.repeat(3 - difficulty)
}
</script>

<template>
  <div class="guide-rec">
    <!-- 返回按钮（Layout 已有顶部标题栏"智能导览"，此处补一个子页返回） -->
    <button class="guide-rec__back" @click="router.back()">
      <span class="guide-rec__back-arrow">←</span>
      <span>返回导览</span>
    </button>

    <!-- 偏好采集 -->
    <template v-if="!aiReady && !aiGenerating">
      <div class="guide-rec__head">
        <h2 class="guide-rec__title">AI 为您推荐专属路线</h2>
        <p class="guide-rec__sub">
          告诉 AI 您的兴趣偏好、体力等级和可用时长，为您生成 2–3 条个性化路线
        </p>
      </div>

      <div class="guide-rec__block">
        <h3 class="guide-rec__label"><GuideIcon name="folder" :size="15" /> 您感兴趣的主题</h3>
        <div class="guide-rec__chips">
          <button
            v-for="tag in INTEREST_OPTIONS"
            :key="tag"
            type="button"
            class="guide-rec__chip"
            :class="{ 'is-active': interests.includes(tag) }"
            @click="toggleInterest(tag)"
          >
            {{ tag }}
          </button>
        </div>
      </div>

      <div class="guide-rec__block">
        <h3 class="guide-rec__label"><GuideIcon name="pin" :size="15" /> 出发位置</h3>
        <div class="guide-rec__origin-tabs">
          <button
            type="button"
            class="guide-rec__origin-tab"
            :class="{ 'is-active': originMode === 'geo' }"
            @click="originMode = 'geo'"
          >
            <GuideIcon name="navigation" :size="14" /> 实时定位
          </button>
          <button
            type="button"
            class="guide-rec__origin-tab"
            :class="{ 'is-active': originMode === 'manual' }"
            @click="originMode = 'manual'"
          >
            <GuideIcon name="landmark" :size="14" /> 手动选择
          </button>
        </div>

        <!-- 实时定位 -->
        <div v-if="originMode === 'geo'" class="guide-rec__origin-body">
          <button
            type="button"
            class="guide-rec__locate"
            :class="{ 'is-ok': geoStatus === 'ok', 'is-fail': geoStatus === 'fail' }"
            :disabled="geoStatus === 'locating'"
            @click="useLocation"
          >
            <template v-if="geoStatus === 'locating'">定位中...</template>
            <template v-else-if="geoStatus === 'ok'">
              ✓ 已获取实时位置<template v-if="gpsPosition?.accuracy">（±{{ Math.round(gpsPosition.accuracy) }} 米）</template> · 点击重新定位
            </template>
            <template v-else><GuideIcon name="navigation" :size="14" /> 使用我的实时定位</template>
          </button>
          <p class="guide-rec__origin-tip" :class="{ 'is-error': geoStatus === 'fail' }">
            {{ geoStatus === 'fail' ? geoError : '将结合您的实时位置、景区热度与距离为您优化推荐' }}
          </p>
        </div>

        <!-- 手动选择 -->
        <div v-if="originMode === 'manual'" class="guide-rec__origin-body">
          <div class="guide-rec__chips">
            <button
              v-for="sp in spots"
              :key="sp.id"
              type="button"
              class="guide-rec__chip"
              :class="{ 'is-active': originSpotId === sp.id }"
              @click="originSpotId = originSpotId === sp.id ? null : sp.id"
            >
              {{ sp.name }}
            </button>
          </div>
          <p class="guide-rec__origin-tip">
            {{ originSpot ? `从「${originSpot.name}」出发，优先推荐距离更近的路线` : '选择您当前所在的景点作为出发点' }}
          </p>
        </div>

        <p v-if="originMode === 'none'" class="guide-rec__origin-tip">
          不选择则按景区默认位置推荐
        </p>
      </div>

      <div class="guide-rec__block">
        <h3 class="guide-rec__label"><GuideIcon name="activity" :size="15" /> 体力等级</h3>
        <div class="guide-rec__fitness">
          <button
            v-for="opt in FITNESS_OPTIONS"
            :key="opt.key"
            type="button"
            class="guide-rec__fit"
            :class="{ 'is-active': fitness === opt.key }"
            @click="fitness = opt.key"
          >
            <span class="guide-rec__fit-label">{{ opt.label }}</span>
            <span class="guide-rec__fit-desc">{{ opt.desc }}</span>
          </button>
        </div>
      </div>

      <div class="guide-rec__block">
        <div class="guide-rec__label-row">
          <h3 class="guide-rec__label"><GuideIcon name="clock" :size="15" /> 可用时长</h3>
          <span class="guide-rec__duration">{{ duration }} 分钟</span>
        </div>
        <input
          type="range"
          class="guide-rec__range"
          min="30"
          max="180"
          step="15"
          v-model.number="duration"
        />
        <div class="guide-rec__range-ticks">
          <span>30 分</span>
          <span>1 小时</span>
          <span>2 小时</span>
          <span>3 小时</span>
        </div>
      </div>

      <div class="guide-rec__actions">
        <RedButton
          type="primary"
          size="large"
          :disabled="!canGenerate"
          @click="generate"
        >
          <GuideIcon name="sparkles" :size="16" /> AI 为我推荐路线
        </RedButton>
      </div>
    </template>

    <!-- AI 生成中 -->
    <template v-if="aiGenerating">
      <div class="guide-rec__generating">
        <div class="guide-rec__dots">
          <span></span>
          <span></span>
          <span></span>
        </div>
        <h3 class="guide-rec__ai-title">AI 正在为您分析...</h3>
        <p class="guide-rec__ai-sub">匹配兴趣偏好 · 评估体力等级 · 结合景区热度</p>
      </div>
    </template>

    <!-- AI 推荐结果 -->
    <template v-if="aiReady">
      <div class="guide-rec__pref-head">
        <h2 class="guide-rec__title"><GuideIcon name="sparkles" :size="20" /> 为您推荐 {{ recommended.length }} 条路线</h2>
        <p class="guide-rec__sub">
          {{ FITNESS_OPTIONS.find((f) => f.key === fitness)?.label }} ·
          约 {{ duration }} 分钟 · 兴趣：{{ interests.join(' / ') }} ·
          出发：{{
            originSpot?.name ??
            (originMode === 'geo' && geoStatus === 'ok' ? '我的实时位置' : '景区默认位置')
          }}
        </p>
        <button class="guide-rec__edit" @click="backToPreference">重新选择偏好</button>
      </div>

      <div
        v-for="r in recommended"
        :key="r.id"
        class="guide-rec__card"
        :class="{ 'is-selected': selectedId === r.id }"
        @click="selectRoute(r.id)"
      >
        <span v-if="selectedId === r.id" class="guide-rec__check">✓</span>
        <div class="guide-rec__score">
          <span class="guide-rec__score-num">{{ r.score }}</span>
          <span class="guide-rec__score-label">匹配分</span>
        </div>
        <div class="guide-rec__info">
          <h3 class="guide-rec__name">{{ r.name }}</h3>
          <p class="guide-rec__highlight">{{ r.highlight }}</p>
          <div class="guide-rec__meta">
            <span>{{ stars(r.difficulty) }}</span>
            <span>约 {{ r.duration }} 分钟</span>
            <span>{{ r.spotCount }} 个景点</span>
          </div>
          <p class="guide-rec__desc">{{ r.desc }}</p>
        </div>
      </div>

      <div class="guide-rec__actions">
        <RedButton
          type="primary"
          size="large"
          :disabled="!selectedId"
          @click="startGuide"
        >
          {{ selectedRoute ? `开始导览：${selectedRoute.name}` : '请选择一条推荐路线' }}
        </RedButton>
      </div>
    </template>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.guide-rec {
  padding-bottom: calc(56px + 92px); // header + tabbar 总量

  &__back {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-bottom: @spacing-lg;
    padding: 8px 16px;
    border: 1.5px solid @color-primary;
    border-radius: 20px;
    background: @color-primary-light;
    color: @color-primary;
    font-size: @font-size-base;
    font-weight: 600;
    cursor: pointer;

    &:active { background: @color-primary; color: #fff; }
  }
  &__back-arrow {
    font-size: 16px;
    line-height: 1;
  }

  &__head,
  &__pref-head {
    margin-bottom: @spacing-lg;
  }
  &__emoji {
    width: 48px;
    height: 48px;
    object-fit: contain;
    display: block;
    margin-bottom: @spacing-sm;
  }
  &__title {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0 0 @spacing-xs;
    font-size: 22px;
    font-weight: 700;
    color: @color-text-primary;
  }
  &__sub {
    margin: 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    line-height: 1.6;
  }
  &__edit {
    margin-top: @spacing-sm;
    padding: 0;
    border: none;
    background: transparent;
    color: @color-primary;
    font-size: @font-size-sm;
    cursor: pointer;
  }

  &__block {
    margin-bottom: @spacing-lg;
  }
  &__label {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0 0 @spacing-sm;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
  }
  &__label-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: @spacing-sm;
  }
  &__duration {
    font-size: @font-size-sm;
    color: @color-primary;
    font-weight: 600;
  }

  // 兴趣标签 chips
  &__chips {
    display: flex;
    flex-wrap: wrap;
    gap: @spacing-sm;
  }
  &__chip {
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

  // 出发位置
  &__origin-tabs {
    display: flex;
    gap: @spacing-sm;
    margin-bottom: @spacing-sm;
  }
  &__origin-tab {
    flex: 1;
    height: 40px;
    border: 1.5px solid @color-border;
    border-radius: @radius-base;
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
  &__origin-body {
    margin-top: @spacing-sm;
  }
  &__locate {
    width: 100%;
    height: 44px;
    border: 1.5px dashed @color-primary;
    border-radius: @radius-base;
    background: fade(@color-primary, 5%);
    color: @color-primary;
    font-size: @font-size-base;
    font-weight: 600;
    cursor: pointer;

    &:disabled { opacity: 0.6; cursor: wait; }
    &.is-ok {
      border-style: solid;
      border-color: #52c41a;
      background: fade(#52c41a, 8%);
      color: #389e0d;
    }
    &.is-fail {
      border-color: #e6a23c;
      background: fade(#e6a23c, 8%);
      color: #b26a00;
    }
  }
  &__origin-tip {
    margin: @spacing-sm 0 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    line-height: 1.5;

    &.is-error {
      color: #c45656;
    }
  }

  // 体力等级
  &__fitness {
    display: flex;
    gap: @spacing-sm;
  }
  &__fit {
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
  &__fit-label {
    display: block;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
    margin-bottom: 2px;
  }
  &__fit-desc {
    display: block;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  // 时长 slider
  &__range {
    width: 100%;
    accent-color: @color-primary;
  }
  &__range-ticks {
    display: flex;
    justify-content: space-between;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    margin-top: 4px;
  }

  // AI 生成中
  &__generating {
    padding: 120px 0;
    text-align: center;
  }
  &__dots {
    display: flex;
    justify-content: center;
    gap: 10px;
    margin-bottom: @spacing-lg;
    span {
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: @color-primary;
      animation: rec-ai-pulse 1.2s ease-in-out infinite;

      &:nth-child(2) { animation-delay: 0.2s; }
      &:nth-child(3) { animation-delay: 0.4s; }
    }
  }
  @keyframes rec-ai-pulse {
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

  // 推荐卡片
  &__card {
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
  &__check {
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
  &__score {
    flex-shrink: 0;
    width: 60px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    border-right: 1px dashed @color-border;
    padding-right: @spacing-md;
  }
  &__score-num {
    font-size: 28px;
    font-weight: 700;
    color: @color-primary;
    line-height: 1;
  }
  &__score-label {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    margin-top: 4px;
  }
  &__info {
    flex: 1;
    min-width: 0;
  }
  &__name {
    margin: 0 0 4px;
    font-size: @font-size-lg;
    font-weight: 600;
    color: @color-text-primary;
  }
  &__highlight {
    margin: 0 0 6px;
    font-size: @font-size-sm;
    color: #e0552b;
    font-weight: 600;
    line-height: 1.5;
  }
  &__meta {
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
  &__desc {
    margin: 0;
    font-size: @font-size-sm;
    color: @color-text-regular;
    line-height: 1.5;
  }

  &__actions {
    margin-top: @spacing-lg;
  }
}
</style>
