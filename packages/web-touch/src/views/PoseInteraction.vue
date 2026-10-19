<script setup lang="ts">
// 姿态互动页（W-T-05 骨架 MVP + W-T-06 WebSocket 实时对接）
// 摄像头抽帧 → /api/v1/pose/stream → action/confidence/keypoints/triggerContent
// 后端不可达 / 无摄像头时自动退回模拟数据演示
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import QRCode from 'qrcode'
import type { PoseTriggerContent } from '@red-tour-project/common'
import { usePoseStream, type PoseResult } from '@/composables/usePoseStream'
import { useClaimToken } from '@/composables/useClaimToken'

const router = useRouter()
function goHome() {
  router.push('/')
}

// 动作标识 → 中文展示
const ACTION_LABELS: Record<string, string> = {
  salute: '敬礼',
  mill: '推磨',
  wave: '挥手',
  unknown: '未识别',
}

// ===== 摄像头预览 =====
const videoRef = ref<HTMLVideoElement | null>(null)
const cameraReady = ref(false)
const cameraError = ref('摄像头未连接 · 播放骨架演示动画')
let mediaStream: MediaStream | null = null

async function initCamera() {
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { width: 1280, height: 720 },
      audio: false,
    })
    if (videoRef.value) {
      videoRef.value.srcObject = mediaStream
      await videoRef.value.play()
      cameraReady.value = true
      // 有真实画面才启动 WS 推帧
      poseStream.connect()
    }
  } catch {
    cameraReady.value = false
  }
}

function stopCamera() {
  mediaStream?.getTracks().forEach((t) => t.stop())
  mediaStream = null
  cameraReady.value = false
}

// ===== COCO-17 关键点骨架 =====
// 骨骼连线（COCO 拓扑）
const BONES: [number, number][] = [
  [0, 1], [0, 2], [1, 3], [2, 4], // 头部
  [5, 6], [5, 7], [7, 9], [6, 8], [8, 10], // 手臂
  [5, 11], [6, 12], [11, 12], // 躯干
  [11, 13], [13, 15], [12, 14], [14, 16], // 腿部
]

// 站立姿态（基准帧）
const POSE_STAND: Kp[] = [
  [0.5, 0.12], [0.485, 0.1], [0.515, 0.1], [0.47, 0.115], [0.53, 0.115],
  [0.42, 0.27], [0.58, 0.27],
  [0.385, 0.41], [0.615, 0.41],
  [0.365, 0.55], [0.635, 0.55],
  [0.45, 0.56], [0.55, 0.56],
  [0.445, 0.73], [0.555, 0.73],
  [0.44, 0.9], [0.56, 0.9],
]

// 以站立姿态为底覆盖部分关键点，生成动作帧
function pose(overrides: Record<number, Kp>): Kp[] {
  return POSE_STAND.map((p, i) => overrides[i] ?? p)
}

const WAVE_A = pose({ 8: [0.66, 0.3], 10: [0.7, 0.16] })
const WAVE_B = pose({ 8: [0.68, 0.31], 10: [0.74, 0.13] })
const SALUTE = pose({ 8: [0.63, 0.3], 10: [0.545, 0.115] })
const MILL_A = pose({ 7: [0.4, 0.42], 9: [0.32, 0.5], 8: [0.6, 0.5], 10: [0.68, 0.58] })
const MILL_B = pose({ 7: [0.42, 0.48], 9: [0.34, 0.58], 8: [0.58, 0.42], 10: [0.66, 0.5] })

interface PoseSegment {
  from: Kp[]
  to: Kp[]
  action: 'salute' | 'mill' | 'wave' | 'unknown'
  label: string
  confidence: number
  duration: number // 秒
  trigger?: PoseTriggerContent // 达标段携带的模拟讲解内容
}
const seg = (
  from: Kp[],
  to: Kp[],
  action: PoseSegment['action'],
  label: string,
  confidence: number,
  duration: number,
  trigger?: PoseTriggerContent,
): PoseSegment => ({ from, to, action, label, confidence, duration, trigger })

