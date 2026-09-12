<script setup lang="ts">
// 快捷信息页（W-T-02）：景区简介 + 开放时间 + 卫生间/充电桩等设施地图标记
// 数据来自 Wiki 条目接口（tags: scenic-intro / open-hours / facility），接口不可用时本地兜底
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getWikiList, getWikiDetail } from '@/api'
import type { WikiEntry } from '@red-tour-project/common'

const router = useRouter()
function goHome() {
  router.push('/')
}

// ===== 标签页 =====
type TabKey = 'intro' | 'hours' | 'facility'
const activeTab = ref<TabKey>('intro')
const tabs = [
  { key: 'intro', label: '景区简介' },
  { key: 'hours', label: '开放时间' },
  { key: 'facility', label: '设施导览' },
] as const

// ===== 设施点位 =====
interface FacilityPoint {
  name: string
  type: string // toilet / charging / parking / medical / service
  x: number // 0-100，相对地图区域百分比
  y: number
}
const FACILITY_META: Record<string, { label: string; color: string }> = {
  toilet: { label: '卫生间', color: '#4aa3ff' },
  charging: { label: '充电桩', color: '#35c46f' },
  parking: { label: '停车场', color: '#f5a623' },
  medical: { label: '医务室', color: '#ff5c5c' },
  service: { label: '服务台', color: '#f5c542' },
}
function pointMeta(type: string) {
  return FACILITY_META[type] ?? FACILITY_META.service
}

// ===== 页面数据 =====
const loading = ref(true)
const introText = ref('')
const openHoursText = ref('')
const facilityPoints = ref<FacilityPoint[]>([])
const selectedPoint = ref<FacilityPoint | null>(null)

// 接口不可用时的本地兜底数据（MVP / 后端未就绪）
const MOCK = {
  intro:
    '红色筑梦之旅景区位于罗霄山脉西麓，是北伐先锋队曾经浴血奋战的红色热土。景区内保存有主展厅、纪念馆、旧居旧址等 12 处革命历史遗存，馆藏文物 800 余件。\n景区以"重走筑梦路、传承赤子心"为主题，提供 AI 智能导览、姿态互动、剧本研学等特色体验，是集红色教育、历史研学、文化休闲于一体的爱国主义教育基地。',
  openHours:
    '开放日：周二至周日（周一闭馆检修，法定节假日除外）\n旺季（4月—10月）：09:00 — 17:30（17:00 停止入园）\n淡季（11月—3月）：09:00 — 17:00（16:30 停止入园）\n咨询服务电话：0791-88888888',
  facility: [
    { name: '主展厅一层卫生间', type: 'toilet', x: 18, y: 30 },
    { name: '纪念馆二层卫生间', type: 'toilet', x: 72, y: 22 },
    { name: '东门充电桩', type: 'charging', x: 88, y: 62 },
    { name: '游客停车场充电桩', type: 'charging', x: 12, y: 78 },
    { name: '医务室', type: 'medical', x: 50, y: 68 },
    { name: '游客服务台', type: 'service', x: 42, y: 50 },
  ] as FacilityPoint[],
}

const scenicId = Number(import.meta.env.VITE_TOUCH_SCENIC_AREA_ID) || 1

function useMock() {
  introText.value = MOCK.intro
  openHoursText.value = MOCK.openHours
  facilityPoints.value = MOCK.facility
}

/** 设施条目 content 为 JSON 点位数组，解析并校验 */
function parsePoints(raw: string): FacilityPoint[] {
  try {
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr)) return []
    return arr
      .filter(
        (p) =>
          p &&
          typeof p.name === 'string' &&
          Number.isFinite(Number(p.x)) &&
          Number.isFinite(Number(p.y)),
      )
      .map((p) => ({
        name: p.name,
        type: String(p.type || 'service'),
        x: Math.min(96, Math.max(4, Number(p.x))),
        y: Math.min(96, Math.max(4, Number(p.y))),
      }))
  } catch {
    return []
  }
}

