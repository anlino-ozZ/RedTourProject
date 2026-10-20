<script setup lang="ts">
// PC管理后台 数据统计页（接口文档 14.9，PRD 5.7 用户分析）
// 4 张图表：访问趋势折线图 / 热门景点柱状图 / 热门问题词云 / 特产点击环形图
// 数据按时间范围（今日/本周/本月）与景区筛选；超管可看全部景区，普管仅管辖景区
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import 'echarts-wordcloud'
import { RedButton } from '@red-tour-project/common'
import {
  getVisitTrend,
  getHotSpots,
  getHotQuestions,
  getProductClicks,
  type VisitTrendData,
  type HotSpotItem,
  type HotQuestionItem,
  type ProductClickItem,
} from '@/api/admin'
import { getScenicAreas } from '@/api'
import { useUserStore } from '@/store'
import type { ScenicAreaInfo } from '@red-tour-project/common'

const userStore = useUserStore()
const isSuperAdmin = userStore.isSuperAdmin
const boundScenicAreaId = isSuperAdmin ? undefined : userStore.userInfo?.scenicAreaId

// ===== 筛选条件 =====
type TimeRange = 'today' | 'week' | 'month'
const timeRange = ref<TimeRange>('week')
const query = reactive({
  scenicAreaId: (boundScenicAreaId ?? '') as number | '',
})

const scenicOptions = ref<ScenicAreaInfo[]>([])

async function loadScenicOptions() {
  try {
    scenicOptions.value = await getScenicAreas()
  } catch {
    // 错误提示已由拦截器统一弹出
  }
}

// ===== 时间范围 → 起止日期 =====
function fmtDate(d: Date) {
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function rangeOf(range: TimeRange): { startDate: string; endDate: string } {
  const now = new Date()
  const endDate = fmtDate(now)
  if (range === 'today') {
    return { startDate: endDate, endDate }
  }
  if (range === 'week') {
    // 周一为一周开始
    const day = now.getDay() || 7
    const monday = new Date(now)
    monday.setDate(now.getDate() - day + 1)
    return { startDate: fmtDate(monday), endDate }
  }
  const first = new Date(now.getFullYear(), now.getMonth(), 1)
  return { startDate: fmtDate(first), endDate }
}

// ===== 数据 =====
const loading = ref(false)

// ===== 图表 =====
const trendEl = ref<HTMLDivElement>()
const spotsEl = ref<HTMLDivElement>()
const questionsEl = ref<HTMLDivElement>()
const productsEl = ref<HTMLDivElement>()

let trendChart: echarts.ECharts | null = null
let spotsChart: echarts.ECharts | null = null
let questionsChart: echarts.ECharts | null = null
let productsChart: echarts.ECharts | null = null

/** 空数据兜底：居中"暂无数据" */
const emptyOption: echarts.EChartsCoreOption = {
  title: {
    text: '暂无数据',
    left: 'center',
    top: 'center',
    textStyle: { color: '#999', fontSize: 14, fontWeight: 'normal' },
  },
}

function renderTrend(data: VisitTrendData) {
  if (!trendEl.value) return
  trendChart = trendChart ?? echarts.init(trendEl.value)
  const isEmpty = !data.dates || data.dates.length === 0
  const option: echarts.EChartsCoreOption = {
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 36, bottom: 32 },
    xAxis: { type: 'category', data: data.dates, boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '访问量',
        type: 'line',
        smooth: true,
        data: data.series,
        itemStyle: { color: '#409eff' },
        areaStyle: { color: 'rgba(64, 158, 255, 0.15)' },
      },
    ],
  }
  trendChart.setOption(isEmpty ? emptyOption : option, true)
}

function renderSpots(data: HotSpotItem[]) {
  if (!spotsEl.value) return
  spotsChart = spotsChart ?? echarts.init(spotsEl.value)
  const isEmpty = !data || data.length === 0
  // 横向柱状图：倒序使访问量最高的在最上方
  const sorted = [...(data || [])].sort((a, b) => a.visitCount - b.visitCount)
  const option: echarts.EChartsCoreOption = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 32, top: 12, bottom: 12, containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: sorted.map((s) => s.spotName) },
    series: [
      {
        name: '访问量',
        type: 'bar',
        data: sorted.map((s) => s.visitCount),
        barMaxWidth: 18,
        itemStyle: { color: '#67c23a', borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right' },
      },
    ],
  }
  spotsChart.setOption(isEmpty ? emptyOption : option, true)
}

