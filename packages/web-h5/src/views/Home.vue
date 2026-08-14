<script setup lang="ts">
// 游客移动端H5 首页
import { ref } from 'vue'
import { RedCard, RedButton } from '@red-tour-project/common'
import { useRouter } from 'vue-router'

const router = useRouter()

// 景区列表（静态 mock）
const scenicAreas = ref([
  { id: 1, name: '井冈山革命根据地' },
  { id: 2, name: '延安革命纪念馆' },
  { id: 3, name: '西柏坡纪念馆' },
  { id: 4, name: '遵义会议会址' },
])
const selectedArea = ref(1)

// 推荐路线（静态 mock）
const routes = ref([
  {
    id: 1,
    title: '红色足迹·经典之旅',
    desc: '3天2夜 · 革命圣地深度游',
    spots: 8,
    distance: '12km',
  },
  {
    id: 2,
    title: '革命薪火·研学之路',
    desc: '2天1夜 · 党史学习主题路线',
    spots: 6,
    distance: '8km',
  },
])

// 热门景点（静态 mock）
const hotSpots = ref([
  { id: 1, name: '黄洋界', tag: '战役遗址' },
  { id: 2, name: '八角楼', tag: '旧址' },
  { id: 3, name: '革命博物馆', tag: '博物馆' },
  { id: 4, name: '烈士陵园', tag: '纪念地' },
  { id: 5, name: '会师广场', tag: '纪念碑' },
])

function startTour() {
  router.push('/guide')
}
</script>

<template>
  <div class="home">
    <!-- 顶部 Banner -->
    <div class="home__banner">
      <h1 class="home__banner-title">红色文旅</h1>
      <p class="home__banner-subtitle">智能导览 · 随时随地探索红色足迹</p>
    </div>

    <!-- 景区选择下拉 -->
    <div class="home__section">
      <div class="home__section-head">
        <span class="home__section-title">选择景区</span>
      </div>
      <div class="home__area-select">
        <select v-model="selectedArea" class="home__select">
          <option v-for="area in scenicAreas" :key="area.id" :value="area.id">
            {{ area.name }}
          </option>
        </select>
        <span class="home__select-arrow">▾</span>
      </div>
    </div>

    <!-- 推荐路线 -->
    <div class="home__section">
      <div class="home__section-head">
        <span class="home__section-title">推荐路线</span>
        <span class="home__section-more" @click="startTour">全部 ›</span>
      </div>
      <div class="home__routes">
        <RedCard v-for="route in routes" :key="route.id" shadow="hover" class="home__route-card">
          <div class="home__route-info">
            <h3 class="home__route-title">{{ route.title }}</h3>
            <p class="home__route-desc">{{ route.desc }}</p>
            <div class="home__route-meta">
              <span class="home__route-tag">{{ route.spots }} 个景点</span>
              <span class="home__route-tag">{{ route.distance }}</span>
            </div>
          </div>
          <RedButton type="primary" size="small" @click="startTour">查看路线</RedButton>
        </RedCard>
      </div>
    </div>

    <!-- 热门景点横向滚动 -->
    <div class="home__section">
      <div class="home__section-head">
        <span class="home__section-title">热门景点</span>
        <span class="home__section-more" @click="startTour">全部 ›</span>
      </div>
      <div class="home__spots-scroll">
        <div v-for="spot in hotSpots" :key="spot.id" class="home__spot-item" @click="startTour">
          <div class="home__spot-img">{{ spot.name.charAt(0) }}</div>
          <span class="home__spot-name">{{ spot.name }}</span>
          <span class="home__spot-tag">{{ spot.tag }}</span>
        </div>
      </div>
    </div>

    <!-- 问答入口大按钮 -->
    <div class="home__qa-entry" @click="router.push('/qa')">
      <div class="home__qa-text">
        <span class="home__qa-title">智能问答</span>
        <span class="home__qa-desc">向 AI 提问，了解党史与红色文化</span>
      </div>
      <span class="home__qa-arrow">→</span>
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.home {
  overflow-x: hidden; // 防止负 margin 导致的横向溢出

  &__banner {
    margin: -@spacing-sm -@spacing-md @spacing-md;
    padding: @spacing-xl @spacing-lg;
    background: linear-gradient(135deg, @color-primary 0%, @color-primary-active 100%);
    color: #fff;
    text-align: center;
    border-radius: 0 0 @radius-lg @radius-lg;
  }
  &__banner-title {
    font-size: 24px;
    font-weight: 700;
    letter-spacing: 2px;
    margin-bottom: @spacing-xs;
  }
  &__banner-subtitle {
    font-size: @font-size-sm;
    opacity: 0.9;
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
    color: @color-text-secondary;
    cursor: pointer;
  }

  // 景区选择下拉
  &__area-select {
    position: relative;
  }
  &__select {
    width: 100%;
    height: 44px;
    padding: 0 @spacing-lg 0 @spacing-md;
    border: 1px solid @color-border;
    border-radius: @radius-base;
    background: @color-bg-card;
    font-size: @font-size-base;
    color: @color-text-primary;
    appearance: none;
    cursor: pointer;
    &:focus {
      border-color: @color-primary;
      outline: none;
    }
  }
  &__select-arrow {
    position: absolute;
    right: @spacing-md;
    top: 50%;
    transform: translateY(-50%);
    color: @color-text-secondary;
    pointer-events: none;
  }

  // 推荐路线
  &__routes {
    display: flex;
    flex-direction: column;
    gap: @spacing-md;
  }
  &__route-card {
    :deep(.rt-card__body) {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: @spacing-md;
    }
  }
  &__route-info {
    flex: 1;
  }
  &__route-title {
    font-size: @font-size-lg;
    font-weight: 600;
    color: @color-text-primary;
    margin-bottom: @spacing-xs;
  }
  &__route-desc {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    margin-bottom: @spacing-sm;
  }
  &__route-meta {
    display: flex;
    gap: @spacing-sm;
  }
  &__route-tag {
    font-size: @font-size-sm;
    color: @color-primary;
    background: @color-primary-light;
    padding: 2px @spacing-sm;
    border-radius: @radius-sm;
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

  // 问答入口大按钮
  &__qa-entry {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: @spacing-lg @spacing-md;
    margin-bottom: @spacing-md;
    background: linear-gradient(135deg, @color-primary 0%, @color-primary-active 100%);
    border-radius: @radius-lg;
    color: #fff;
    cursor: pointer;
    transition: opacity 0.2s;
    &:active {
      opacity: 0.85;
    }
  }
  &__qa-text {
    display: flex;
    flex-direction: column;
    gap: @spacing-xs;
  }
  &__qa-title {
    font-size: @font-size-lg;
    font-weight: 700;
  }
  &__qa-desc {
    font-size: @font-size-sm;
    opacity: 0.9;
  }
  &__qa-arrow {
    font-size: 20px;
    font-weight: 700;
  }
}
</style>