// 演示时间线：站立 → 挥手 → 敬礼 → 推磨 → 循环
const SEGMENTS: PoseSegment[] = [
  seg(POSE_STAND, POSE_STAND, 'unknown', '未识别', 0.32, 1.4),
  seg(POSE_STAND, WAVE_A, 'wave', '挥手', 0.62, 0.7),
  seg(WAVE_A, WAVE_B, 'wave', '挥手', 0.88, 0.55, {
    title: '挥手致意 · 红军标语前的问候',
    audioUrl: '/audio/pose/wave.mp3',
  }),
  seg(WAVE_B, WAVE_A, 'wave', '挥手', 0.9, 0.55, {
    title: '挥手致意 · 红军标语前的问候',
    audioUrl: '/audio/pose/wave.mp3',
  }),
  seg(WAVE_A, WAVE_B, 'wave', '挥手', 0.87, 0.55, {
    title: '挥手致意 · 红军标语前的问候',
    audioUrl: '/audio/pose/wave.mp3',
  }),
  seg(WAVE_B, POSE_STAND, 'unknown', '未识别', 0.4, 0.7),
  seg(POSE_STAND, SALUTE, 'salute', '敬礼', 0.6, 0.7),
  seg(SALUTE, SALUTE, 'salute', '敬礼', 0.91, 2.2, {
    title: '军礼的由来',
    audioUrl: '/audio/pose/salute.mp3',
    wikiRef: 'wiki/军礼',
  }),
  seg(SALUTE, POSE_STAND, 'unknown', '未识别', 0.42, 0.7),
  seg(POSE_STAND, MILL_A, 'mill', '推磨', 0.63, 0.7),
  seg(MILL_A, MILL_B, 'mill', '推磨', 0.9, 0.8, {
    title: '红嫂推磨 · 沂蒙精神',
    audioUrl: '/audio/pose/mill.mp3',
  }),
  seg(MILL_B, MILL_A, 'mill', '推磨', 0.88, 0.8, {
    title: '红嫂推磨 · 沂蒙精神',
    audioUrl: '/audio/pose/mill.mp3',
  }),
  seg(MILL_A, MILL_B, 'mill', '推磨', 0.9, 0.8, {
    title: '红嫂推磨 · 沂蒙精神',
    audioUrl: '/audio/pose/mill.mp3',
  }),
  seg(MILL_B, POSE_STAND, 'unknown', '未识别', 0.38, 0.7),
]
const TOTAL_DURATION = SEGMENTS.reduce((s, x) => s + x.duration, 0)

// ===== 识别状态（模拟与实时共用单一状态源） =====
type Kp = [number, number]
const currentLabel = ref('未识别')
const currentAction = ref('unknown')
const currentConfidence = ref(0.32)
const personVisible = ref(true) // 实时模式下未检测到人体时为 false

// 实时识别结果（仅在 status === 'live' 时驱动渲染）
const realtimeKpts = ref<Kp[]>([])

// ===== 全屏触发特效（confidence ≥ 0.85，W-T-06/W-T-07）=====
const TRIGGER_THRESHOLD = 0.85
const TRIGGER_REARM = 0.75 // 置信度回落至此值以下才允许再次触发
const EFFECT_COOLDOWN = 750 // 全屏页关闭后冷却，防止同一动作重复触发
const effectVisible = ref(false)
const effectAction = ref('')
const effectActionKey = ref('')
const effectTrigger = ref<PoseTriggerContent | null>(null)
let triggerArmed = true
let rearmTimer: ReturnType<typeof setTimeout> | null = null

// ===== 成就二维码（5 分钟有效临时 token，W-T-07）=====
const claim = useClaimToken()
const qrDataUrl = ref('')

async function renderQr() {
  if (!claim.claimUrl.value) {
    qrDataUrl.value = ''
    return
  }
  try {
    qrDataUrl.value = await QRCode.toDataURL(claim.claimUrl.value, {
      width: 320,
      margin: 1,
      color: { dark: '#1a1210', light: '#ffffff' },
    })
  } catch {
    qrDataUrl.value = ''
  }
}

function refreshQr() {
  claim.issue(effectActionKey.value)
  void renderQr()
}

// ===== 讲解音频（相对路径基于业务后端根地址解析）=====
const audioRef = ref<HTMLAudioElement | null>(null)
const audioPlaying = ref(false)
const audioError = ref(false)

