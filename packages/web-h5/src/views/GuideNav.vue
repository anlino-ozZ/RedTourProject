<script setup lang="ts">
/**
 * 导览进行中页（离线地图 + 实时导航 + GPS 讲解）
 * - 从 Guide.vue 选定路线后进入，通过 query.routeId 传入路线
 * - 优先使用浏览器 GPS 实时定位并计算真实距离；定位不可用时降级为演示进度
 * - 支持打卡印章、语音讲解播放控制、步行/驾车模式切换
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RedButton, RedDialog } from '@red-tour-project/common'
import { ElMessage } from 'element-plus'
import {
  playScoreSound,
  playTapSound,
  playFanfareSound,
  playBadgeShineSound,
} from '@/utils/sound'
import { askNarration, resolveAudioUrl } from '@/api/guide'
import { checkinRouteSpot, isCheckinOutOfRange } from '@/api/checkin'
import { getRoutes, type GuideRoute } from '@/api/routes'
import { getSpots, type GuideSpot } from '@/api/spots'
import { speak, stopSpeak } from '@/utils/narrator'
import GuideIcon from '@/components/GuideIcon.vue'
import { addPoints } from '@/utils/levelStorage'
import {
  getSettings,
  saveSettings,
  type NarratorGender,
} from '@/utils/profileStorage'
import { useGeolocation } from '@/composables/useGeolocation'
import { buildMapProjection, haversineMeters, projectGeoPoint } from '@/utils/geo'
import {
  getGuideCheckins,
  markGuideCheckinSynced,
  saveGuideCheckin,
} from '@/utils/guideCheckinStorage'

const route = useRoute()
const router = useRouter()
const routeId = Number(route.query.routeId) || 1
/** 与后端打卡点默认半径保持一致（docs/05 checkpoint.radius 默认 50 米） */
const CHECKIN_RADIUS_METERS = 50

/** 路线列表 */
const routes = ref<GuideRoute[]>([])
/** 景点列表 */
const spots = ref<GuideSpot[]>([])
const guideDataReady = ref(false)
const checkinHydrated = ref(false)
const pendingSpotIds = ref<Set<number>>(new Set())
Promise.all([getRoutes(), getSpots()]).then(([routeResult, spotResult]) => {
  routes.value = routeResult.list
  spots.value = spotResult.list
  guideDataReady.value = true
  hydrateCheckins()
  void syncPendingCheckins()
})

// ===== 真实 GPS 定位 =====
const {
  position: gpsPosition,
  status: geoStatus,
  errorMessage: geoError,
  start: locateMe,
} = useGeolocation({ watch: true, immediate: true })
const geoSpots = computed(() =>
  spots.value.filter((spot) => spot.latitude != null && spot.longitude != null),
)
const mapProjection = computed(() =>
  buildMapProjection(
    geoSpots.value.map((spot) => ({
      latitude: spot.latitude as number,
      longitude: spot.longitude as number,
    })),
  ),
)
/** 真实位置投影到模拟地图；没有 GPS 时返回 null，页面继续使用演示进度 */
const gpsMapPos = computed(() => {
  if (!gpsPosition.value || !mapProjection.value) return null
  return projectGeoPoint(gpsPosition.value, mapProjection.value)
})

const currentRoute = computed<GuideRoute | undefined>(
  () => routes.value.find((r) => r.id === routeId) ?? routes.value[0],
)

/** 该路线上的景点列表（按顺序） */
const routeSpots = computed<GuideSpot[]>(() => {
  if (!currentRoute.value) return []
  return currentRoute.value.spotIds
    .map((id) => spots.value.find((s) => s.id === id))
    .filter((s): s is GuideSpot => !!s)
})

// ===== 导航状态机 =====
/** 当前正在前往的景点索引（0 表示第一个） */
const currentIndex = ref(0)
/** 当前定位进度 0~1，表示从当前景点到下一景点的进度 */
const progress = ref(0)
/** 是否已开始导航 */
const navigating = ref(false)
/** 当前选中景点的打卡状态 */
const stampMap = ref<Record<number, boolean>>({})
/** 正在请求打卡的景点，避免 GPS 连续回调重复提交 */
const checkingSpotId = ref<number | null>(null)
/** GPS 触发的讲解弹窗 */
const lectureVisible = ref(false)

const currentSpot = computed(() => routeSpots.value[currentIndex.value])
const nextSpot = computed(() => routeSpots.value[currentIndex.value + 1] ?? null)
const remainingCount = computed(
  () => routeSpots.value.length - currentIndex.value - 1,
)

// 当前位置在地图上的坐标（起点=上一景点；未开始时=第一个景点）
const currentPos = computed(() => {
  if (gpsMapPos.value) return gpsMapPos.value
  const fromSp =
    currentIndex.value > 0 ? routeSpots.value[currentIndex.value - 1] : routeSpots.value[0]
  const toSp = routeSpots.value[currentIndex.value]
  if (!fromSp || !toSp) return { x: 50, y: 50 }
  const t = progress.value
  return {
    x: fromSp.x + (toSp.x - fromSp.x) * t,
    y: fromSp.y + (toSp.y - fromSp.y) * t,
  }
})

// ===== 模拟导航定时器 =====
let timer: ReturnType<typeof setInterval> | undefined

