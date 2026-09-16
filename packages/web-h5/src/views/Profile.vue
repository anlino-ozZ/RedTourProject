<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  mockUser,
  mockMedals,
  mockFootprints,
  mockOrders,
  type MedalItem,
  type OrderItem,
} from '@/mock/profile'

const router = useRouter()
const user = mockUser
const medals = mockMedals
const footprintGroups = mockFootprints
const orders = ref(mockOrders)

const orderStatusColor: Record<OrderItem['status'], string> = {
  pending: '#faad14',
  paid: '#2f7cf6',
  shipped: '#fa8c16',
  done: '#52c41a',
  refund: '#8c8c8c',
}

// 订单 Tab 筛选
type OrderTab = 'all' | OrderItem['status']
const orderTabs: { key: OrderTab; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待付款' },
  { key: 'paid', label: '待发货' },
  { key: 'shipped', label: '待收货' },
  { key: 'done', label: '已完成' },
]
const activeTab = ref<OrderTab>('all')
const filteredOrders = computed(() =>
  activeTab.value === 'all'
    ? orders.value
    : orders.value.filter((o) => o.status === activeTab.value),
)

const allStats = computed(() => user.stats)

function onFootprintAction(to?: string) {
  if (to) router.push(to)
}

function medalColor(m: MedalItem) {
  if (!m.earned) return '#bfbfbf'
  return m.color ?? '#c41e3a'
}

function onSettings() {
  // TODO: 接入设置页
  router.push('/profile')
}

function onLogout() {
  // TODO: 接入登出接口 / 清 token
  router.push('/auth')
}
</script>

