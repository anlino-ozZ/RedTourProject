<script setup lang="ts">
/**
 * AI 个性化导览推荐页（独立路由）
 * - 偏好采集（兴趣标签 / 体力等级 / 可用时长）
 * - AI 加权评分 → 生成 2-3 条推荐路线
 * - 选定后跳转到 /guide/navigate?routeId=xx
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RedButton } from '@red-tour-project/common'
import {
  recommendRoutes,
  routes,
  type FitnessLevel,
  type GuideRoute,
  type InterestTag,
} from '@/mock/guide'

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
    routes.find((r) => r.id === selectedId.value) ??
    null
  )
})

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
        <h3 class="guide-rec__label">🗂️ 您感兴趣的主题</h3>
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
        <h3 class="guide-rec__label">💪 体力等级</h3>
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
          <h3 class="guide-rec__label">⏱️ 可用时长</h3>
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
          ✨ AI 为我推荐路线
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
        <h2 class="guide-rec__title">✨ 为您推荐 {{ recommended.length }} 条路线</h2>
        <p class="guide-rec__sub">
          {{ FITNESS_OPTIONS.find((f) => f.key === fitness)?.label }} ·
          约 {{ duration }} 分钟 · 兴趣：{{ interests.join(' / ') }}
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
