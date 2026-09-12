<script setup lang="ts">
// 触摸屏大屏首页：三大入口（语音提问 / 姿态互动 / 快捷信息）
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { getScenicArea } from '@/api'

const router = useRouter()

// 当前绑定景区名称（大字显示）
const scenicName = ref<string>('红色筑梦之旅')
const scenicId = Number(import.meta.env.VITE_TOUCH_SCENIC_AREA_ID) || 1

// 设备位置 / 编号（由设备配置写入环境变量）
const location = import.meta.env.VITE_TOUCH_LOCATION || '主展厅 01 号位'
const deviceNo = import.meta.env.VITE_TOUCH_DEVICE_NO || 'RT-TS-05'

async function loadScenicName() {
  try {
    const data = await getScenicArea(scenicId)
    if (data?.name) scenicName.value = data.name
  } catch {
    // 接口失败时使用兜底名称
    scenicName.value = '红色筑梦之旅'
  }
}

// 三大入口
const entries = [
  { key: 'voice', label: '语音提问' },
  { key: 'pose', label: '姿态互动' },
  { key: 'quick', label: '快捷信息' },
] as const

function goEntry(key: (typeof entries)[number]['key']) {
  if (key === 'voice') router.push('/voice-ask')
  else if (key === 'pose') router.push('/pose-interaction')
  else router.push('/quick-info')
}

