<script setup lang="ts">
// 智能导览 - 路线列表页（H-04，静态页面，数据为 mock，后续 H-07 接接口）
import { computed, ref } from 'vue'
import { RedButton } from '@red-tour-project/common'

/** 导览路线（mock） */
interface GuideRoute {
  id: number
  name: string
  desc: string
  /** 难度 1~3（对应星级） */
  difficulty: number
  /** 预计时长（分钟） */
  duration: number
  /** 景点数 */
  spotCount: number
  /** 距当前位置（公里，mock） */
  distance: number
  tag: string
}

const routes = ref<GuideRoute[]>([
  {
    id: 1,
    name: '初心之旅·经典打卡线',
    desc: '黄洋界哨口、茨坪旧居、革命博物馆，首次到访必走',
    difficulty: 1,
    duration: 60,
    spotCount: 4,
    distance: 3.2,
    tag: '轻松',
  },
  {
    id: 2,
    name: '烽火岁月·深度体验线',
    desc: '小井红军医院、龙潭旧址群，感受艰苦卓绝的斗争史',
    difficulty: 2,
    duration: 90,
    spotCount: 6,
    distance: 1.5,
    tag: '推荐',
  },
  {
    id: 3,
    name: '重上井冈·全程徒步线',
    desc: '五大哨口全线徒步，适合体力充沛、时间充裕的游客',
    difficulty: 3,
    duration: 120,
    spotCount: 9,
    distance: 6.8,
    tag: '挑战',
  },
])

/** 视图：list 列表 / map 地图 */
const view = ref<'list' | 'map'>('list')
/** 排序方式：recommend 推荐优先 / distance 距离优先 */
const sortType = ref<'recommend' | 'distance'>('recommend')
/** 当前选中路线 ID */
const selectedId = ref<number | null>(null)

/** 排序后的路线（距离优先按距离升序；推荐优先保持推荐顺序） */
const sortedRoutes = computed(() => {
  if (sortType.value === 'distance') {
    return [...routes.value].sort((a, b) => a.distance - b.distance)
  }
  return routes.value
})

const selectedRoute = computed(
  () => routes.value.find((r) => r.id === selectedId.value) ?? null,
)

/** 难度星级：★ 实心 + ☆ 空心，共 3 颗 */
function stars(difficulty: number): string {
  return '★'.repeat(difficulty) + '☆'.repeat(3 - difficulty)
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
    <!-- 视图切换 -->
    <div class="guide__switch">
      <button
        class="guide__switch-item"
        :class="{ 'is-active': view === 'list' }"
        @click="view = 'list'"
      >
        路线列表
      </button>
      <button
        class="guide__switch-item"
        :class="{ 'is-active': view === 'map' }"
        @click="view = 'map'"
      >
        地图视图
      </button>
    </div>

    <!-- 列表视图 -->
    <template v-if="view === 'list'">
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
        v-for="route in sortedRoutes"
        :key="route.id"
        class="guide__card"
        :class="{ 'is-selected': selectedId === route.id }"
        @click="selectRoute(route.id)"
      >
        <span v-if="selectedId === route.id" class="guide__card-check">✓</span>
        <div class="guide__card-main">
          <div class="guide__card-head">
            <h3 class="guide__card-name">{{ route.name }}</h3>
            <span class="guide__card-tag">{{ route.tag }}</span>
          </div>
          <p class="guide__card-desc">{{ route.desc }}</p>
          <div class="guide__card-meta">
            <span class="guide__card-stars">{{ stars(route.difficulty) }}</span>
            <span class="guide__card-duration">约 {{ route.duration }} 分钟</span>
            <span class="guide__card-spots">{{ route.spotCount }} 个景点</span>
            <span class="guide__card-distance">距您 {{ route.distance }}km</span>
          </div>
        </div>
      </div>
    </template>

    <!-- 地图视图（静态占位，地图能力后续接入） -->
    <div v-else class="guide__map">
      <div class="guide__map-placeholder">
        <p class="guide__map-icon">🗺️</p>
        <p class="guide__map-text">地图视图开发中</p>
        <p class="guide__map-sub">后续将在此展示路线在景区地图上的分布</p>
      </div>
      <div v-if="selectedRoute" class="guide__map-selected">
        当前选中：{{ selectedRoute.name }}
      </div>
    </div>

    <!-- 底部操作栏（位于 TabBar 上方） -->
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
  padding-bottom: 120px; // 为底部固定操作栏留空

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

  // 地图占位
  &__map {
    margin-top: @spacing-md;
  }

  &__map-placeholder {
    background: @color-bg-card;
    border-radius: @radius-lg;
    padding: 48px @spacing-md;
    text-align: center;
    box-shadow: @shadow-card;
  }

  &__map-icon {
    font-size: 48px;
    margin: 0 0 @spacing-sm;
  }

  &__map-text {
    font-size: @font-size-lg;
    font-weight: 600;
    color: @color-text-primary;
    margin: 0 0 @spacing-xs;
  }

  &__map-sub {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    margin: 0;
  }

  &__map-selected {
    margin-top: @spacing-md;
    text-align: center;
    font-size: @font-size-sm;
    color: @color-primary;
  }

  // 底部固定操作栏
  &__footer {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 56px; // TabBar 高度
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
