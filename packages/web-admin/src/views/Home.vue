<script setup lang="ts">
// PC管理后台 首页概览：欢迎条 + 核心数据统计卡 + 快捷入口
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  getAdminScenicAreas,
  getAdminRoutes,
  getAdminCheckpoints,
  getAdminProducts,
  getAdminProductOrders,
  getAdminArtifacts,
  getAdminUsers,
} from '@/api/admin'
import { useUserStore } from '@/store'
import MenuIcon from '@/components/MenuIcon.vue'

const router = useRouter()
const userStore = useUserStore()

// ===== 统计卡片 =====
interface StatMeta {
  key: string
  title: string
  icon: string
  path: string
  color: string
}
const statMetaList: StatMeta[] = [
  { key: 'scenic', title: '景区总数', icon: 'map-pin', path: '/scenic', color: '#c41e3a' },
  { key: 'route', title: '游览路线', icon: 'map', path: '/route', color: '#d4af37' },
  { key: 'checkpoint', title: '打卡点位', icon: 'flag', path: '/checkpoint', color: '#2f9e6f' },
  { key: 'product', title: '特产文创', icon: 'gift', path: '/product', color: '#e07b39' },
  { key: 'order', title: '意向订单', icon: 'file-text', path: '/order', color: '#4a7dd6' },
  { key: 'artifact', title: '在册文物', icon: 'archive', path: '/artifact', color: '#8a5cd1' },
]

// 值为 null 表示尚未取到数据（接口失败/加载中），UI 显示 “—”
const statValues = ref<Record<string, number | null>>(
  statMetaList.reduce<Record<string, number | null>>((acc, item) => {
    acc[item.key] = null
    return acc
  }, {}),
)

// 只取 1 条数据，用 total 作为总数，避免拉取全量列表
const loaders: Record<string, () => Promise<number>> = {
  scenic: async () => (await getAdminScenicAreas({ page: 1, pageSize: 1 })).total,
  route: async () => (await getAdminRoutes({ page: 1, pageSize: 1 })).total,
  checkpoint: async () => (await getAdminCheckpoints({ page: 1, pageSize: 1 })).total,
  product: async () => (await getAdminProducts({ page: 1, pageSize: 1 })).total,
  order: async () => (await getAdminProductOrders({ page: 1, pageSize: 1 })).total,
  artifact: async () => (await getAdminArtifacts({ page: 1, pageSize: 1 })).total,
}

async function loadStats() {
  // 用户数为超管专属，普管不展示也不请求
  const entries = Object.entries(loaders)
  if (userStore.isSuperAdmin) {
    entries.push(['user', async () => (await getAdminUsers({ page: 1, pageSize: 1 })).total])
  }

  // 任一接口失败不影响其他卡片（错误提示由 axios 拦截器统一弹出）
  await Promise.all(
    entries.map(async ([key, loader]) => {
      try {
        statValues.value[key] = await loader()
      } catch {
        statValues.value[key] = null
      }
    }),
  )
}

const statCards = computed(() => {
  const list = [...statMetaList]
  if (userStore.isSuperAdmin) {
    list.push({ key: 'user', title: '管理员账号', icon: 'users', path: '/user', color: '#3aa0a0' })
  }
  return list.map((meta) => ({
    ...meta,
    value: statValues.value[meta.key] ?? null,
  }))
})

// ===== 快捷入口 =====
const quickEntries = computed(() => {
  const list = [
    { title: '知识库管理', desc: '素材上传与向量编译', icon: 'book', path: '/wiki', color: '#4a7dd6' },
    { title: '剧本管理', desc: '红色剧本与角色配置', icon: 'edit', path: '/script', color: '#c41e3a' },
    { title: '数据统计', desc: '客流与热点分析', icon: 'chart', path: '/stats', color: '#2f9e6f' },
    { title: '设备监控', desc: '边缘设备在线状态', icon: 'monitor', path: '/device', color: '#e07b39' },
  ]
  if (userStore.isSuperAdmin) {
    list.push({
      title: '成就定义管理',
      desc: '勋章与成就规则配置',
      icon: 'award',
      path: '/achievement',
      color: '#d4af37',
    })
  }
  return list
})

// ===== 欢迎信息 =====
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() =>
  new Date().toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }),
)

const roleLabel = computed(() => (userStore.role === 'super_admin' ? '超级管理员' : '普通管理员'))

function go(path: string) {
  router.push(path)
}

onMounted(loadStats)
</script>