function resolveMediaUrl(url?: string): string {
  if (!url) return ''
  if (/^https?:\/\//.test(url)) return url
  const base = import.meta.env.VITE_API_BASE_URL || ''
  // /api/v1 是接口前缀，静态媒体位于服务根路径
  const root = base.replace(/\/api\/v1\/?$/, '')
  return `${root}${url.startsWith('/') ? '' : '/'}${url}`
}

const audioSrc = computed(() => resolveMediaUrl(effectTrigger.value?.audioUrl))

function resetAudio() {
  audioPlaying.value = false
  audioError.value = false
  const el = audioRef.value
  if (el) {
    el.pause()
    el.currentTime = 0
    // src 变化后尝试自动播放（浏览器策略允许时）
    void el.play().then(
      () => (audioPlaying.value = true),
      () => (audioPlaying.value = false),
    )
  }
}

function toggleAudio() {
  const el = audioRef.value
  if (!el || audioError.value) return
  if (el.paused) {
    void el.play().then(() => (audioPlaying.value = true), () => {})
  } else {
    el.pause()
    audioPlaying.value = false
  }
}

function showEffect(action: string, trigger?: PoseTriggerContent | null) {
  effectActionKey.value = action
  effectAction.value = ACTION_LABELS[action] ?? action
  effectTrigger.value = trigger ?? null
  effectVisible.value = true
  // 签发 5 分钟领取 token 并渲染二维码
  claim.issue(action)
  void renderQr()
  // 音频元素在 v-if 层内挂载完成后自动播放
  requestAnimationFrame(() => resetAudio())
}

function hideEffect() {
  effectVisible.value = false
  audioRef.value?.pause()
  claim.clear()
  qrDataUrl.value = ''
  if (rearmTimer) clearTimeout(rearmTimer)
  rearmTimer = setTimeout(() => {
    triggerArmed = true
  }, EFFECT_COOLDOWN)
}

// 统一的识别结果入口：模拟时间线与 WS 结果都走这里（单一事实源）
function applyResult(action: string, confidence: number, trigger?: PoseTriggerContent | null) {
  currentAction.value = action
  currentConfidence.value = confidence
  currentLabel.value = ACTION_LABELS[action] ?? action

  if (confidence < TRIGGER_REARM) triggerArmed = true
  // 全屏讲解页展示期间不重复触发；关闭并冷却后才允许下一次
  if (!effectVisible.value && triggerArmed && confidence >= TRIGGER_THRESHOLD && action !== 'unknown') {
    triggerArmed = false
    showEffect(action, trigger)
  }
}

// ===== 实时姿态 WebSocket（W-T-06） =====
const poseStream = usePoseStream({
  getVideo: () => videoRef.value,
  fps: 8,
  onResult: (r: PoseResult) => {
    // 后端坐标基于非镜像原始帧，前端视频做了镜像 → x 翻转后与画面重合
    realtimeKpts.value = (r.keypoints ?? []).map(
      (k) => [1 - Number(k[0]), Number(k[1])] as Kp,
    )
    personVisible.value = (r.keypoints?.length ?? 0) >= 17
    applyResult(r.action, r.confidence, r.triggerContent)
  },
})

// ===== Canvas 绘制 =====
const stageRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
let rafId = 0
let startTime = 0
let resizeObserver: ResizeObserver | null = null

function easeInOut(t: number) {
  return t * t * (3 - 2 * t)
}

function lerpKp(a: Kp, b: Kp, t: number): Kp {
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]
}

function resizeCanvas() {
  const canvas = canvasRef.value
  const stage = stageRef.value
  if (!canvas || !stage) return
  const dpr = window.devicePixelRatio || 1
  canvas.width = stage.clientWidth * dpr
  canvas.height = stage.clientHeight * dpr
}