function startNav() {
  if (navigating.value) return
  navigating.value = true
  // 用户手势内轻触音，同时解锁后续"到达景点"的自动播报音
  playTapSound()
  // 有真实 GPS 时按实际距离判断到达；没有定位时才使用演示进度
  if (gpsPosition.value) checkGpsArrival()
  else startTicker()
}
function pauseNav() {
  navigating.value = false
  stopTicker()
}
function stopTicker() {
  if (timer) clearInterval(timer)
  timer = undefined
}
function startTicker() {
  stopTicker()
  timer = setInterval(() => {
    if (!navigating.value) return
    progress.value += 0.015
    if (progress.value >= 1) {
      progress.value = 1
      // 没有 GPS 时只演示路线进度，不能伪造真实到达记录
      if (currentSpot.value && !stampMap.value[currentSpot.value.id]) {
        ElMessage.info('演示导航已到站，请授权定位后点击打卡')
      }
      stopTicker()
    }
  }, 300)
}
function selectSpot(index: number) {
  if (index < 0 || index >= routeSpots.value.length) return
  currentIndex.value = index
  progress.value = 0
  if (navigating.value && !gpsPosition.value) startTicker()
}
function nextStop() {
  lectureVisible.value = false
  if (currentIndex.value >= routeSpots.value.length - 1) {
    // 整条路线完成：由"全部打卡"监听弹出庆祝弹层
    return
  }
  currentIndex.value += 1
  progress.value = 0
  if (!gpsPosition.value) startTicker()
}

// ===== 打卡完成庆祝 =====
const celebrated = ref(false)
const celebrateVisible = ref(false)
const allStamped = computed(
  () =>
    routeSpots.value.length > 0 &&
    routeSpots.value.every((s) => stampMap.value[s.id]),
)
watch(allStamped, (all) => {
  if (!all || celebrated.value || !checkinHydrated.value) return
  celebrated.value = true
  // 稍作停顿让用户读完到达提示，再收起讲解弹窗、弹出庆祝
  window.setTimeout(() => {
    lectureVisible.value = false
    celebrateVisible.value = true
    playFanfareSound()
    // 号角尾音结束后叠加徽章闪耀音
    window.setTimeout(() => playBadgeShineSound(), 900)
  }, 600)
})

// 撒花：进入页面时生成一次（确定性伪随机，避免每次渲染位置跳动）
// 小方片 / 圆点 / 短条混排，负延迟让粒子一出现就均匀铺满全屏，不会挤成一条横带
const CONFETTI_COLORS = ['#d92b4b', '#e6b422', '#f08b3a', '#f7efe2', '#ffd66b', '#a8202f', '#8f1d2c']
const confetti = Array.from({ length: 72 }, (_, i) => {
  const mode = i % 5
  const width = 5 + ((i * 7) % 4) * 3
  const height = mode === 1 ? width : mode === 2 ? width * 2.1 : width * 0.6
  const dur = 4.6 + (i % 7) * 0.45
  return {
    id: i,
    left: `${(i * 19 + 7) % 98}%`,
    width: `${width.toFixed(1)}px`,
    height: `${height.toFixed(1)}px`,
    radius: mode === 2 ? '1px' : mode === 3 ? '999px' : '2px',
    bg: CONFETTI_COLORS[i % CONFETTI_COLORS.length],
    delay: `-${((i * 1.13) % dur).toFixed(2)}s`,
    dur: `${dur.toFixed(2)}s`,
    drift: `${(((i * 7) % 17) - 8) * 6}px`,
    sway: `${6 + ((i * 11) % 14)}px`,
  }
})
onBeforeUnmount(() => {
  stopTicker()
  stopNarration()
  window.removeEventListener('online', handleOnline)
})

// ===== 模式切换 =====
type TransMode = 'walk' | 'drive'
const mode = ref<TransMode>('walk')

// ===== 语音讲解（后端 TTS 优先，浏览器语音合成兜底） =====
/** 主播音色：男声/女声，选择结果持久化到设置 */
const voiceGender = ref<NarratorGender>(getSettings().voiceGender)
function setVoiceGender(g: NarratorGender) {
  voiceGender.value = g
  saveSettings({ voiceGender: g })
  // 播报中切换音色：用新音色重新播报
  if (playing.value || narrLoading.value) void playNarration()
}

const narrLoading = ref(false)
const playing = ref(false)
let audioEl: HTMLAudioElement | null = null

/** 停止当前讲解（音频 + 语音合成） */
function stopNarration() {
  stopSpeak()
  if (audioEl) {
    audioEl.pause()
    audioEl.currentTime = 0
    audioEl = null
  }
  playing.value = false
  narrLoading.value = false
}

/** 播放当前景点讲解：POST /ask 请求 TTS 音频，audioUrl 为空时浏览器合成兜底 */
async function playNarration() {
  const spot = currentSpot.value
  if (!spot) return
  stopNarration()
  narrLoading.value = true
  try {
    const res = await askNarration({
      question: `请以讲解员的口吻，详细介绍${spot.name}景点的历史背景与故事`,
      useVoice: false,
    })
    narrLoading.value = false
    if (res.audioUrl) {
      // 后端已生成 TTS 音频，直接播放
      const el = new Audio(resolveAudioUrl(res.audioUrl))
      audioEl = el
      el.onended = () => {
        playing.value = false
        audioEl = null
      }
      el.onerror = () => {
        playing.value = false
        audioEl = null
      }
      playing.value = true
      await el.play()
      return
    }
    // audioUrl 为空：浏览器 SpeechSynthesis 播报回答文本
    playing.value = speak(res.answer || spot.desc, voiceGender.value, {
      onEnd: () => (playing.value = false),
    })
  } catch {
    narrLoading.value = false
    // 接口异常（后端未启动等）：本地朗读景点简介兜底
    ElMessage.warning('在线讲解暂不可用，已为您本地播报')
    playing.value = speak(spot.desc, voiceGender.value, {
      onEnd: () => (playing.value = false),
    })
  }
}