<template>
  <div class="home">
    <!-- 欢迎条 -->
    <div class="home__hero">
      <div class="home__hero-text">
        <p class="home__hero-greeting">{{ greeting }}，{{ userStore.displayName || '管理员' }}</p>
        <h2 class="home__hero-title">欢迎使用红色文旅智能导览管理后台</h2>
        <p class="home__hero-meta">
          <span>{{ todayText }}</span>
          <span class="home__hero-dot">·</span>
          <span>{{ roleLabel }}</span>
        </p>
      </div>
      <div class="home__hero-badge">
        <MenuIcon name="sparkles" :size="28" />
      </div>
    </div>

    <!-- 核心数据统计 -->
    <div class="home__section">
      <div class="home__section-head">
        <h3 class="home__section-title">核心数据</h3>
        <span class="home__section-desc">点击卡片进入对应管理页</span>
      </div>
      <div class="home__stats">
        <div
          v-for="card in statCards"
          :key="card.key"
          class="home__stat"
          @click="go(card.path)"
        >
          <span class="home__stat-icon" :style="{ color: card.color, background: `${card.color}1a` }">
            <MenuIcon :name="card.icon" :size="20" />
          </span>
          <div class="home__stat-body">
            <p class="home__stat-value">{{ card.value === null ? '—' : card.value }}</p>
            <p class="home__stat-title">{{ card.title }}</p>
          </div>
          <MenuIcon name="arrowRight" :size="16" class="home__stat-arrow" />
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="home__section">
      <div class="home__section-head">
        <h3 class="home__section-title">快捷入口</h3>
      </div>
      <div class="home__quick">
        <div
          v-for="item in quickEntries"
          :key="item.path"
          class="home__quick-item"
          @click="go(item.path)"
        >
          <span
            class="home__quick-icon"
            :style="{ color: item.color, background: `${item.color}1a` }"
          >
            <MenuIcon :name="item.icon" :size="20" />
          </span>
          <div class="home__quick-body">
            <p class="home__quick-title">{{ item.title }}</p>
            <p class="home__quick-desc">{{ item.desc }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.home {
  &__hero {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: @spacing-lg;
    padding: @spacing-lg @spacing-xl;
    border-radius: @radius-lg;
    color: #fff;
    background: linear-gradient(120deg, @color-primary-active 0%, @color-primary 55%, #e0354f 100%);
    box-shadow: 0 8px 24px rgba(196, 30, 58, 0.22);
  }

  &__hero-text {
    min-width: 0;
  }

  &__hero-greeting {
    margin: 0 0 @spacing-xs;
    font-size: @font-size-base;
    color: rgba(255, 255, 255, 0.82);
  }

  &__hero-title {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
    letter-spacing: 0.5px;
  }

  &__hero-meta {
    margin: @spacing-sm 0 0;
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.78);
  }

  &__hero-dot {
    margin: 0 @spacing-xs;
  }

  &__hero-badge {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 72px;
    height: 72px;
    flex: none;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.16);
    color: #fff;
  }

  // ===== 区块 =====
  &__section {
    margin-top: @spacing-xl;
  }

  &__section-head {
    display: flex;
    align-items: baseline;
    gap: @spacing-sm;
    margin-bottom: @spacing-md;
  }

  &__section-title {
    position: relative;
    margin: 0;
    padding-left: 10px;
    font-size: @font-size-lg;
    font-weight: 600;
    color: @color-text-primary;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 14px;
      border-radius: 2px;
      background: @color-primary;
    }
  }

  &__section-desc {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  // ===== 统计卡片 =====
  &__stats {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: @spacing-md;
  }

  &__stat {
    display: flex;
    align-items: center;
    gap: @spacing-md;
    padding: @spacing-md;
    background: #fafbfc;
    border: 1px solid #f0f0f2;
    border-radius: @radius-base;
    cursor: pointer;
    transition: box-shadow 0.25s ease, transform 0.25s ease, border-color 0.25s ease,
      background 0.25s ease;

    &:hover {
      background: @color-bg-card;
      transform: translateY(-2px);
      border-color: @color-primary-light;
      box-shadow: 0 6px 18px rgba(196, 30, 58, 0.12);

      .home__stat-arrow {
        opacity: 1;
        transform: translateX(0);
      }
    }
  }

  &__stat-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    flex: none;
    border-radius: @radius-base;
  }

  &__stat-body {
    min-width: 0;
  }

  &__stat-value {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
    line-height: 1.2;
    color: @color-text-primary;
  }

  &__stat-title {
    margin: 2px 0 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__stat-arrow {
    margin-left: auto;
    color: @color-text-secondary;
    opacity: 0;
    transform: translateX(-4px);
    transition: opacity 0.25s ease, transform 0.25s ease;
  }

  // ===== 快捷入口 =====
  &__quick {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    gap: @spacing-md;
  }

  &__quick-item {
    display: flex;
    align-items: center;
    gap: @spacing-md;
    padding: @spacing-md;
    border: 1px solid #f0f0f2;
    border-radius: @radius-base;
    background: #fafbfc;
    cursor: pointer;
    transition: box-shadow 0.25s ease, transform 0.25s ease, border-color 0.25s ease,
      background 0.25s ease;

    &:hover {
      background: @color-bg-card;
      transform: translateY(-2px);
      border-color: @color-primary-light;
      box-shadow: 0 6px 18px rgba(31, 31, 31, 0.08);
    }
  }

  &__quick-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    flex: none;
    border-radius: @radius-base;
  }

  &__quick-body {
    min-width: 0;
  }

  &__quick-title {
    margin: 0;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
  }

  &__quick-desc {
    margin: 2px 0 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }
}
</style>