function drawFrame(kpts: Kp[]) {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const dpr = window.devicePixelRatio || 1
  const w = canvas.width / dpr
  const h = canvas.height / dpr
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, w, h)
  const px = (k: Kp) => [k[0] * w, k[1] * h] as const

  // 骨骼连线（金色 + 辉光）
  ctx.lineWidth = 5
  ctx.lineCap = 'round'
  ctx.strokeStyle = '#f5c542'
  ctx.shadowColor = 'rgba(245, 197, 66, 0.6)'
  ctx.shadowBlur = 10
  for (const [i, j] of BONES) {
    if (!kpts[i] || !kpts[j]) continue
    const [x1, y1] = px(kpts[i])
    const [x2, y2] = px(kpts[j])
    ctx.beginPath()
    ctx.moveTo(x1, y1)
    ctx.lineTo(x2, y2)
    ctx.stroke()
  }

  // 关键点（红色圆点 + 白芯）
  for (let i = 0; i < kpts.length; i++) {
    if (!kpts[i]) continue
    const [x, y] = px(kpts[i])
    const r = i === 0 ? 8 : 6.5
    ctx.beginPath()
    ctx.fillStyle = '#e3283c'
    ctx.arc(x, y, r, 0, Math.PI * 2)
    ctx.fill()
    ctx.shadowBlur = 0
    ctx.beginPath()
    ctx.fillStyle = '#ffe9ec'
    ctx.arc(x, y, 2.4, 0, Math.PI * 2)
    ctx.fill()
    ctx.shadowBlur = 10
  }
  ctx.shadowBlur = 0
}

// 模拟模式：时间线推进
function stepMock(now: number) {
  let t = ((now - startTime) / 1000) % TOTAL_DURATION
  let active = SEGMENTS[SEGMENTS.length - 1]
  for (const s of SEGMENTS) {
    if (t < s.duration) {
      active = s
      break
    }
    t -= s.duration
  }
  const p = easeInOut(Math.min(1, t / active.duration))
  const kpts = active.from.map((k, i) => lerpKp(k, active.to[i], p))
  // 模拟置信度小幅抖动，更接近真实识别
  const confidence = Math.min(0.99, active.confidence + Math.sin(now / 400) * 0.015)

  drawFrame(kpts)
  applyResult(active.action, confidence, active.trigger)
}

// 唯一渲染循环：实时连接时画真实关键点，否则跑模拟时间线
function loop(now: number) {
  if (!startTime) startTime = now

  if (poseStream.status.value === 'live') {
    startTime = 0 // 重置模拟时间线，断回模拟时从头播放
    if (realtimeKpts.value.length) drawFrame(realtimeKpts.value)
    else {
      const canvas = canvasRef.value
      if (canvas) canvas.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height)
    }
  } else {
    stepMock(now)
  }

  rafId = requestAnimationFrame(loop)
}