function toggleAudio() {
  if (playing.value || narrLoading.value) stopNarration()
  else void playNarration()
}

// 切换景点 / 关闭讲解弹窗 / 离开页面时停止播报
watch(currentIndex, stopNarration)
watch(lectureVisible, (v) => {
  if (!v) stopNarration()
})

// ===== 距离 & 时长（mock 计算） =====
const mockDistance = ref(450) // 米
const mockMinutes = ref(6)

// ===== 真实距离 & GPS 到达 =====
const currentSpotGeo = computed(() => {
  const spot = currentSpot.value
  if (!spot || spot.latitude == null || spot.longitude == null) return null
  return { latitude: spot.latitude, longitude: spot.longitude }
})
/** 与当前目标景点的真实直线距离（米）；无 GPS / 景点无经纬度时为 null */
const realDistanceMeters = computed(() => {
  if (!gpsPosition.value || !currentSpotGeo.value) return null
  return haversineMeters(gpsPosition.value, currentSpotGeo.value)
})
const distanceText = computed(() => {
  if (realDistanceMeters.value == null) {
    return `${Math.max(0, Math.round(mockDistance.value * (1 - progress.value)))} 米`
  }
  return realDistanceMeters.value < 1000
    ? `${Math.round(realDistanceMeters.value)} 米`
    : `${(realDistanceMeters.value / 1000).toFixed(1)} 公里`
})
const minutesText = computed(() => {
  if (realDistanceMeters.value == null) {
    return `${Math.max(1, Math.ceil(mockMinutes.value * (1 - progress.value)))} 分钟`
  }
  // 步行约 75 米/分钟，驾车约 400 米/分钟
  const speed = mode.value === 'walk' ? 75 : 400
  return `${Math.max(1, Math.ceil(realDistanceMeters.value / speed))} 分钟`
})

const currentCheckinPending = computed(() => {
  const spot = currentSpot.value
  return !!spot && pendingSpotIds.value.has(spot.id)
})

const checkinButtonText = computed(() => {
  const spot = currentSpot.value
  if (!spot) return '暂无景点'
  if (stampMap.value[spot.id]) return currentCheckinPending.value ? '待同步' : '已打卡'
  if (checkingSpotId.value === spot.id) return '打卡中…'
  if (geoStatus.value === 'locating') return '定位中…'
  if (!gpsPosition.value) return '定位后打卡'
  if (realDistanceMeters.value == null) return '确认位置中…'
  if (realDistanceMeters.value > CHECKIN_RADIUS_METERS) {
    return `距 ${Math.ceil(realDistanceMeters.value)} 米`
  }
  return '到达打卡'
})

function hydrateCheckins() {
  const state = getGuideCheckins(routeId)
  stampMap.value = Object.fromEntries(
    state.checkedSpotIds.map((spotId) => [spotId, true]),
  )
  pendingSpotIds.value = new Set(state.pending.map((item) => item.spotId))
  const firstUnchecked = routeSpots.value.findIndex((spot) => !stampMap.value[spot.id])
  currentIndex.value =
    firstUnchecked >= 0 ? firstUnchecked : Math.max(0, routeSpots.value.length - 1)
  progress.value = 0
  checkinHydrated.value = true
  // 恢复已完成路线时不再自动弹庆祝层，用户可点“路线完成”主动查看
  if (allStamped.value) celebrated.value = true
}

let syncingPending = false
/** 联网/重新进入页面时，按打卡时保存的经纬度补传离线记录 */
async function syncPendingCheckins() {
  if (
    syncingPending ||
    !guideDataReady.value ||
    !localStorage.getItem('rt_token')
  ) {
    return
  }
  const pending = getGuideCheckins(routeId).pending
  if (!pending.length) return

  syncingPending = true
  let syncedCount = 0
  try {
    for (const record of pending) {
      try {
        const result = await checkinRouteSpot(routeId, {
          spotId: record.spotId,
          longitude: record.longitude,
          latitude: record.latitude,
        })
        if (!result.checked) continue
        markGuideCheckinSynced(routeId, record.spotId)
        pendingSpotIds.value.delete(record.spotId)
        syncedCount += 1
      } catch (error) {
        // 超出范围说明该记录无法补传；其它异常（断网、后端未上线）等下次再试
        if (isCheckinOutOfRange(error)) continue
        break
      }
    }
  } finally {
    syncingPending = false
  }
  if (syncedCount > 0) {
    ElMessage.success(`已同步 ${syncedCount} 条离线打卡记录`)
  }
}

function handleOnline() {
  void syncPendingCheckins()
}
window.addEventListener('online', handleOnline)

/**
 * 提交当前景点打卡：
 * - 有登录态且后端可用：在线打卡
 * - 断网 / 后端未上线：落本地并标记待同步
 * - 后端明确返回 400：说明不在打卡范围，不写成功状态
 */