async function loadData() {
  loading.value = true
  selectedPoint.value = null
  try {
    const res = await getWikiList({ scenicAreaId: scenicId, page: 1, pageSize: 100 })
    const entries: WikiEntry[] = Array.isArray(res) ? res : (res?.list ?? [])
    const findByTag = (tag: string) => entries.find((e) => (e.tags ?? []).includes(tag))

    // 列表返回已含 content 则直接使用，否则再拉详情
    const fetchContent = async (entry?: WikiEntry): Promise<string> => {
      if (!entry) return ''
      if (entry.content) return entry.content
      try {
        const detail = await getWikiDetail(entry.id)
        return detail?.content ?? ''
      } catch {
        return ''
      }
    }

    const [intro, hours, facilityRaw] = await Promise.all([
      fetchContent(findByTag('scenic-intro')),
      fetchContent(findByTag('open-hours')),
      fetchContent(findByTag('facility')),
    ])

    const points = parsePoints(facilityRaw)
    if (intro || hours || points.length) {
      introText.value = intro
      openHoursText.value = hours
      facilityPoints.value = points
    } else {
      useMock()
    }
  } catch {
    useMock()
  } finally {
    loading.value = false
  }
}

onMounted(loadData)

// 图例：仅展示地图上实际存在的设施类型
const legendItems = computed(() => {
  const types = [...new Set(facilityPoints.value.map((p) => p.type))]
  return types.map((t) => ({ type: t, ...pointMeta(t) }))
})

function selectPoint(p: FacilityPoint) {
  selectedPoint.value = selectedPoint.value?.name === p.name ? null : p
}
</script>