onMounted(() => {
  initCamera()
  resizeCanvas()
  // 监听舞台尺寸变化（含首帧布局完成），保证 canvas 始终与舞台同尺寸
  resizeObserver = new ResizeObserver(resizeCanvas)
  if (stageRef.value) resizeObserver.observe(stageRef.value)
  window.addEventListener('resize', resizeCanvas)
  rafId = requestAnimationFrame(loop)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  window.removeEventListener('resize', resizeCanvas)
  resizeObserver?.disconnect()
  resizeObserver = null
  if (rearmTimer) clearTimeout(rearmTimer)
  audioRef.value?.pause()
  stopCamera()
})
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

    <div class="pose-grid">
      <!-- 左：摄像头预览 + 骨架叠加 -->
      <div ref="stageRef" class="pose-stage">
        <video
          ref="videoRef"
          class="pose-stage__video"
          :class="{ 'pose-stage__video--on': cameraReady }"
          autoplay
          muted
          playsinline
        />
        <canvas ref="canvasRef" class="pose-stage__canvas" />
        <div v-if="!cameraReady" class="pose-stage__placeholder">
          <svg viewBox="0 0 24 24" width="72" height="72" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 8a2 2 0 0 1 2-2h2l2-2h6l2 2h2a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
            <circle cx="12" cy="13" r="3.5" />
          </svg>
          <span>{{ cameraError }}</span>
        </div>
        <!-- 实时模式下未检测到人体 -->
        <div
          v-if="poseStream.status.value === 'live' && !personVisible"
          class="pose-stage__no-person"
        >
          未检测到人体，请站到识别区域内
        </div>
        <!-- 连接状态徽标 -->
        <span class="pose-stage__badge" :class="`pose-stage__badge--${poseStream.status.value}`">
          {{
            poseStream.status.value === 'live' ? '● 实时识别'
            : poseStream.status.value === 'connecting' ? '○ 正在连接识别服务'
            : poseStream.status.value === 'reconnecting' ? '○ 连接断开 · 重连中'
            : '模拟数据模式'
          }}
        </span>
      </div>

      <!-- 右：识别状态面板 -->
      <aside class="pose-side">
        <h1 class="pose-side__title">姿态识别</h1>
        <p class="pose-side__desc">站到屏幕前，试试以下动作</p>

        <div class="pose-card">
          <span class="pose-card__label">当前动作</span>
          <span class="pose-card__value" :class="{ 'pose-card__value--none': currentAction === 'unknown' }">
            {{ currentLabel }}
          </span>
        </div>

        <div class="pose-card">
          <span class="pose-card__label">置信度</span>
          <div class="pose-conf">
            <div class="pose-conf__bar">
              <i
                class="pose-conf__fill"
                :style="{ width: `${Math.round(currentConfidence * 100)}%` }"
              />
              <i class="pose-conf__mark" />
            </div>
            <span class="pose-conf__num" :class="{ 'pose-conf__num--pass': currentConfidence >= 0.85 }">
              {{ Math.round(currentConfidence * 100) }}%
            </span>
          </div>
          <p class="pose-card__tip">置信度 ≥ 85% 触发讲解特效</p>
        </div>

        <div class="pose-actions">
          <span class="pose-action" :class="{ 'pose-action--active': currentAction === 'salute' }">敬礼</span>
          <span class="pose-action" :class="{ 'pose-action--active': currentAction === 'mill' }">推磨</span>
          <span class="pose-action" :class="{ 'pose-action--active': currentAction === 'wave' }">挥手</span>
        </div>

        <p class="pose-side__note">
          {{
            poseStream.status.value === 'live'
              ? '实时识别已连接 · 保持动作直到置信度达标'
              : '识别服务未连接，当前播放模拟演示数据'
          }}
        </p>
      </aside>
    </div>

    <!-- W-T-06/W-T-07：达标全屏讲解页（光环特效 + 讲解音频 + 成就二维码） -->
    <transition name="effect-fade">
      <div v-if="effectVisible" class="pose-effect">
        <div class="pose-effect__ring" />
        <div class="pose-effect__ring pose-effect__ring--delay" />

        <span class="pose-effect__badge">动作识别成功</span>
        <h2 class="pose-effect__action">{{ effectAction }}</h2>
        <p class="pose-effect__title">
          {{ effectTrigger?.title || '为您解锁对应讲解内容' }}
        </p>

        <!-- 讲解音频 -->
        <div v-if="audioSrc" class="pose-effect__audio">
          <button
            type="button"
            class="pose-effect__audio-btn"
            :class="{ 'pose-effect__audio-btn--playing': audioPlaying }"
            :disabled="audioError"
            @click="toggleAudio"
          >
            <svg v-if="!audioPlaying" viewBox="0 0 24 24" width="26" height="26" fill="currentColor">
              <path d="M8 5.5v13a1 1 0 0 0 1.52.86l10.2-6.5a1 1 0 0 0 0-1.72L9.52 4.64A1 1 0 0 0 8 5.5z" />
            </svg>
            <svg v-else viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
              <rect x="6.5" y="5" width="3.6" height="14" rx="1.2" />
              <rect x="13.9" y="5" width="3.6" height="14" rx="1.2" />
            </svg>
          </button>
          <div class="pose-effect__audio-meta">
            <span class="pose-effect__audio-label">
              {{ audioError ? '讲解音频暂不可用' : audioPlaying ? '讲解播放中…' : '讲解音频已就绪，点击播放' }}
            </span>
            <div class="pose-effect__audio-wave" :class="{ 'pose-effect__audio-wave--on': audioPlaying }">
              <i /><i /><i /><i /><i /><i /><i />
            </div>
          </div>
          <audio
            ref="audioRef"
            :src="audioSrc"
            preload="auto"
            @play="audioPlaying = true"
            @pause="audioPlaying = false"
            @ended="audioPlaying = false"
            @error="audioError = true"
          />
        </div>

        <!-- 右下角成就二维码（临时 token，5 分钟有效） -->
        <div class="pose-effect__qr" :class="{ 'pose-effect__qr--expired': claim.expired.value }">
          <template v-if="!claim.expired.value">
            <img v-if="qrDataUrl" :src="qrDataUrl" class="pose-effect__qr-img" alt="成就领取二维码" />
            <p class="pose-effect__qr-title">扫码领取「{{ effectAction }}」成就勋章</p>
            <p class="pose-effect__qr-tip">
              手机扫码打开 · <b>{{ claim.countdown.value }}</b> 内有效
            </p>
          </template>
          <template v-else>
            <div class="pose-effect__qr-expired">
              <svg viewBox="0 0 24 24" width="44" height="44" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                <circle cx="12" cy="12" r="9" />
                <path d="M12 7v6M12 16.2v.3" />
              </svg>
              <span>二维码已失效</span>
            </div>
            <button type="button" class="pose-effect__qr-refresh" @click="refreshQr">
              刷新二维码
            </button>
          </template>
        </div>

        <button type="button" class="pose-effect__close" @click="hideEffect">继续体验</button>
      </div>
    </transition>
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