async function checkinCurrentSpot(source: 'manual' | 'auto' = 'manual') {
  const spot = currentSpot.value
  if (!spot || checkingSpotId.value != null || stampMap.value[spot.id]) return

  const position = gpsPosition.value
  if (!position) {
    if (source === 'manual') {
      ElMessage.warning('请先允许浏览器定位，定位成功后才能打卡')
      locateMe()
    }
    return
  }
  const distance = realDistanceMeters.value
  if (distance == null) {
    if (source === 'manual') ElMessage.warning('正在确认当前位置，请稍后再试')
    return
  }
  if (distance > CHECKIN_RADIUS_METERS) {
    if (source === 'manual') {
      ElMessage.warning(
        `距离景点约 ${Math.ceil(distance)} 米，进入 ${CHECKIN_RADIUS_METERS} 米范围后才能打卡`,
      )
    }
    return
  }

  checkingSpotId.value = spot.id
  const requestBody = {
    spotId: spot.id,
    longitude: position.longitude,
    latitude: position.latitude,
  }
  let synced = false
  let progressText = ''
  const hasSession = !!localStorage.getItem('rt_token')

  try {
    if (hasSession) {
      try {
        const result = await checkinRouteSpot(routeId, requestBody)
        if (!result.checked) {
          if (source === 'manual') ElMessage.warning('打卡未生效，请靠近景点后重试')
          return
        }
        progressText = result.progress
        synced = true
      } catch (error) {
        if (isCheckinOutOfRange(error)) {
          if (source === 'manual') {
            ElMessage.warning('后端校验未通过：当前不在打卡范围内')
          }
          return
        }
        // 网络异常 / 后端接口未上线：继续走离线兜底
      }
    }

    saveGuideCheckin(routeId, spot.id, requestBody, !synced)
    if (synced) {
      pendingSpotIds.value.delete(spot.id)
    } else {
      pendingSpotIds.value.add(spot.id)
    }

    const isNewCheckin = !stampMap.value[spot.id]
    stampMap.value[spot.id] = true
    if (isNewCheckin) addPoints(5)
    progress.value = 1
    lectureVisible.value = true
    playScoreSound()
    stopTicker()

    if (source === 'manual') {
      if (synced) {
        ElMessage.success(progressText ? `打卡成功 · 路线进度 ${progressText}` : '打卡成功')
      } else {
        ElMessage.warning(
          hasSession
            ? '打卡服务暂不可用，已保存到本地，联网后自动同步'
            : '当前未登录，打卡已保存到本地，登录后自动同步',
        )
      }
    }
  } finally {
    checkingSpotId.value = null
  }
}

/** GPS 到达判定：进入目标景点 50 米内自动打卡并弹出讲解 */
function checkGpsArrival() {
  const spot = currentSpot.value
  if (!navigating.value || !spot || realDistanceMeters.value == null) return
  if (stampMap.value[spot.id]) return
  if (realDistanceMeters.value > CHECKIN_RADIUS_METERS) return
  void checkinCurrentSpot('auto')
}

watch([realDistanceMeters, navigating], () => {
  if (navigating.value && gpsPosition.value) checkGpsArrival()
})
// 开始时 GPS 还在定位：拿到坐标后立刻从演示进度切到真实位置
watch(gpsPosition, (position) => {
  if (!position || !navigating.value) return
  stopTicker()
  checkGpsArrival()
})

/** 地图上景点间的连线坐标串（L + M） */
const pathD = computed(() => {
  const coords = routeSpots.value.map((s) => `${s.x},${s.y}`)
  if (coords.length === 0) return ''
  return 'M' + coords.join(' L')
})
</script>