<template>
  <div class="profile">
    <!-- 顶部红色用户区（全宽，顶到状态栏下） -->
    <div class="profile__header">
      <div class="profile__actions">
        <button class="profile__icon-btn" title="设置" @click="onSettings">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3" />
            <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.6 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.6a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09A1.65 1.65 0 0015 4.6a1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9c.24.56.81.96 1.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z" />
          </svg>
        </button>
        <button class="profile__icon-btn" title="退出登录" @click="onLogout">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4" />
            <path d="M16 17l5-5-5-5" />
            <path d="M21 12H9" />
          </svg>
        </button>
      </div>
      <div class="profile__user">
        <div class="profile__avatar">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 12a5 5 0 100-10 5 5 0 000 10zm0 2c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5z" />
          </svg>
        </div>
        <div class="profile__user-info">
          <div class="profile__nickname">{{ user.nickname }}</div>
          <div class="profile__slogan">{{ user.slogan }}</div>
          <div class="profile__badges">
            <span class="profile__badge profile__badge--level">
              Lv {{ user.level }} {{ user.levelName }}
            </span>
            <span class="profile__badge profile__badge--medal">
              勋章 {{ user.medals.owned }}/{{ user.medals.total }}
            </span>
          </div>
        </div>
      </div>

      <div class="profile__stats">
        <div v-for="s in allStats" :key="s.label" class="profile__stat">
          <div class="profile__stat-value">{{ s.value }}</div>
          <div class="profile__stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <!-- 荣誉勋章墙 -->
    <section class="profile__card profile__medals">
      <div class="profile__section-title">
        <span>荣誉勋章墙</span>
        <span class="profile__arrow">›</span>
      </div>
      <div class="profile__medal-list">
        <div v-for="m in medals" :key="m.name" class="profile__medal">
          <div class="profile__medal-icon" :class="{ 'is-locked': !m.earned }" :style="{ color: medalColor(m) }">
            <svg v-if="m.icon === 'star'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2l2.9 6.3 6.9.8-5.1 4.7 1.4 6.8L12 17.8 5.9 20.6l1.4-6.8L2.2 9.1l6.9-.8L12 2z" />
            </svg>
            <svg v-else-if="m.icon === 'check'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10" fill="currentColor" stroke="none" opacity="0.12" />
              <path d="M8 12.5l2.8 2.8L16 10" />
            </svg>
            <svg v-else-if="m.icon === 'trophy'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M6 4h12v2a6 6 0 01-12 0V4zm0 2v0a4 4 0 008 0H6zm2 8h8l-1 6H9l-1-6zm-3-6h2v2a4 4 0 01-2-2zm14 0h-2v2a4 4 0 002-2z" />
            </svg>
            <svg v-else-if="m.icon === 'map'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2a7 7 0 00-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 00-7-7zm0 9.5A2.5 2.5 0 1112 6.5a2.5 2.5 0 010 5z" />
            </svg>
            <svg v-else-if="m.icon === 'fire'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M13.5 2s4.5 4 4.5 8.5a6 6 0 01-12 0c0-2 1-3.5 1-3.5s2 2.5 3 2.5c0-3 3.5-7.5 3.5-7.5zM12 22a5 5 0 01-5-5c0-2 1-3.5 2.5-4.5.5 1.5 1.5 2.5 2.5 3 .5-2 1.5-3 2.5-3.5C16 12.5 17 14 17 16a5 5 0 01-5 6z" />
            </svg>
            <svg v-else-if="m.icon === 'book'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M4 4h6a3 3 0 013 3v13a2 2 0 00-2-2H4V4zm16 0h-6a3 3 0 00-3 3v13a2 2 0 012-2h7V4z" />
            </svg>
            <svg v-else-if="m.icon === 'flag'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M5 3h2v18H5V3zm2 3h12l-2 4 2 4H7V6z" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="currentColor">
              <path d="M17 9V7a5 5 0 00-10 0v2H5v13h14V9h-2zm-8-2a3 3 0 016 0v2H9V7z" />
            </svg>
          </div>
          <div class="profile__medal-name">{{ m.name }}</div>
        </div>
      </div>
    </section>

    <!-- 我的订单 -->
    <section class="profile__card profile__orders">
      <div class="profile__section-title">
        <span>我的订单</span>
        <span class="profile__arrow">›</span>
      </div>
      <div class="profile__order-tabs">
        <span
          v-for="t in orderTabs"
          :key="t.key"
          class="profile__order-tab"
          :class="{ 'is-active': activeTab === t.key }"
          @click="activeTab = t.key"
        >{{ t.label }}</span>
      </div>
      <div class="profile__order-list">
        <div v-if="filteredOrders.length" v-for="o in filteredOrders" :key="o.id" class="profile__order-item">
          <div class="profile__order-cover">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M6 2l1.5 4h9L18 2M4 6h16l-1.5 13a2 2 0 01-2 1.7h-9A2 2 0 015.5 19L4 6z" />
              <path d="M9 10v6M15 10v6" />
            </svg>
          </div>
          <div class="profile__order-body">
            <div class="profile__order-top">
              <span class="profile__order-title">{{ o.title }}</span>
              <span class="profile__order-status" :style="{ color: orderStatusColor[o.status] }">{{ o.statusText }}</span>
            </div>
            <div class="profile__order-spec">{{ o.spec }}</div>
            <div class="profile__order-bottom">
              <span class="profile__order-date">{{ o.date }}</span>
              <span class="profile__order-price">¥{{ o.price }} <small>×{{ o.qty }}</small></span>
            </div>
          </div>
        </div>
        <div v-else class="profile__order-empty">暂无相关订单</div>
      </div>
    </section>

    <!-- 游览足迹 -->
    <section v-for="g in footprintGroups" :key="g.date" class="profile__footprint">
      <div class="profile__section-title">
        <span>游览足迹（{{ g.date }}）</span>
      </div>
      <div
        v-for="item in g.items"
        :key="item.id"
        class="profile__footprint-card"
      >
        <div class="profile__footprint-main">
          <div class="profile__footprint-title">{{ item.title }}</div>
          <div class="profile__footprint-meta">{{ item.meta }}</div>
          <div v-if="item.tags?.length" class="profile__footprint-tags">
            <span v-for="t in item.tags" :key="t" class="profile__tag">{{ t }}</span>
          </div>
          <div v-if="item.progress != null" class="profile__progress">
            <div class="profile__progress-bar" :style="{ width: item.progress + '%' }" />
          </div>
        </div>
        <div v-if="item.actionText" class="profile__footprint-action">
          <button class="profile__btn-action" @click="onFootprintAction(item.actionTo)">
            {{ item.actionText }} <span class="profile__arrow-sm">›</span>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.profile {
  // 抵消 Layout main 的 16px 内边距，让红色头部铺满到顶
  margin: -@spacing-md;
  padding-bottom: @spacing-md;

  &__header {
    position: relative;
    background: linear-gradient(160deg, @color-primary 0%, @color-primary-active 100%);
    padding: calc(env(safe-area-inset-top) + 18px) @spacing-md @spacing-lg;
    border-bottom-left-radius: 24px;
    border-bottom-right-radius: 24px;
    color: #fff;
  }

  &__actions {
    position: absolute;
    top: calc(env(safe-area-inset-top) + 12px);
    right: @spacing-md;
    display: flex;
    gap: @spacing-sm;
  }

  &__icon-btn {
    width: 34px;
    height: 34px;
    border-radius: 50%;
    border: none;
    background: rgba(255, 255, 255, 0.18);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: background 0.15s;

    svg {
      width: 18px;
      height: 18px;
    }

    &:active {
      background: rgba(255, 255, 255, 0.3);
    }
  }

  &__user {
    display: flex;
    align-items: flex-start;
    gap: @spacing-md;
  }

  &__avatar {
    flex-shrink: 0;
    width: 56px;
    height: 56px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.22);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 2px solid rgba(255, 255, 255, 0.5);

    svg {
      width: 32px;
      height: 32px;
    }
  }

  &__user-info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__nickname {
    font-size: 20px;
    font-weight: 700;
    line-height: 1.2;
  }

  &__slogan {
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.85);
  }

  &__badges {
    display: flex;
    flex-wrap: wrap;
    gap: @spacing-sm;
    margin-top: @spacing-xs;
  }

  &__badge {
    font-size: @font-size-sm;
    line-height: 1;
    padding: 4px 10px;
    border-radius: 10px;
    white-space: nowrap;
  }

  &__badge--level {
    background: linear-gradient(90deg, #f5cd6b, @color-accent);
    color: #7a5a10;
    font-weight: 700;
  }

  &__badge--medal {
    background: rgba(255, 255, 255, 0.18);
    color: #fff;
    font-weight: 600;
  }

  &__stats {
    display: flex;
    gap: @spacing-sm;
    margin-top: @spacing-lg;
  }

  &__stat {
    flex: 1;
    background: rgba(255, 255, 255, 0.16);
    border-radius: @radius-lg;
    padding: @spacing-sm 0;
    text-align: center;
    backdrop-filter: blur(6px);
  }

  &__stat-value {
    font-size: 22px;
    font-weight: 800;
    line-height: 1.1;
    color: #fff;
  }

  &__stat-label {
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.82);
    margin-top: 2px;
  }

  // ===== 通用卡片 =====
  &__card {
    background: @color-bg-card;
    margin: @spacing-md;
    border-radius: @radius-lg;
    box-shadow: @shadow-card;
  }

  &__section-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: @spacing-md;
    font-size: @font-size-lg;
    font-weight: 700;
    color: @color-text-primary;
  }

  &__arrow {
    color: @color-text-secondary;
    font-size: 20px;
    line-height: 1;
  }

  // ===== 勋章 =====
  &__medal-list {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: @spacing-md @spacing-sm;
    padding: 0 @spacing-md @spacing-md;
  }

  &__medal {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: @spacing-xs;
  }

  &__medal-icon {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    background: @color-bg-page;
    display: flex;
    align-items: center;
    justify-content: center;

    svg {
      width: 26px;
      height: 26px;
    }

    &.is-locked {
      background: #f0f0f2;
    }
  }

  &__medal-name {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  // ===== 订单 =====
  &__order-tabs {
    display: flex;
    gap: @spacing-sm;
    padding: 0 @spacing-md @spacing-md;
    border-bottom: 1px solid #f2f2f4;
    margin: 0 @spacing-md;
  }

  &__order-tab {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    padding: 4px 0;
    position: relative;
    cursor: pointer;

    &.is-active {
      color: @color-primary;
      font-weight: 600;

      &::after {
        content: '';
        position: absolute;
        left: 50%;
        bottom: -9px;
        transform: translateX(-50%);
        width: 16px;
        height: 2px;
        border-radius: 2px;
        background: @color-primary;
      }
    }
  }

  &__order-list {
    padding: @spacing-xs @spacing-md @spacing-md;
  }

  &__order-item {
    display: flex;
    gap: @spacing-md;
    padding: @spacing-md 0;
    border-bottom: 1px solid #f5f5f7;

    &:last-child {
      border-bottom: none;
    }
  }

  &__order-cover {
    flex-shrink: 0;
    width: 56px;
    height: 56px;
    border-radius: @radius-base;
    background: @color-bg-page;
    color: @color-primary;
    display: flex;
    align-items: center;
    justify-content: center;

    svg {
      width: 30px;
      height: 30px;
    }
  }

  &__order-body {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__order-top {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: @spacing-sm;
  }

  &__order-title {
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__order-status {
    font-size: @font-size-sm;
    font-weight: 600;
    flex-shrink: 0;
  }

  &__order-spec {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__order-bottom {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: auto;
    padding-top: @spacing-xs;
  }

  &__order-date {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__order-price {
    font-size: @font-size-base;
    font-weight: 700;
    color: @color-primary;

    small {
      font-size: @font-size-sm;
      font-weight: 400;
      color: @color-text-secondary;
    }
  }

  &__order-empty {
    text-align: center;
    color: @color-text-secondary;
    font-size: @font-size-sm;
    padding: @spacing-lg 0;
  }

  // ===== 足迹 =====
  &__footprint {
    margin-top: @spacing-sm;
  }

  &__footprint-card {
    margin: 0 @spacing-md @spacing-md;
    background: @color-bg-card;
    border-radius: @radius-lg;
    padding: @spacing-md;
    box-shadow: @shadow-card;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: @spacing-md;
  }

  &__footprint-main {
    flex: 1;
    min-width: 0;
  }

  &__footprint-title {
    font-size: @font-size-lg;
    font-weight: 700;
    color: @color-text-primary;
    margin-bottom: @spacing-xs;
  }

  &__footprint-meta {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    margin-bottom: @spacing-sm;
  }

  &__footprint-tags {
    display: flex;
    flex-wrap: wrap;
    gap: @spacing-xs;
  }

  &__tag {
    font-size: @font-size-sm;
    line-height: 1;
    padding: 4px 10px;
    border-radius: 10px;
    background: @color-primary-light;
    color: @color-primary;
    font-weight: 500;
  }

  &__progress {
    height: 6px;
    background: #f0f0f2;
    border-radius: 3px;
    overflow: hidden;
    margin-top: 2px;
  }

  &__progress-bar {
    height: 100%;
    background: linear-gradient(90deg, @color-primary, @color-warning);
    border-radius: 3px;
    transition: width 0.4s;
  }

  &__footprint-action {
    flex-shrink: 0;
  }

  &__btn-action {
    border: none;
    background: @color-primary-light;
    color: @color-primary;
    font-size: @font-size-sm;
    font-weight: 600;
    padding: 6px 12px;
    border-radius: 14px;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 2px;

    &:active {
      background: fade(@color-primary, 18%);
    }
  }

  &__arrow-sm {
    font-size: 16px;
    line-height: 1;
  }
}
</style>