.pose-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 32px;
  padding: 8px 40px 40px;
}

// ===== 摄像头舞台 =====
.pose-stage {
  position: relative;
  min-height: 0;
  border-radius: @radius-lg;
  border: 2px solid rgba(196, 30, 58, 0.55);
  background: linear-gradient(180deg, #1a1a1a 0%, #121212 100%);
  box-shadow:
    inset 0 0 60px rgba(196, 30, 58, 0.18),
    0 0 36px rgba(196, 30, 58, 0.25);
  overflow: hidden;

  &__video {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    // 镜像显示（自拍视角），骨架画布同步镜像保持重合
    transform: scaleX(-1);
    opacity: 0;
    transition: opacity 0.4s ease;

    &--on {
      opacity: 1;
    }
  }
  &__canvas {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    transform: scaleX(-1); // 与视频同向镜像
    pointer-events: none;
  }
  &__placeholder {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 20px;
    color: #4d4d4d;

    span {
      font-size: 1.5rem;
      letter-spacing: 2px;
    }
  }
  &__no-person {
    position: absolute;
    left: 50%;
    bottom: 28px;
    transform: translateX(-50%);
    padding: 14px 28px;
    border-radius: 999px;
    background-color: rgba(0, 0, 0, 0.65);
    border: 1px solid rgba(245, 197, 66, 0.45);
    color: @color-touch-gold;
    font-size: 1.5rem;
    letter-spacing: 2px;
  }
  &__badge {
    position: absolute;
    top: 20px;
    right: 20px;
    padding: 8px 20px;
    border-radius: 999px;
    border: 1px solid rgba(245, 197, 66, 0.5);
    background-color: rgba(0, 0, 0, 0.55);
    color: @color-touch-gold;
    font-size: 1.2rem;
    letter-spacing: 2px;

    &--live {
      border-color: rgba(80, 220, 130, 0.6);
      color: #69e08c;
    }
    &--connecting,
    &--reconnecting {
      border-color: rgba(245, 197, 66, 0.5);
      color: @color-touch-gold;
      animation: badge-blink 1.2s ease-in-out infinite;
    }
  }
}

@keyframes badge-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}