<template>
  <div class="guide-nav">
    <!-- 顶部离线模式标识 -->
    <div class="guide-nav__offline">
      <span><GuideIcon name="map" :size="13" /> 离线地图模式 · 无需网络即可导航</span>
    </div>

    <!-- 模拟地图 -->
    <div class="guide-nav__map">
      <!-- 装饰 -->
      <span class="guide-nav__map-road"></span>
      <span class="guide-nav__map-lake"></span>
      <span class="guide-nav__map-chip">{{ currentRoute?.name }}</span>
      <button
        type="button"
        class="guide-nav__geo-chip"
        :class="{ 'is-ok': geoStatus === 'ok', 'is-fail': geoStatus === 'fail' }"
        :title="geoStatus === 'ok' ? '点击重新定位' : geoError || '点击获取当前位置'"
        @click="locateMe"
      >
        <GuideIcon name="navigation" :size="12" />
        <template v-if="geoStatus === 'ok'">
          GPS 已定位<span v-if="gpsPosition?.accuracy"> ±{{ Math.round(gpsPosition.accuracy) }}m</span>
        </template>
        <template v-else-if="geoStatus === 'locating'">GPS 定位中…</template>
        <template v-else>GPS 未定位 · 重试</template>
      </button>

      <!-- 路线虚线 -->
      <svg class="guide-nav__svg" viewBox="0 0 100 100" preserveAspectRatio="none">
        <path :d="pathD" stroke="#c41e3a" stroke-width="0.8" stroke-dasharray="2 1" fill="none" />
      </svg>

      <!-- 景点 pin（已完成=绿色对勾，进行中=红色脉冲） -->
      <button
        v-for="(sp, i) in routeSpots"
        :key="sp.id"
        type="button"
        class="guide-nav__pin"
        :class="{
          'is-done': !!stampMap[sp.id],
          'is-current': i === currentIndex && navigating,
        }"
        :style="{ left: `${sp.x}%`, top: `${sp.y}%` }"
        @click="selectSpot(i)"
      >
        <span class="guide-nav__pin-label">
          {{ i + 1 }}.{{ sp.name }}
        </span>
        <span v-if="stampMap[sp.id]" class="guide-nav__pin-done">✓</span>
        <span v-else class="guide-nav__pin-dot"></span>
      </button>

      <!-- 我的位置（实时蓝点） -->
      <button
        type="button"
        class="guide-nav__me"
        :class="{
          'is-gps': !!gpsPosition,
          'is-fail': geoStatus === 'fail',
          'is-locating': geoStatus === 'locating',
        }"
        :style="{ left: `${currentPos.x}%`, top: `${currentPos.y}%` }"
        :title="gpsPosition ? '实时位置 · 点击重新定位' : geoError || '点击获取当前位置'"
        @click="locateMe"
      >
        <span class="guide-nav__me-dot"></span>
      </button>
    </div>
    <p v-if="geoStatus === 'fail'" class="guide-nav__geo-tip">
      {{ geoError }}
    </p>

    <!-- 景点顺序卡片：快速查看全路线与打卡状态 -->
    <div class="guide-nav__spot-strip" role="list" aria-label="路线景点顺序">
      <button
        v-for="(sp, i) in routeSpots"
        :key="sp.id"
        type="button"
        class="guide-nav__spot-card"
        :class="{
          'is-current': i === currentIndex,
          'is-done': !!stampMap[sp.id],
          'is-pending': pendingSpotIds.has(sp.id),
        }"
        role="listitem"
        @click="selectSpot(i)"
      >
        <span class="guide-nav__spot-index">{{ i + 1 }}</span>
        <span class="guide-nav__spot-copy">
          <strong>{{ sp.name }}</strong>
          <small v-if="pendingSpotIds.has(sp.id)">待同步</small>
          <small v-else-if="stampMap[sp.id]">已打卡</small>
          <small v-else-if="i === currentIndex">当前景点</small>
          <small v-else>待游览</small>
        </span>
      </button>
    </div>

    <!-- 路线进度条 -->
    <div class="guide-nav__progress">
      <span
        v-for="i in routeSpots.length"
        :key="i"
        class="guide-nav__progress-step"
        :class="{
          'is-done': i - 1 < currentIndex || (i - 1 === currentIndex && !!stampMap[routeSpots[i - 1].id]),
          'is-active': i - 1 === currentIndex,
        }"
      ></span>
    </div>

    <!-- 正在前往卡片 -->
    <div class="guide-nav__card">
      <div class="guide-nav__card-left">
        <div class="guide-nav__status">
          <span class="guide-nav__status-dot"></span>
          <span>{{ navigating ? '正在前往' : '即将前往' }}</span>
        </div>
        <h2 class="guide-nav__spot-name">{{ currentSpot?.name }}</h2>
        <p class="guide-nav__spot-meta">
          距您 {{ distanceText }} · 约 {{ minutesText }}
          <span v-if="realDistanceMeters != null" class="guide-nav__gps-tag">GPS 实时</span>
          <span v-if="nextSpot" class="guide-nav__spot-next">
            · 下一个 {{ nextSpot.name }}
          </span>
        </p>
      </div>
      <div class="guide-nav__card-right">
        <RedButton
          v-if="!navigating"
          type="primary"
          size="large"
          @click="startNav"
        >
          开始导航
        </RedButton>
        <RedButton
          v-else
          type="ghost"
          size="large"
          @click="pauseNav"
        >
          暂停
        </RedButton>
      </div>
    </div>

    <!-- 打卡印章 + 下个景点（横向两栏卡片） -->
    <div class="guide-nav__toolbar">
      <div class="guide-nav__tool">
        <div class="guide-nav__tool-top">
          <GuideIcon
            class="guide-nav__tool-icon"
            :name="stampMap[currentSpot?.id ?? -1] ? 'seal' : 'bookmark'"
            :size="16"
          />
          <span class="guide-nav__tool-title">打卡印章</span>
          <span v-if="currentSpot" class="guide-nav__tool-badge">{{ currentSpot.name.slice(0, 4) }}</span>
        </div>
        <button
          class="guide-nav__tool-sub"
          :class="{
            'is-done': stampMap[currentSpot?.id ?? -1],
            'is-pending': currentCheckinPending,
            'is-ready':
              !stampMap[currentSpot?.id ?? -1] &&
              realDistanceMeters != null &&
              realDistanceMeters <= CHECKIN_RADIUS_METERS,
          }"
          :disabled="!!stampMap[currentSpot?.id ?? -1] || checkingSpotId != null"
          @click="checkinCurrentSpot('manual')"
        >
          {{ checkinButtonText }}
        </button>
      </div>

      <div class="guide-nav__tool">
        <div class="guide-nav__tool-top">
          <GuideIcon class="guide-nav__tool-icon" name="chevron-right" :size="16" />
          <span class="guide-nav__tool-title">下个景点</span>
        </div>
        <button
          v-if="nextSpot"
          class="guide-nav__tool-sub"
          @click="currentIndex++"
        >
          {{ nextSpot.name }} · {{ mode === 'walk' ? '步行' : '驾车' }}
        </button>
        <span v-else class="guide-nav__tool-sub guide-nav__tool-sub--muted">终点</span>
      </div>
    </div>

    <!-- 退出导览：固定在页面左下角，不随滚动 -->
    <button class="guide-nav__back" @click="router.back()">
      <GuideIcon name="chevron-left" :size="16" />
      退出导览
    </button>

    <!-- 路线完成按钮 -->
    <div v-if="remainingCount === 0 && allStamped" class="guide-nav__bottom">
      <button
        class="guide-nav__finish"
        @click="celebrateVisible = true"
      >
        <GuideIcon name="check-circle" :size="16" />
        路线完成
      </button>
    </div>

    <!-- ===== GPS 到达讲解弹窗 ===== -->
    <RedDialog
      :visible="lectureVisible"
      :title="`到达 · ${currentSpot?.name ?? ''}`"
      width="min(420px, 92vw)"
      @update:visible="(v: boolean) => (lectureVisible = v)"
    >
      <template #default>
        <div class="lecture">
          <p class="lecture__desc">{{ currentSpot?.desc }}</p>
          <div v-if="currentSpot?.audioLen" class="lecture__audio">
            <span><GuideIcon name="headphones" :size="13" /> 语音讲解</span>
            <span class="lecture__audio-len">
              约 {{ Math.floor(currentSpot.audioLen / 60) }} 分
              {{ currentSpot.audioLen % 60 }} 秒
            </span>
            <button class="lecture__play" @click="toggleAudio">
              {{ narrLoading ? '生成中...' : playing ? '停止' : '播放' }}
            </button>
          </div>
          <div class="lecture__voices">
            <span class="lecture__voices-label">主播音色</span>
            <button
              class="lecture__voice"
              :class="{ 'is-active': voiceGender === 'male' }"
              @click="setVoiceGender('male')"
            >
              <GuideIcon name="mic" :size="13" /> 播音男主播
            </button>
            <button
              class="lecture__voice"
              :class="{ 'is-active': voiceGender === 'female' }"
              @click="setVoiceGender('female')"
            >
              <GuideIcon name="mic" :size="13" /> 播音女主播
            </button>
          </div>
          <p class="lecture__tip">
            <GuideIcon name="check-circle" :size="13" />
            {{ currentCheckinPending ? '打卡记录已离线保存' : '您已完成打卡' }} · 继续前进将前往
            <strong>{{ nextSpot?.name ?? '终点' }}</strong>
          </p>
        </div>
      </template>
      <template #footer>
        <RedButton type="primary" size="medium" @click="nextStop">
          {{ remainingCount === 0 ? '完成导览' : '前往下一个景点 →' }}
        </RedButton>
      </template>
    </RedDialog>

    <!-- ===== 打卡完成庆祝弹层 ===== -->
    <div v-if="celebrateVisible" class="celebrate">
      <span
        v-for="c in confetti"
        :key="c.id"
        class="celebrate__confetti"
        :style="{
          left: c.left,
          background: c.bg,
          width: c.width,
          height: c.height,
          borderRadius: c.radius,
          '--drift': c.drift,
          '--sway': c.sway,
          animationDelay: c.delay,
          animationDuration: c.dur,
        }"
      ></span>
      <div class="celebrate__card">
        <span class="celebrate__trophy"><GuideIcon name="trophy" :size="56" /></span>
        <h2 class="celebrate__title">恭喜打卡完成！</h2>
        <p class="celebrate__sub">{{ currentRoute?.name }}</p>
        <p class="celebrate__num">共打卡 {{ routeSpots.length }} 个景点 · 全部完成</p>
        <div class="celebrate__actions">
          <RedButton type="primary" size="medium" @click="router.push('/guide')">
            返回导览首页
          </RedButton>
          <RedButton type="ghost" size="medium" @click="celebrateVisible = false">
            继续查看
          </RedButton>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.guide-nav {
  padding-bottom: 180px;

  // 离线标识
  &__offline {
    text-align: center;
    padding: 6px 0;
    background: fade(@color-primary, 6%);
    font-size: @font-size-sm;
    color: @color-primary;
    font-weight: 500;
    border-radius: @radius-base;
    margin-bottom: @spacing-sm;
  }

  // 地图
  &__map {
    position: relative;
    height: 280px;
    border-radius: @radius-lg;
    overflow: hidden;
    background:
      linear-gradient(rgba(255, 255, 255, 0.5) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 255, 255, 0.5) 1px, transparent 1px),
      linear-gradient(135deg, #e4efdc 0%, #d3e4c8 100%);
    background-size: 28px 28px, 28px 28px, 100% 100%;
    box-shadow: @shadow-card;
    margin-bottom: @spacing-md;
  }
  &__svg {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
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
  }
  &__geo-chip {
    position: absolute;
    left: @spacing-sm;
    bottom: @spacing-sm;
    z-index: 6;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 3px 9px;
    border: 1px solid rgba(47, 124, 246, 0.25);
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.92);
    color: #2f7cf6;
    font-size: 11px;
    cursor: pointer;

    &.is-ok {
      border-color: rgba(82, 196, 26, 0.3);
      color: #389e0d;
    }
    &.is-fail {
      border-color: rgba(230, 162, 60, 0.4);
      color: #b26a00;
    }
  }
  &__map-road {
    position: absolute;
    width: 140%;
    height: 6px;
    top: 52%;
    left: -20%;
    background: rgba(255, 255, 255, 0.85);
    transform: rotate(-10deg);
  }
  &__map-lake {
    position: absolute;
    width: 90px;
    height: 56px;
    right: 6%;
    bottom: 6%;
    background: rgba(135, 188, 230, 0.55);
    border-radius: 46% 54% 60% 40% / 50% 44% 56% 50%;
  }

  // 景点 pin
  &__pin {
    position: absolute;
    transform: translate(-50%, -100%);
    padding: 0;
    border: none;
    background: transparent;
    z-index: 4;
    cursor: pointer;
    margin-bottom: -4px;

    &-dot {
      width: 16px;
      height: 16px;
      border-radius: 50% 50% 50% 0;
      transform: rotate(-45deg);
      background: #fff;
      border: 3px solid @color-primary;
      display: block;
    }
    &.is-current .guide-nav__pin-dot {
      background: @color-primary;
      animation: nav-pin-pulse 1.8s ease-out infinite;
    }
    &.is-done .guide-nav__pin-dot {
      background: #52c41a;
      border-color: #389e0d;
    }
    &-done {
      width: 16px;
      height: 16px;
      line-height: 12px;
      text-align: center;
      border-radius: 50%;
      background: #52c41a;
      color: #fff;
      font-size: 10px;
      border: 3px solid #fff;
      display: block;
    }
    &-label {
      display: inline-block;
      margin-top: 4px;
      padding: 2px 8px;
      border-radius: @radius-sm;
      background: rgba(255, 255, 255, 0.92);
      color: @color-text-regular;
      font-size: 11px;
      white-space: nowrap;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
    }
  }
  @keyframes nav-pin-pulse {
    0% { box-shadow: 0 0 0 0 rgba(196, 30, 58, 0.4); }
    70% { box-shadow: 0 0 0 10px rgba(196, 30, 58, 0); }
    100% { box-shadow: 0 0 0 0 rgba(196, 30, 58, 0); }
  }

  // 我的位置
  &__me {
    position: absolute;
    z-index: 5;
    transform: translate(-50%, -50%);
    padding: 0;
    border: none;
    background: transparent;
    cursor: pointer;
    transition: left 0.4s ease, top 0.4s ease;
    &-dot {
      display: block;
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: #2f7cf6;
      border: 3px solid #fff;
      box-shadow: 0 0 0 4px rgba(47, 124, 246, 0.3);
    }
    &.is-gps &-dot {
      animation: nav-location-pulse 1.6s ease-in-out infinite;
    }
    &.is-fail &-dot {
      background: #9aa4b2;
      box-shadow: 0 0 0 4px rgba(154, 164, 178, 0.25);
    }
    &.is-locating &-dot {
      animation: nav-location-pulse 1.1s ease-in-out infinite;
    }
  }
  @keyframes nav-location-pulse {
    0%, 100% { box-shadow: 0 0 0 4px rgba(47, 124, 246, 0.3); }
    50% { box-shadow: 0 0 0 10px rgba(47, 124, 246, 0.08); }
  }
  &__geo-tip {
    margin: -@spacing-sm 0 @spacing-sm;
    padding: @spacing-sm @spacing-md;
    border-radius: @radius-base;
    background: #fff7e6;
    color: #9a5b00;
    font-size: @font-size-sm;
    line-height: 1.5;
    overflow-wrap: anywhere;
    word-break: break-word;
  }

  // 景点顺序卡片
  &__spot-strip {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    padding: 2px 2px 8px;
    margin-bottom: @spacing-sm;
    scrollbar-width: none;
    -webkit-overflow-scrolling: touch;

    &::-webkit-scrollbar {
      display: none;
    }
  }
  &__spot-card {
    flex: 0 0 auto;
    min-width: 132px;
    max-width: 172px;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 9px 11px;
    border: 1px solid @color-border;
    border-radius: @radius-base;
    background: @color-bg-card;
    color: @color-text-regular;
    text-align: left;
    cursor: pointer;
    transition: border-color 0.2s, background 0.2s, transform 0.2s;

    &:active {
      transform: scale(0.98);
    }
    &.is-current {
      border-color: @color-primary;
      background: fade(@color-primary, 7%);
      color: @color-primary;
    }
    &.is-done {
      border-color: fade(#52c41a, 45%);
      background: fade(#52c41a, 7%);
      color: #389e0d;
    }
    &.is-pending {
      border-color: fade(#d48806, 45%);
      background: #fffbe6;
      color: #ad6800;
    }
  }
  &__spot-index {
    flex: 0 0 22px;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: fade(@color-primary, 10%);
    color: @color-primary;
    font-size: 11px;
    font-weight: 700;

    .is-done & {
      background: #52c41a;
      color: #fff;
    }
    .is-pending & {
      background: #faad14;
      color: #fff;
    }
  }
  &__spot-copy {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;

    strong {
      font-size: 12px;
      font-weight: 600;
      line-height: 1.25;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    small {
      font-size: 10px;
      color: @color-text-secondary;

      .is-current & { color: @color-primary; }
      .is-done & { color: #389e0d; }
      .is-pending & { color: #ad6800; }
    }
  }

  // 进度条
  &__progress {
    display: flex;
    justify-content: center;
    gap: 6px;
    margin-bottom: @spacing-md;
    &-step {
      width: 24px;
      height: 4px;
      border-radius: 2px;
      background: @color-border;
      transition: background 0.2s;
      &.is-done { background: @color-primary; }
      &.is-active {
        background: @color-primary;
        width: 32px;
      }
    }
  }

  // 正在前往卡片
  &__card {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: @spacing-md;
    padding: @spacing-md;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: @shadow-card;
    margin-bottom: @spacing-md;
  }
  &__card-left { flex: 1; min-width: 0; }
  &__status {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 2px 8px;
    border-radius: 10px;
    background: fade(@color-primary, 10%);
    color: @color-primary;
    font-size: @font-size-sm;
    font-weight: 500;
    margin-bottom: 4px;
  }
  &__status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: @color-primary;
    animation: nav-dot-blink 1.2s ease-in-out infinite;
  }
  @keyframes nav-dot-blink {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.3; }
  }
  &__spot-name {
    margin: 0 0 4px;
    font-size: 18px;
    font-weight: 700;
    color: @color-text-primary;
  }
  &__spot-meta {
    margin: 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    line-height: 1.5;
  }
  &__spot-next {
    color: @color-text-regular;
  }
  &__gps-tag {
    display: inline-block;
    margin-left: 4px;
    padding: 0 5px;
    border-radius: 999px;
    background: fade(#2f7cf6, 10%);
    color: #2f7cf6;
    font-size: 10px;
    line-height: 16px;
    vertical-align: 1px;
  }

  // 操作区：横向两栏卡片（打卡印章 / 下个景点）
  &__toolbar {
    display: flex;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: @shadow-card;
    margin-bottom: @spacing-md;
    overflow: hidden;
    border: 1px solid @color-border;
  }
  &__tool {
    flex: 1;
    padding: @spacing-sm @spacing-md;
    border-right: 1px solid @color-border;
    display: flex;
    flex-direction: column;
    gap: 6px;

    &:last-child { border-right: none; }
  }
  &__tool-top {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  &__tool-icon {
    display: inline-flex;
    color: @color-primary;
  }
  &__tool-title {
    font-size: 13px;
    font-weight: 600;
    color: @color-text-primary;
  }
  &__tool-badge {
    margin-left: auto;
    padding: 1px 6px;
    border-radius: 8px;
    background: fade(@color-primary, 10%);
    color: @color-primary;
    font-size: 10px;
    font-weight: 500;
  }
  &__tool-sub {
    padding: 0;
    border: none;
    background: transparent;
    color: @color-text-secondary;
    font-size: 12px;
    text-align: left;
    cursor: pointer;
    font-weight: 500;
    display: inline-flex;
    align-items: center;
    gap: 3px;

    &.is-done { color: #52c41a; }
    &.is-ready {
      color: @color-primary;
      font-weight: 700;
    }
    &.is-pending {
      color: #ad6800;
    }
    &.is-playing { color: @color-primary; }
    &:disabled {
      cursor: default;
      opacity: 0.88;
    }
    &--muted { color: @color-text-secondary; cursor: default; }
  }

  &__bottom {
    position: fixed;
    left: 0;
    right: 0;
    bottom: calc(env(safe-area-inset-bottom) + @spacing-md);
    padding: 0 @spacing-md;
    display: flex;
    justify-content: flex-end;
    align-items: center;
    z-index: 90;
  }
  &__back {
    position: fixed;
    left: @spacing-md;
    bottom: calc(env(safe-area-inset-bottom) + @spacing-md);
    z-index: 91;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    border: 1px solid fade(@color-primary, 35%);
    background: @color-primary-light;
    color: @color-primary;
    font-size: @font-size-base;
    font-weight: 600;
    padding: 9px 18px;
    border-radius: 20px;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(196, 30, 58, 0.12);

    svg {
      width: 16px;
      height: 16px;
    }
    &:active {
      background: fade(@color-primary, 12%);
    }
  }
  &__finish {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    border: none;
    background: #52c41a;
    color: #fff;
    padding: 8px 20px;
    border-radius: @radius-base;
    font-size: @font-size-base;
    font-weight: 600;
    cursor: pointer;
  }
}

// ===== 讲解弹窗 =====
.lecture {
  &__desc {
    margin: 0 0 @spacing-md;
    font-size: @font-size-base;
    color: @color-text-regular;
    line-height: 1.7;
  }
  &__audio {
    display: flex;
    align-items: center;
    gap: @spacing-md;
    padding: @spacing-md;
    background: @color-primary-light;
    border-radius: @radius-base;
    margin-bottom: @spacing-md;
    font-size: @font-size-sm;
  }
  &__audio-len {
    color: @color-text-secondary;
  }
  &__play {
    margin-left: auto;
    border: none;
    background: @color-primary;
    color: #fff;
    padding: 6px 14px;
    border-radius: @radius-base;
    font-size: @font-size-sm;
    cursor: pointer;
  }
  &__tip {
    margin: 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }
  &__voices {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    margin-bottom: @spacing-md;
  }
  &__voices-label {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }
  &__voice {
    padding: 5px 12px;
    border: 1.5px solid @color-border;
    border-radius: 16px;
    background: @color-bg-card;
    color: @color-text-regular;
    font-size: @font-size-sm;
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
}

// ===== 打卡完成庆祝弹层 =====
.celebrate {
  position: fixed;
  inset: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: @spacing-lg;
  background: rgba(20, 8, 10, 0.72);
  backdrop-filter: blur(4px);

  &__confetti {
    position: absolute;
    top: 0;
    // 每片独立的横向落点与摆动幅度，由内联样式注入
    --drift: 0px;
    --sway: 10px;
    opacity: 0;
    animation: celebrate-fall 5.4s ease-in infinite;
    pointer-events: none;
    will-change: transform, opacity;
  }
  @keyframes celebrate-fall {
    0% {
      transform: translate3d(0, -14vh, 0) rotate(0deg);
      opacity: 0;
    }
    10% {
      opacity: 1;
    }
    35% {
      transform: translate3d(var(--sway), 24vh, 0) rotate(190deg);
    }
    65% {
      transform: translate3d(calc(var(--sway) * -0.8), 60vh, 0) rotate(400deg);
    }
    100% {
      transform: translate3d(var(--drift), 116vh, 0) rotate(620deg);
      opacity: 0.5;
    }
  }


  &__card {
    position: relative;
    z-index: 1;
    width: min(340px, 88vw);
    padding: @spacing-xl @spacing-lg;
    text-align: center;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
    animation: celebrate-pop 0.55s cubic-bezier(0.34, 1.56, 0.64, 1) both;
  }
  @keyframes celebrate-pop {
    0% {
      transform: scale(0.4);
      opacity: 0;
    }
    100% {
      transform: scale(1);
      opacity: 1;
    }
  }
  &__trophy {
    display: inline-flex;
    color: #d4af37;
    animation: celebrate-bounce 1.2s ease-in-out infinite;
  }
  @keyframes celebrate-bounce {
    0%,
    100% {
      transform: translateY(0);
    }
    50% {
      transform: translateY(-10px);
    }
  }
  &__title {
    margin: @spacing-sm 0 @spacing-xs;
    font-size: 22px;
    font-weight: 700;
    color: @color-text-primary;
  }
  &__sub {
    margin: 0 0 4px;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-primary;
  }
  &__num {
    margin: 0 0 @spacing-lg;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }
  &__actions {
    display: flex;
    justify-content: center;
    gap: @spacing-sm;
  }
}
</style>