function renderQuestions(data: HotQuestionItem[]) {
  if (!questionsEl.value) return
  questionsChart = questionsChart ?? echarts.init(questionsEl.value)
  const isEmpty = !data || data.length === 0
  const option: echarts.EChartsCoreOption = {
    tooltip: { show: true, formatter: (p: { name: string; value: number }) => `${p.name}：提问 ${p.value} 次` },
    series: [
      {
        type: 'wordCloud',
        shape: 'circle',
        gridSize: 8,
        sizeRange: [14, 40],
        rotationRange: [0, 0],
        width: '96%',
        height: '96%',
        data: (data || []).map((q) => ({ name: q.question, value: q.askCount })),
        textStyle: { fontWeight: 'bold', fontFamily: 'sans-serif' },
        emphasis: { textStyle: { shadowBlur: 6, shadowColor: 'rgba(0, 0, 0, 0.2)' } },
      },
    ],
  }
  questionsChart.setOption(isEmpty ? emptyOption : option, true)
}

function renderProducts(data: ProductClickItem[]) {
  if (!productsEl.value) return
  productsChart = productsChart ?? echarts.init(productsEl.value)
  const isEmpty = !data || data.length === 0
  const option: echarts.EChartsCoreOption = {
    tooltip: { trigger: 'item', formatter: '{b}：{c} 次（{d}%）' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        name: '点击量',
        type: 'pie',
        radius: ['38%', '66%'],
        center: ['50%', '46%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 4, borderWidth: 2, borderColor: '#fff' },
        label: { show: false },
        data: (data || []).map((p) => ({ name: p.productName, value: p.clickCount })),
      },
    ],
  }
  productsChart.setOption(isEmpty ? emptyOption : option, true)
}

// ===== 加载 =====
async function loadAll() {
  loading.value = true
  const { startDate, endDate } = rangeOf(timeRange.value)
  const params = {
    scenicAreaId: (boundScenicAreaId ?? query.scenicAreaId) || undefined,
    startDate,
    endDate,
  }
  try {
    const [trend, spots, questions, products] = await Promise.all([
      getVisitTrend(params),
      getHotSpots(params),
      getHotQuestions(params),
      getProductClicks(params),
    ])
    renderTrend(trend)
    renderSpots(spots)
    renderQuestions(questions)
    renderProducts(products)
  } catch {
    // 错误提示已由拦截器统一弹出
  } finally {
    loading.value = false
  }
}

watch([timeRange, () => query.scenicAreaId], () => {
  loadAll()
})

// ===== 窗口缩放自适应 =====
function handleResize() {
  trendChart?.resize()
  spotsChart?.resize()
  questionsChart?.resize()
  productsChart?.resize()
}

onMounted(() => {
  loadScenicOptions()
  loadAll()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  spotsChart?.dispose()
  questionsChart?.dispose()
  productsChart?.dispose()
  trendChart = null
  spotsChart = null
  questionsChart = null
  productsChart = null
})
</script>

<template>
  <div class="stats-page">
    <!-- 筛选栏 -->
    <div class="stats-page__toolbar">
      <el-radio-group v-model="timeRange">
        <el-radio-button value="today">今日</el-radio-button>
        <el-radio-button value="week">本周</el-radio-button>
        <el-radio-button value="month">本月</el-radio-button>
      </el-radio-group>
      <el-select
        v-if="isSuperAdmin"
        v-model="query.scenicAreaId"
        placeholder="全部景区"
        clearable
        filterable
        style="width: 180px"
      >
        <el-option v-for="s in scenicOptions" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <RedButton type="primary" size="small" @click="loadAll">刷新</RedButton>
    </div>

    <!-- 图表区 -->
    <el-row :gutter="16" v-loading="loading">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="stats-page__card">
          <template #header>游客访问趋势</template>
          <div ref="trendEl" class="stats-page__chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="stats-page__card">
          <template #header>热门景点排行</template>
          <div ref="spotsEl" class="stats-page__chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="stats-page__card">
          <template #header>热门问题词云</template>
          <div ref="questionsEl" class="stats-page__chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="stats-page__card">
          <template #header>特产点击统计</template>
          <div ref="productsEl" class="stats-page__chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.stats-page {
  &__toolbar {
    display: flex;
    align-items: center;
    gap: @spacing-md;
    margin-bottom: @spacing-md;
  }

  &__card {
    margin-bottom: @spacing-md;
  }

  &__chart {
    width: 100%;
    height: 320px;
  }
}
</style>