// ===== 达标全屏讲解页（W-T-06 特效 / W-T-07 音频 + 二维码） =====
.pose-effect {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 28px;
  background: radial-gradient(
    circle at center,
    rgba(196, 30, 58, 0.55) 0%,
    rgba(10, 8, 8, 0.94) 70%
  );

  &__ring {
    position: absolute;
    width: 560px;
    height: 560px;
    border-radius: 50%;
    border: 4px solid rgba(245, 197, 66, 0.7);
    animation: effect-ring 2.2s ease-out infinite;
    pointer-events: none;

    &--delay {
      animation-delay: 1.1s;
    }
  }
  &__badge {
    padding: 10px 32px;
    border-radius: 999px;
    border: 1px solid @color-touch-gold;
    color: @color-touch-gold;
    font-size: 1.6rem;
    letter-spacing: 6px;
    pointer-events: none;
  }
  &__action {
    margin: 0;
    font-size: 9rem;
    font-weight: 900;
    letter-spacing: 24px;
    text-indent: 24px;
    color: #fff;
    text-shadow:
      0 0 40px rgba(245, 197, 66, 0.8),
      0 0 90px rgba(196, 30, 58, 0.7);
    pointer-events: none;
  }
  &__title {
    margin: 0;
    max-width: 60vw;
    text-align: center;
    font-size: 2.4rem;
    line-height: 1.6;
    letter-spacing: 2px;
    color: @color-touch-gold;
    pointer-events: none;
  }

  // ---- 讲解音频条 ----
  &__audio {
    display: flex;
    align-items: center;
    gap: 22px;
    padding: 18px 36px 18px 20px;
    border-radius: 999px;
    border: 1px solid rgba(245, 197, 66, 0.5);
    background-color: rgba(0, 0, 0, 0.5);
    backdrop-filter: blur(4px);
    pointer-events: auto;
  }
  &__audio-btn {
    flex-shrink: 0;
    width: 64px;
    height: 64px;
    border-radius: 50%;
    border: none;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(160deg, #f5d572 0%, @color-touch-gold 60%, #c99a1f 100%);
    color: #4a2c00;
    cursor: pointer;
    box-shadow: 0 0 24px rgba(245, 197, 66, 0.45);
    transition: transform 0.2s ease;

    &:hover:not(:disabled) {
      transform: scale(1.06);
    }
    &:disabled {
      background: #555;
      color: #999;
      box-shadow: none;
      cursor: not-allowed;
    }
  }
  &__audio-meta {
    display: flex;
    flex-direction: column;
    gap: 8px;
    min-width: 240px;
  }
  &__audio-label {
    font-size: 1.5rem;
    letter-spacing: 2px;
    color: rgba(255, 255, 255, 0.9);
  }
  &__audio-wave {
    display: flex;
    align-items: center;
    gap: 5px;
    height: 16px;

    i {
      width: 4px;
      height: 6px;
      border-radius: 2px;
      background-color: rgba(245, 197, 66, 0.45);
    }
    &--on i {
      animation: audio-bar 0.9s ease-in-out infinite;
      &:nth-child(1) { animation-delay: 0s; }
      &:nth-child(2) { animation-delay: 0.12s; }
      &:nth-child(3) { animation-delay: 0.24s; }
      &:nth-child(4) { animation-delay: 0.36s; }
      &:nth-child(5) { animation-delay: 0.24s; }
      &:nth-child(6) { animation-delay: 0.12s; }
      &:nth-child(7) { animation-delay: 0s; }
    }
  }

  // ---- 右下角成就二维码 ----
  &__qr {
    position: absolute;
    right: 40px;
    bottom: 40px;
    width: 320px;
    padding: 22px 22px 20px;
    border-radius: 18px;
    background-color: #fff;
    color: #2b1d18;
    text-align: center;
    box-shadow: 0 12px 48px rgba(0, 0, 0, 0.5);
    pointer-events: auto;
    transition: opacity 0.3s ease;
  }
  &__qr-img {
    display: block;
    width: 272px;
    height: 272px;
    margin: 0 auto 14px;
  }
  &__qr-title {
    margin: 0 0 6px;
    font-size: 1.5rem;
    font-weight: 700;
    line-height: 1.5;
    color: #c41e3a;
  }
  &__qr-tip {
    margin: 0;
    font-size: 1.25rem;
    color: #7a6e68;

    b {
      color: #c41e3a;
      font-variant-numeric: tabular-nums;
    }
  }
  &__qr-expired {
    height: 272px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 14px;
    color: #9a9a9a;
    font-size: 1.7rem;
    letter-spacing: 2px;
    border: 2px dashed #d8d2cd;
    border-radius: 12px;
    margin-bottom: 14px;
  }
  &__qr-refresh {
    margin-top: 12px;
    padding: 12px 36px;
    border-radius: 999px;
    border: 1px solid #c41e3a;
    background-color: #fff;
    color: #c41e3a;
    font-size: 1.5rem;
    letter-spacing: 2px;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      background-color: #c41e3a;
      color: #fff;
    }
  }
  &__qr--expired {
    opacity: 0.92;
  }

  // ---- 底部关闭 ----
  &__close {
    position: absolute;
    bottom: 44px;
    left: 50%;
    transform: translateX(-50%);
    padding: 16px 56px;
    border-radius: 999px;
    border: 1px solid rgba(255, 255, 255, 0.55);
    background-color: rgba(0, 0, 0, 0.45);
    color: #fff;
    font-size: 1.7rem;
    letter-spacing: 6px;
    cursor: pointer;
    pointer-events: auto;
    transition: all 0.2s ease;

    &:hover {
      border-color: @color-touch-gold;
      color: @color-touch-gold;
    }
  }
}