// 页脚实时日期时间：2026年05月08日 | 星期五 | 10:45 AM
const now = ref(new Date())
let clockTimer: ReturnType<typeof setInterval> | null = null
const weekMap = ['日', '一', '二', '三', '四', '五', '六']
const dateText = computed(() => {
  const d = now.value
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const week = weekMap[d.getDay()]
  const isAm = d.getHours() < 12
  const h12 = d.getHours() % 12 || 12
  const hh = String(h12).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${y}年${m}月${day}日 | 星期${week} | ${hh}:${mm} ${isAm ? 'AM' : 'PM'}`
})

onMounted(() => {
  loadScenicName()
  clockTimer = setInterval(() => {
    now.value = new Date()
  }, 1000)
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<template>
  <div class="touch-home">
    <div class="touch-home__frame">
      <!-- 顶部：Logo + 景区名称（大字）+ 系统副标题 + 欢迎语 -->
      <header class="touch-home__header">
        <div class="touch-home__brand">
          <div class="touch-home__logo">
            <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="#c41e3a" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 3v18" />
              <path d="M5 4h11l-2.5 3L16 10H5" />
            </svg>
          </div>
          <div class="touch-home__heading">
            <h1 class="touch-home__title">{{ scenicName }}</h1>
            <span class="touch-home__subtitle">智能导览系统 2.0</span>
          </div>
        </div>
        <p class="touch-home__welcome">
          我是您的 AI 导览助手，您可以随时通过语音或手势与我互动，探索那些波澜壮阔的历史瞬间。
        </p>
      </header>

      <!-- 三大入口：超大红底金边卡片，按钮 ≥ 200×80px，字号 ≥ 3rem -->
      <main class="touch-home__actions">
        <button
          v-for="entry in entries"
          :key="entry.key"
          class="entry-card"
          type="button"
          @click="goEntry(entry.key)"
        >
          <span class="entry-card__icon">
            <!-- 语音提问：麦克风 -->
            <svg v-if="entry.key === 'voice'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <rect x="9" y="2" width="6" height="12" rx="3" />
              <path d="M5 10a7 7 0 0 0 14 0" />
              <path d="M12 17v4" />
              <path d="M8 21h8" />
            </svg>
            <!-- 姿态互动：交叉双剑 -->
            <svg v-else-if="entry.key === 'pose'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4.5 4.5 19 19" />
              <path d="M19.5 4.5 5 19" />
              <path d="M16 2h6v6" />
              <path d="M16 8 21 3" />
              <path d="M8 2H2v6" />
              <path d="M8 8 3 3" />
            </svg>
            <!-- 快捷信息：信息牌 -->
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="4" width="18" height="16" rx="2" />
              <path d="M7 8h6" />
              <path d="M7 12h10" />
              <path d="M7 16h10" />
            </svg>
          </span>
          <span class="entry-card__label">{{ entry.label }}</span>
        </button>
      </main>

      <!-- 底部：设备信息 + 实时日期时间 -->
      <footer class="touch-home__footer">
        <div class="touch-home__device">
          <span>当前地点：{{ location }}</span>
          <span class="touch-home__device-sep">设备编号：{{ deviceNo }}</span>
        </div>
        <div class="touch-home__date">{{ dateText }}</div>
      </footer>
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.touch-home {
  width: 100%;
  height: 100%;
  padding: 28px;
  background-color: #000;
  box-sizing: border-box;

  &__frame {
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    padding: 56px 72px 32px;
    box-sizing: border-box;
    border-radius: 16px;
    border: 1px solid rgba(196, 30, 58, 0.45);
    background: linear-gradient(180deg, #262626 0%, #1a1a1a 55%, #121212 100%);
    box-shadow:
      inset 0 0 90px rgba(196, 30, 58, 0.28),
      0 0 60px rgba(196, 30, 58, 0.35);
  }

  // ===== 顶部品牌区 =====
  &__header {
    flex-shrink: 0;
  }
  &__brand {
    display: flex;
    align-items: center;
    gap: 28px;
  }
  &__logo {
    flex-shrink: 0;
    width: 88px;
    height: 88px;
    border-radius: 50%;
    background-color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 0 24px rgba(255, 255, 255, 0.25);
  }
  &__heading {
    display: flex;
    align-items: baseline;
    gap: 24px;
  }
  // 景区名称大字显示
  &__title {
    margin: 0;
    font-size: 4.5rem;
    font-weight: 800;
    font-style: italic;
    letter-spacing: 6px;
    color: #fff;
    text-shadow: 0 2px 16px rgba(0, 0, 0, 0.45);
  }
  &__subtitle {
    font-size: 2rem;
    font-weight: 600;
    letter-spacing: 4px;
    color: @color-accent;
  }
  &__welcome {
    margin: 28px 0 0;
    max-width: 1100px;
    font-size: 1.6rem;
    line-height: 1.9;
    color: #d2d2d2;
  }

  // ===== 三大入口卡片 =====
  &__actions {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 48px;
    padding: 40px 0;
  }
}

.entry-card {
  flex: 1;
  // 按钮尺寸 ≥ 200×80px
  min-width: 200px;
  min-height: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 28px;
  padding: 32px 24px;
  border: 2px solid @color-touch-gold;
  border-radius: 24px;
  background: linear-gradient(160deg, #d3222f 0%, @color-primary 45%, #9e1530 100%);
  box-shadow:
    0 0 24px rgba(245, 197, 66, 0.22),
    inset 0 0 36px rgba(0, 0, 0, 0.22);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease;

  &__icon {
    color: @color-touch-gold;
    display: flex;
    svg {
      width: 96px;
      height: 96px;
    }
  }
  // 字号 ≥ 3rem
  &__label {
    font-size: 3rem;
    font-weight: 700;
    letter-spacing: 6px;
    color: #fff;
    text-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
  }

  &:hover {
    transform: translateY(-8px);
    filter: brightness(1.08);
    box-shadow:
      0 12px 40px rgba(245, 197, 66, 0.38),
      0 0 32px rgba(196, 30, 58, 0.55),
      inset 0 0 36px rgba(0, 0, 0, 0.18);
  }
  &:active {
    transform: translateY(-2px);
    filter: brightness(0.96);
  }
}

.touch-home {
  // ===== 底部信息栏 =====
  &__footer {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 1.15rem;
    color: @color-touch-text-secondary;
  }
  &__device {
    display: flex;
    align-items: center;
    gap: 56px;
  }
  &__date {
    letter-spacing: 1px;
  }
}
</style>