<template>
  <div class="sub-page">
    <header class="sub-page__topbar">
      <button class="sub-page__back" type="button" @click="goHome">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M15 18l-6-6 6-6" />
        </svg>
        返回首页
      </button>
    </header>

    <div class="quick-info">
      <!-- 左侧栏目导航 -->
      <nav class="quick-info__nav">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="nav-btn"
          :class="{ 'nav-btn--active': activeTab === tab.key }"
          type="button"
          @click="activeTab = tab.key"
        >
          <!-- 景区简介：信息图标 -->
          <svg v-if="tab.key === 'intro'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 8h.01" />
            <path d="M11 12h1v5h1" />
          </svg>
          <!-- 开放时间：时钟 -->
          <svg v-else-if="tab.key === 'hours'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v5l3 3" />
          </svg>
          <!-- 设施导览：地图钉 -->
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 21s-7-6.1-7-11a7 7 0 0 1 14 0c0 4.9-7 11-7 11z" />
            <circle cx="12" cy="10" r="2.5" />
          </svg>
          <span>{{ tab.label }}</span>
        </button>
      </nav>

      <!-- 右侧内容区 -->
      <section class="quick-info__panel">
        <div v-if="loading" class="quick-info__loading">正在加载…</div>

        <!-- 景区简介 / 开放时间：大字号文本展示 -->
        <template v-else-if="activeTab === 'intro' || activeTab === 'hours'">
          <h1 class="quick-info__title">
            {{ activeTab === 'intro' ? '景区简介' : '开放时间' }}
          </h1>
          <div class="divider" />
          <p class="quick-info__text">
            {{ activeTab === 'intro' ? introText || '暂无简介' : openHoursText || '暂无开放时间信息' }}
          </p>
        </template>

        <!-- 设施导览：平面示意图 + 标记点 -->
        <template v-else>
          <h1 class="quick-info__title">设施导览</h1>
          <div class="divider" />

          <div class="facility-map">
            <!-- 装饰性展厅分区（示意） -->
            <div class="map-room map-room--a">主展厅</div>
            <div class="map-room map-room--b">纪念馆</div>
            <div class="map-room map-room--c">旧居旧址</div>
            <div class="map-room map-room--d">研学教室</div>

            <!-- 设施标记点 -->
            <button
              v-for="p in facilityPoints"
              :key="p.name"
              class="map-marker"
              :class="{ 'map-marker--active': selectedPoint?.name === p.name }"
              :style="{ left: `${p.x}%`, top: `${p.y}%` }"
              type="button"
              @click="selectPoint(p)"
            >
              <span class="map-marker__pin" :style="{ '--marker-color': pointMeta(p.type).color }">
                <!-- 卫生间 -->
                <svg v-if="p.type === 'toilet'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="9" cy="4.5" r="1.8" />
                  <path d="M9 8v5h3l2 6" />
                  <path d="M9 13l-2 6" />
                  <circle cx="16.5" cy="4.5" r="1.8" />
                  <path d="M16.5 8c-1.5 0-2.5 2-2.5 4" />
                </svg>
                <!-- 充电桩 -->
                <svg v-else-if="p.type === 'charging'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M13 2 5 13h5l-1 9 8-11h-5l1-9z" />
                </svg>
                <!-- 停车场 -->
                <svg v-else-if="p.type === 'parking'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="4" y="4" width="16" height="16" rx="3" />
                  <path d="M10 16V8h3a2.5 2.5 0 0 1 0 5h-3" />
                </svg>
                <!-- 医务室 -->
                <svg v-else-if="p.type === 'medical'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="4" y="4" width="16" height="16" rx="3" />
                  <path d="M12 8v8M8 12h8" />
                </svg>
                <!-- 服务台/其他 -->
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="9" />
                  <path d="M12 16v-5" />
                  <path d="M12 8h.01" />
                </svg>
              </span>
              <span class="map-marker__name">{{ p.name }}</span>
            </button>
          </div>

          <!-- 图例 + 选中信息 -->
          <div class="facility-foot">
            <div class="facility-legend">
              <span v-for="item in legendItems" :key="item.type" class="legend-item">
                <i class="legend-dot" :style="{ backgroundColor: item.color }" />
                {{ item.label }}
              </span>
            </div>
            <div class="facility-selected">
              <template v-if="selectedPoint">
                {{ pointMeta(selectedPoint.type).label }} · {{ selectedPoint.name }}
              </template>
              <template v-else>点击地图上的标记查看设施名称</template>
            </div>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.sub-page {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: @color-touch-bg;
  color: @color-touch-text;

  &__topbar {
    flex-shrink: 0;
    padding: 32px 40px 16px;
  }
  &__back {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    padding: 14px 32px;
    border-radius: 999px;
    border: 1px solid @color-touch-border;
    background-color: @color-touch-panel-light;
    color: @color-touch-text;
    font-size: 1.4rem;
    cursor: pointer;
    transition: all 0.2s ease;
    &:hover {
      border-color: @color-primary;
      color: @color-primary-hover;
    }
  }
}

// ===== 主体布局：左导航 + 右内容 =====
.quick-info {
  flex: 1;
  display: flex;
  gap: 32px;
  padding: 8px 40px 40px;
  min-height: 0;

  &__nav {
    flex-shrink: 0;
    width: 300px;
    display: flex;
    flex-direction: column;
    gap: 24px;
  }
  &__panel {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    padding: 40px 48px;
    border-radius: @radius-lg;
    background-color: @color-touch-panel;
    border: 1px solid @color-touch-border;
    overflow-y: auto;
  }
  &__loading {
    margin: auto;
    font-size: 2rem;
    color: @color-touch-text-secondary;
  }
  &__title {
    margin: 0;
    font-size: 3rem;
    font-weight: 700;
    color: @color-touch-text;
  }
  // 大字号正文（老年人可读）
  &__text {
    margin: 32px 0 0;
    font-size: 1.8rem;
    line-height: 2.1;
    color: #d9d9d9;
    white-space: pre-wrap;
  }
}

.divider {
  margin-top: 20px;
  height: 3px;
  width: 120px;
  border-radius: 2px;
  background: linear-gradient(90deg, @color-primary, transparent);
}