@keyframes audio-bar {
  0%, 100% { height: 5px; }
  50% { height: 16px; }
}

@keyframes effect-ring {
  0% {
    transform: scale(0.55);
    opacity: 0.9;
  }
  100% {
    transform: scale(1.35);
    opacity: 0;
  }
}

.effect-fade-enter-active,
.effect-fade-leave-active {
  transition: opacity 0.35s ease;
}
.effect-fade-enter-from,
.effect-fade-leave-to {
  opacity: 0;
}

// ===== 右侧状态面板 =====
.pose-side {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 36px 32px;
  border-radius: @radius-lg;
  border: 1px solid @color-touch-border;
  background-color: @color-touch-panel;
  overflow-y: auto;

  &__title {
    margin: 0;
    font-size: 2.6rem;
    font-weight: 700;
  }
  &__desc {
    margin: 0;
    font-size: 1.5rem;
    color: @color-touch-text-secondary;
  }
  &__note {
    margin: auto 0 0;
    font-size: 1.2rem;
    line-height: 1.8;
    color: #5c5c5c;
  }
}

.pose-card {
  padding: 28px 28px;
  border-radius: 16px;
  border: 1px solid @color-touch-border;
  background-color: @color-touch-panel-light;
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__label {
    font-size: 1.3rem;
    letter-spacing: 2px;
    color: @color-touch-text-secondary;
  }
  &__value {
    font-size: 3.4rem;
    font-weight: 800;
    color: @color-touch-gold;
    letter-spacing: 6px;

    &--none {
      color: #6b6b6b;
    }
  }
  &__tip {
    margin: 0;
    font-size: 1.2rem;
    color: #5c5c5c;
  }
}

// 置信度进度条（85% 处达标刻度线）
.pose-conf {
  display: flex;
  align-items: center;
  gap: 20px;

  &__bar {
    position: relative;
    flex: 1;
    height: 18px;
    border-radius: 999px;
    background-color: #0d0d0d;
    border: 1px solid @color-touch-border;
    overflow: hidden;
  }
  &__fill {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    border-radius: 999px;
    background: linear-gradient(90deg, @color-primary 0%, #f5c542 100%);
    transition: width 0.15s linear;
  }
  &__mark {
    position: absolute;
    left: 85%;
    top: -2px;
    bottom: -2px;
    width: 2px;
    background-color: rgba(255, 255, 255, 0.55);
  }
  &__num {
    flex-shrink: 0;
    min-width: 76px;
    text-align: right;
    font-size: 1.9rem;
    font-weight: 700;
    color: @color-touch-text;

    &--pass {
      color: @color-touch-gold;
    }
  }
}

// 支持动作列表
.pose-actions {
  display: flex;
  gap: 16px;
}
.pose-action {
  flex: 1;
  text-align: center;
  padding: 20px 0;
  border-radius: 14px;
  border: 1px solid @color-touch-border;
  background-color: @color-touch-panel-light;
  color: @color-touch-text-secondary;
  font-size: 1.7rem;
  font-weight: 600;
  letter-spacing: 4px;
  transition: all 0.25s ease;

  &--active {
    border-color: @color-primary;
    background: linear-gradient(160deg, #d3222f 0%, @color-primary 55%, #9e1530 100%);
    color: #fff;
    box-shadow: 0 0 20px rgba(196, 30, 58, 0.4);
  }
}
</style>