// ===== 左侧栏目按钮 =====
.nav-btn {
  display: flex;
  align-items: center;
  gap: 20px;
  min-height: 96px;
  padding: 0 32px;
  border-radius: 16px;
  border: 2px solid @color-touch-border;
  background-color: @color-touch-panel;
  color: @color-touch-text-secondary;
  font-size: 2rem;
  font-weight: 600;
  letter-spacing: 2px;
  cursor: pointer;
  transition: all 0.2s ease;

  svg {
    width: 40px;
    height: 40px;
    flex-shrink: 0;
  }

  &:hover {
    border-color: @color-primary;
    color: @color-touch-text;
  }
  &--active {
    border-color: @color-primary;
    background: linear-gradient(160deg, #d3222f 0%, @color-primary 55%, #9e1530 100%);
    color: #fff;
    box-shadow: 0 0 24px rgba(196, 30, 58, 0.4);
  }
}

// ===== 设施平面示意图 =====
.facility-map {
  position: relative;
  width: 100%;
  margin-top: 32px;
  aspect-ratio: 16 / 8.5;
  border-radius: 16px;
  border: 1px solid @color-touch-border;
  background:
    repeating-linear-gradient(0deg, transparent 0 79px, rgba(255, 255, 255, 0.04) 79px 80px),
    repeating-linear-gradient(90deg, transparent 0 79px, rgba(255, 255, 255, 0.04) 79px 80px),
    linear-gradient(180deg, #191919 0%, #131313 100%);
  overflow: hidden;
}

// 装饰性展厅分区
.map-room {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed rgba(255, 255, 255, 0.14);
  border-radius: 10px;
  font-size: 1.3rem;
  letter-spacing: 4px;
  color: rgba(255, 255, 255, 0.22);
  pointer-events: none;

  &--a {
    left: 4%;
    top: 8%;
    width: 42%;
    height: 44%;
  }
  &--b {
    right: 4%;
    top: 8%;
    width: 30%;
    height: 34%;
  }
  &--c {
    right: 4%;
    bottom: 8%;
    width: 30%;
    height: 26%;
  }
  &--d {
    left: 4%;
    bottom: 8%;
    width: 26%;
    height: 24%;
  }
}

// 设施标记点
.map-marker {
  position: absolute;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  cursor: pointer;
  z-index: 2;

  &__pin {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--marker-color);
    background-color: rgba(0, 0, 0, 0.65);
    border: 2px solid var(--marker-color);
    box-shadow: 0 0 14px color-mix(in srgb, var(--marker-color) 55%, transparent);
    transition: transform 0.15s ease;

    svg {
      width: 30px;
      height: 30px;
    }
  }
  &__name {
    max-width: 140px;
    padding: 2px 10px;
    border-radius: 6px;
    font-size: 1.2rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    color: #eaeaea;
    background-color: rgba(0, 0, 0, 0.55);
  }

  &:hover &__pin {
    transform: scale(1.12);
  }
  &--active &__pin {
    transform: scale(1.18);
    box-shadow: 0 0 22px color-mix(in srgb, var(--marker-color) 85%, transparent);
  }
  &--active &__name {
    color: #fff;
    background-color: rgba(196, 30, 58, 0.85);
  }
}

// ===== 图例 + 选中信息 =====
.facility-foot {
  margin-top: auto;
  padding-top: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;

  .facility-legend {
    display: flex;
    flex-wrap: wrap;
    gap: 12px 36px;
  }
  .legend-item {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    font-size: 1.4rem;
    color: @color-touch-text-secondary;
  }
  .legend-dot {
    width: 14px;
    height: 14px;
    border-radius: 50%;
  }
  .facility-selected {
    flex-shrink: 0;
    min-height: 44px;
    display: inline-flex;
    align-items: center;
    padding: 0 24px;
    border-radius: 999px;
    border: 1px solid @color-touch-border;
    background-color: @color-touch-panel-light;
    font-size: 1.5rem;
    color: @color-touch-text;
  }
}
</style>
