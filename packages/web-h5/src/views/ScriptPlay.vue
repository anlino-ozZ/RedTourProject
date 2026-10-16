<script setup lang="ts">
/**
 * 沉浸式剧本（按 ?scriptId= 加载 mockScripts 中对应剧本）
 * 流程：选择角色 → 剧情开场 → 任务循环（动作/问答/打卡，失败重试+引导）
 *      → 全部完成 → 结局剧情 → 解锁成就徽章 + 积分 + 分享/收藏
 * 数据暂为 Mock，动作识别/定位打卡后续接入 ai-engine
 */
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getScriptById, getAchievements, type Script, type AchievementBadge, type ScriptTask } from '@/api/scripts'
import {
  playBadgeShineSound,
  playCorrectSound,
  playFanfareSound,
  playScoreSound,
  playTapSound,
  playWrongSound,
} from '@/utils/sound'
import { addPoints } from '@/utils/levelStorage'
import { unlockBadge } from '@/utils/achievementStorage'
import { usePoseCamera } from '@/composables/usePoseCamera'

const router = useRouter()
const route = useRoute()

/** 当前剧本：按路由参数异步加载，无效/缺失时回退第一部可玩剧本 */
const script = ref<Script | null>(null)
/** 成就徽章列表（用于查找奖励徽章） */
const badges = ref<AchievementBadge[]>([])

async function loadScript() {
  const id = route.query.scriptId as string
  let s = await getScriptById(id)
  if (!s) {
    // 回退：取第一部可玩剧本
    const { getScripts } = await import('@/api/scripts')
    const all = await getScripts()
    s = all.find((x) => !x.locked) ?? all[0]
  }
  script.value = s ?? null
}
loadScript()
getAchievements().then((list) => (badges.value = list))

const rewardBadge = computed(() =>
  script.value ? badges.value.find((b) => b.id === script.value!.reward.badgeId) : undefined,
)

// 路由参数变化时重新加载剧本
watch(() => route.query.scriptId, loadScript)

// ===== 流程状态机 =====
type Stage = 'role' | 'intro' | 'tasks' | 'ending' | 'reward'
const stage = ref<Stage>('role')
const roleId = ref('')
const role = computed(() => script.value?.roles.find((r) => r.id === roleId.value))

const taskIndex = ref(0)
const currentTask = computed(() => script.value?.tasks[taskIndex.value] as ScriptTask | undefined)
const score = ref(0)
const overallProgress = computed(() => {
  if (stage.value === 'role' || stage.value === 'intro') return 0
  if (stage.value === 'ending' || stage.value === 'reward') return 100
  const len = script.value?.tasks.length ?? 1
  return Math.round((taskIndex.value / len) * 100)
})

const stageLabel = computed(() => {
  switch (stage.value) {
    case 'role': return '选择角色'
    case 'intro': return '剧情开场'
    case 'tasks': return `任务 ${taskIndex.value + 1}/${script.value?.tasks.length ?? 0}`
    case 'ending': return '结局'
    case 'reward': return '通关'
  }
})

// ===== 轻提示 =====
const toastText = ref('')
let toastTimer: ReturnType<typeof setTimeout> | undefined
function showToast(text: string) {
  toastText.value = text
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toastText.value = ''), 1800)
}

// ===== 打字机（开场/结局共用，打字中点击可一键展开） =====
const displayedText = ref('')
const isTyping = ref(false)
let typeTimer: ReturnType<typeof setInterval> | undefined
let typeFullText = ''
let typeDoneCb: (() => void) | undefined

function typewriter(text: string, done?: () => void) {
  displayedText.value = ''
  isTyping.value = true
  typeFullText = text
  typeDoneCb = done
  let i = 0
  if (typeTimer) clearInterval(typeTimer)
  typeTimer = setInterval(() => {
    i++
    displayedText.value = text.slice(0, i)
    if (i >= text.length) finishTyping()
  }, 40)
}

/** 立即补全剩余文字并触发完成回调（快进/一键展开） */
function finishTyping() {
  if (typeTimer) clearInterval(typeTimer)
  displayedText.value = typeFullText
  isTyping.value = false
  const cb = typeDoneCb
  typeDoneCb = undefined
  cb?.()
}

/** 打字过程中点击剧情文本：直接展开全部 */
function onDialogTap() {
  if (isTyping.value) finishTyping()
}

// ===== 选择角色 =====
function pickRole(id: string) {
  roleId.value = id
  playTapSound()
}

function startScript() {
  if (!roleId.value || !script.value) {
    showToast('请先选择一个角色')
    return
  }
  stage.value = 'intro'
  typewriter(script.value.intro)
}

// ===== 任务循环 =====
// 动作任务
const actionState = ref<'idle' | 'recognizing' | 'done'>('idle')
/** 剧本动作名 → 后端动作码（ai-engine 目前仅识别 salute / mill / wave） */
const SCRIPT_ACTION_TO_POSE: Record<string, string> = {
  军礼: 'salute',
  敬礼: 'salute',
  挥手: 'wave',
  推磨: 'mill',
}
/** 姿态识别：真实开启摄像头并调用 POST /pose/recognize */
const {
  videoRef: poseVideoRef,
  cameraOn: poseCameraOn,
  cameraError: poseCameraError,
  recognizeError: poseRecognizeError,
  keypoints: poseKeypoints,
  scanning: poseScanning,
  startCamera: startPoseCamera,
  stopCamera: stopPoseCamera,
  startScan: startPoseScan,
  stopScan: stopPoseScan,
} = usePoseCamera()
/** 动作任务操作提示 */
const poseTip = computed(() => {
  const target = currentTask.value?.action ?? '动作'
  if (actionState.value === 'done') return '识别成功，已自动结束识别'
  if (poseScanning.value) return '识别中，请保持动作…（再点按钮可停止）'
  if (!poseCameraOn.value) return '点击按钮开启摄像头'
  return `对准镜头做出「${target}」，点按钮开始识别`
})

/** COCO 17 点骨架连线，用于在预览画面上叠加姿态提示 */
const POSE_SKELETON_EDGES: [number, number][] = [
  [0, 1], [0, 2], [1, 3], [2, 4],
  [5, 6], [5, 7], [7, 9], [6, 8], [8, 10],
  [5, 11], [6, 12], [11, 12],
  [11, 13], [13, 15], [12, 14], [14, 16],
]
const poseCanvasRef = ref<HTMLCanvasElement | null>(null)

/** 把最近一帧的骨架画到预览上（预览是 object-fit: cover + 镜像，这里做同样的坐标映射） */
function drawPoseSkeleton() {
  const canvas = poseCanvasRef.value
  const video = poseVideoRef.value
  if (!canvas || !video) return
  const boxWidth = canvas.clientWidth
  const boxHeight = canvas.clientHeight
  if (!boxWidth || !boxHeight) return
  if (canvas.width !== boxWidth || canvas.height !== boxHeight) {
    canvas.width = boxWidth
    canvas.height = boxHeight
  }
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.clearRect(0, 0, boxWidth, boxHeight)
  const points = poseKeypoints.value
  if (points.length !== 17) return

  const frameAspect =
    video.videoWidth && video.videoHeight ? video.videoWidth / video.videoHeight : 4 / 3
  const boxAspect = boxWidth / boxHeight
  let drawWidth: number
  let drawHeight: number
  let offsetX: number
  let offsetY: number
  if (frameAspect > boxAspect) {
    drawWidth = boxWidth
    drawHeight = boxWidth / frameAspect
    offsetX = 0
    offsetY = (boxHeight - drawHeight) / 2
  } else {
    drawHeight = boxHeight
    drawWidth = boxHeight * frameAspect
    offsetX = (boxWidth - drawWidth) / 2
    offsetY = 0
  }
  const toXY = (index: number): [number, number] => {
    const point = points[index]
    const x = offsetX + point[0] * drawWidth
    const y = offsetY + point[1] * drawHeight
    // 预览画面做了水平镜像，坐标同步翻转才能对齐
    return [boxWidth - x, y]
  }

  ctx.lineCap = 'round'
  ctx.lineWidth = 3
  ctx.strokeStyle = '#ff2d55'
  for (const [from, to] of POSE_SKELETON_EDGES) {
    const [x1, y1] = toXY(from)
    const [x2, y2] = toXY(to)
    ctx.beginPath()
    ctx.moveTo(x1, y1)
    ctx.lineTo(x2, y2)
    ctx.stroke()
  }
  for (let index = 0; index < 17; index += 1) {
    const [x, y] = toXY(index)
    ctx.beginPath()
    ctx.arc(x, y, 4, 0, Math.PI * 2)
    ctx.fillStyle = '#fff'
    ctx.fill()
    ctx.lineWidth = 2
    ctx.strokeStyle = '#ff2d55'
    ctx.stroke()
  }
}

watch(poseKeypoints, () => drawPoseSkeleton())
watch(poseCameraOn, () => nextTick(drawPoseSkeleton))
/** 连续识别失败次数：用于逐级给出更具体的引导 */
const poseFailCount = ref(0)
/** 各动作的关键要领（对应 ai-engine 判定规则：敬礼看手腕是否到耳边，挥手看手腕是否高过肩，推磨看双手高度与间距） */
const POSE_ACTION_GUIDE: Record<string, string[]> = {
  军礼: ['一只手抬到耳边、手掌贴向耳侧', '手要举过肩膀，手肘抬起来别夹太紧'],
  挥手: ['抬起一只手，手腕要高过肩膀', '手掌朝向镜头，左右摆动'],
  推磨: ['双手放在腰腹高度、两只手基本齐平', '双手间距大约一个肩膀宽'],
}
/** 通用要点：任务动作没有专属要领时使用 */
const POSE_GUIDE_FALLBACK = ['上半身完整入镜（能看到肩膀和腰）', '正对镜头，动作保持 1 秒再点识别']
/** 当前动作的要领列表 */
const poseGuide = computed(
  () => POSE_ACTION_GUIDE[currentTask.value?.action ?? ''] ?? POSE_GUIDE_FALLBACK,
)
// 问答任务
const quizPicked = ref(-1)
const quizWrong = ref(false)
const quizCorrect = ref(false)
const showGuide = ref(false)
// 打卡任务
const checkinState = ref<'idle' | 'locating' | 'done'>('idle')
// 积分飘字
const flyPoints = ref('')

// ===== 音效：统一使用 @/utils/sound 合成（无需音频素材）=====

function flyup(points: number, soundDelaySec = 0) {
  score.value += points
  flyPoints.value = `+${points} 积分`
  playScoreSound(soundDelaySec)
  setTimeout(() => (flyPoints.value = ''), 1200)
}

/** 任务完成 → 推进剧情 + 获得积分；所有任务完成 → 结局 */
function completeTask(scoreDelaySec = 0) {
  const task = currentTask.value
  if (!task || !script.value) return
  flyup(task.points, scoreDelaySec)
  showToast(`任务完成，剧情推进！`)
  setTimeout(() => {
    // 重置本任务状态
    actionState.value = 'idle'
    closePoseCamera()
    quizPicked.value = -1
    quizWrong.value = false
    quizCorrect.value = false
    showGuide.value = false
    checkinState.value = 'idle'
    if (taskIndex.value < script.value!.tasks.length - 1) {
      taskIndex.value++
    } else {
      stage.value = 'ending'
      typewriter(script.value!.ending)
    }
  }, 1000)
}

/** 动作任务：开启摄像头 → 连续识别 → 命中目标动作自动结束识别 */
async function doAction() {
  if (actionState.value === 'done') return
  playTapSound()
  if (!poseCameraOn.value) {
    poseFailCount.value = 0
    await startPoseCamera()
    return
  }
  if (poseScanning.value) {
    // 识别中再点一次 = 主动停止
    stopPoseScan()
    actionState.value = 'idle'
    return
  }
  const target = currentTask.value?.action ?? ''
  const expected = SCRIPT_ACTION_TO_POSE[target] ?? ''
  poseFailCount.value = 0
  actionState.value = 'recognizing'
  startPoseScan((res) => {
    if (!expected || res.action === expected) {
      actionState.value = 'done'
      poseFailCount.value = 0
      completeTask()
      return true
    }
    // 没命中就继续识别，同时累加失败次数用于逐步给出更具体的引导
    poseFailCount.value += 1
    return false
  })
}

/** 动作任务结束或切换任务时关闭摄像头，避免指示灯长亮 */
function closePoseCamera() {
  poseFailCount.value = 0
  stopPoseCamera()
}

/** 问答任务：答错 → 提示重试 + 引导帮助；答对 → 过 */
function pickQuiz(i: number) {
  if (quizCorrect.value) return
  const task = currentTask.value
  if (!task?.quiz) return
  quizPicked.value = i
  const quiz = task.quiz
  if (i === quiz.answer) {
    quizCorrect.value = true
    playCorrectSound()
    // 答对琶音播完后再响积分音，避免叠音浑浊
    completeTask(0.34)
  } else {
    quizWrong.value = true
    showGuide.value = true
    playWrongSound()
    showToast('答错了，看看引导提示再试一次')
  }
}

/** 打卡任务：模拟定位打卡 */
function doCheckin() {
  if (checkinState.value !== 'idle') return
  checkinState.value = 'locating'
  setTimeout(() => {
    checkinState.value = 'done'
    completeTask()
  }, 1500)
}

// ===== 结局 → 奖励 =====
const collected = ref(false)

function toReward() {
  if (isTyping.value) return
  stage.value = 'reward'
  // 剧本通关：累计积分落库（等级体系），重玩重复通关也计分
  addPoints(score.value)
  // 徽章解锁号角
  playFanfareSound()
  // 写入解锁状态（互动中心徽章墙据此点亮）；仅首次解锁时错峰叠徽章闪耀音
  const { isNew } = unlockBadge(script.value!.reward.badgeId)
  badgeIsNew.value = isNew
  if (isNew) shineTimer = setTimeout(() => playBadgeShineSound(), 900)
  // 撒花彩带（确定性伪随机，避免每次渲染位置跳动）
  confetti.value = Array.from({ length: 28 }, (_, i) => ({
    left: `${(i * 37) % 100}%`,
    bg: CONFETTI_COLORS[i % CONFETTI_COLORS.length],
    delay: `${((i * 13) % 10) / 10}s`,
    dur: `${2.4 + ((i * 7) % 10) / 8}s`,
  }))
}

// ===== 通关奖励：解锁动画与撒花 =====
const badgeIsNew = ref(false)
const confetti = ref<Array<{ left: string; bg: string; delay: string; dur: string }>>([])
const CONFETTI_COLORS = ['#c41e3a', '#d4af37', '#e8c96a', '#f5f0e6', '#e2703a', '#8f1d2c']
let shineTimer: ReturnType<typeof setTimeout> | undefined

function onShare() {
  playTapSound()
  showToast('分享海报已生成，快去发给小伙伴吧')
}

function onCollect() {
  collected.value = !collected.value
  playTapSound()
  showToast(collected.value ? '已收藏剧本' : '已取消收藏')
}

function backToInteractive() {
  router.push('/interactive/scripts')
}

function onClose() {
  if (window.history.length > 1) router.back()
  else router.push('/interactive/scripts')
}

function onHelp() {
  showToast('按任务提示完成动作/答题/打卡即可推进剧情')
}

onUnmounted(() => {
  if (typeTimer) clearInterval(typeTimer)
  if (toastTimer) clearTimeout(toastTimer)
  if (shineTimer) clearTimeout(shineTimer)
})
</script>

<template>
  <div class="script-play">
    <!-- 剧本加载中 -->
    <div v-if="!script" class="script-play__loading">剧本加载中...</div>

    <template v-else>
    <!-- ===== 顶部操作栏 ===== -->
    <div class="topbar">
      <button class="icon-btn" aria-label="退出剧本" @click="onClose">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M18 6L6 18M6 6l12 12" />
        </svg>
      </button>
      <span class="topbar__pill">
        <i class="dot" />{{ script.title }} · {{ stageLabel }}
      </span>
      <button class="icon-btn icon-btn--round" aria-label="玩法说明" @click="onHelp">?</button>
    </div>

    <!-- 总进度条（任务循环阶段） -->
    <div v-if="stage !== 'role' && stage !== 'intro'" class="progress-track">
      <i :style="{ width: overallProgress + '%' }" />
    </div>

    <!-- ===== 阶段一：选择角色 ===== -->
    <div v-if="stage === 'role'" class="stage stage--role">
      <h1 class="stage__headline">选择你的角色</h1>
      <p class="stage__sub">每位角色都将看到专属剧情视角</p>
      <div class="role-list">
        <div
          v-for="r in script.roles"
          :key="r.id"
          class="role-card"
          :class="{ 'is-picked': roleId === r.id }"
          :style="roleId === r.id ? { borderColor: r.color } : undefined"
          @click="pickRole(r.id)"
        >
          <span class="role-card__avatar" :style="{ background: r.color }">
            <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round">
              <circle cx="12" cy="8" r="4" />
              <path d="M4 21c0-4 3.6-6 8-6s8 2 8 6" />
            </svg>
          </span>
          <div class="role-card__name">{{ r.name }}</div>
          <div class="role-card__title">{{ r.title }}</div>
          <div class="role-card__desc">{{ r.desc }}</div>
          <span v-if="roleId === r.id" class="role-card__check">✓ 已选择</span>
        </div>
      </div>
      <button class="primary-btn" :disabled="!roleId" @click="startScript">开始剧本</button>
    </div>

    <!-- ===== 阶段二：剧情开场介绍 ===== -->
    <div v-else-if="stage === 'intro'" class="stage stage--intro">
      <div class="dialog-card">
        <div class="dialog-card__head">
          <span class="dialog-card__avatar">
            <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round">
              <path d="M12 2l3 6 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1z" />
            </svg>
          </span>
          <span class="dialog-card__speaker">剧情开场</span>
        </div>
        <p
          class="dialog-card__text"
          :class="{ 'is-skippable': isTyping }"
          @click="onDialogTap"
        >
          {{ displayedText }}<i v-if="isTyping" class="cursor" />
        </p>
        <button
          v-if="isTyping"
          class="dialog-card__skip"
          type="button"
          @click="onDialogTap"
        >
          点击一键展开 ▶
        </button>
      </div>
      <button class="primary-btn" :disabled="isTyping" @click="stage = 'tasks'">
        开始第一个任务
      </button>
    </div>

    <!-- ===== 阶段三：任务循环 ===== -->
    <div v-else-if="stage === 'tasks' && currentTask" class="stage stage--tasks">
      <div class="task-head">
        <span class="task-head__label">{{ currentTask.title }}</span>
        <span class="task-head__score">积分 {{ score }}</span>
      </div>
      <p class="task-head__hint">{{ currentTask.hint }}</p>

      <!-- 动作任务：真实调用摄像头 + POST /pose/recognize -->
      <div v-if="currentTask.type === 'action'" class="task-body task-body--action">
        <div class="gesture-wrap">
          <div v-if="poseCameraOn" class="pose-cam">
            <video ref="poseVideoRef" class="pose-cam__video" playsinline muted />
            <canvas ref="poseCanvasRef" class="pose-cam__skeleton" />
            <span class="pose-cam__badge">{{ currentTask.action }}</span>
          </div>
          <button
            class="gesture-btn"
            :class="{ 'is-done': actionState === 'done', 'is-busy': actionState === 'recognizing' }"
            :aria-label="currentTask.action"
            @click="doAction"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 11V6a2 2 0 00-4 0v5M14 10V4a2 2 0 00-4 0v6M10 10.5V6a2 2 0 00-4 0v8" />
              <path d="M18 8a2 2 0 114 0v6a8 8 0 01-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 012.83-2.82L7 15" />
            </svg>
          </button>
          <span class="gesture-wrap__tip">{{ poseTip }}</span>
          <div class="pose-guide" :class="{ 'is-fail': poseFailCount > 0 }">
            <p class="pose-guide__title">
              {{ poseFailCount > 0 ? '没识别到，按下面要点再来一次' : `「${currentTask.action}」动作要点` }}
            </p>
            <ul class="pose-guide__list">
              <li v-for="(item, index) in poseGuide" :key="index">{{ item }}</li>
            </ul>
            <p v-if="poseFailCount >= 2" class="pose-guide__extra">
              还是不行？离镜头 0.5~1.5 米、上半身完整入镜、光线亮一点，动作保持 1 秒再点识别。
            </p>
          </div>
          <p v-if="poseCameraError" class="pose-cam__error">{{ poseCameraError }}</p>
          <p v-if="poseRecognizeError" class="pose-cam__error">{{ poseRecognizeError }}</p>
        </div>
      </div>

      <!-- 问答任务 -->
      <div v-else-if="currentTask.type === 'quiz'" class="task-body">
        <div class="quiz-card" :class="{ 'is-shake': quizWrong }">
          <p class="quiz-card__q">{{ currentTask.quiz!.question }}</p>
          <button
            v-for="(opt, i) in currentTask.quiz!.options"
            :key="i"
            class="quiz-card__opt"
            :class="{
              'is-wrong': quizWrong && quizPicked === i,
              'is-right': quizCorrect && quizPicked === i,
            }"
            @click="pickQuiz(i)"
          >
            {{ String.fromCharCode(65 + i) }}. {{ opt }}
          </button>
          <div v-if="showGuide" class="quiz-card__guide">
            💡 引导帮助：{{ currentTask.quiz!.guide }}
          </div>
        </div>
      </div>

      <!-- 打卡任务 -->
      <div v-else class="task-body task-body--checkin">
        <div class="checkin-spot">
          <span class="checkin-spot__pin">📍</span>
          <span class="checkin-spot__name">{{ currentTask.spot }}</span>
        </div>
        <button
          class="primary-btn"
          :disabled="checkinState !== 'idle'"
          @click="doCheckin"
        >
          {{ checkinState === 'idle' && '立即打卡'
            || checkinState === 'locating' && '定位中…'
            || '打卡成功！' }}
        </button>
      </div>

      <!-- 积分飘字 -->
      <transition name="fly">
        <span v-if="flyPoints" class="fly-points">{{ flyPoints }}</span>
      </transition>
    </div>

    <!-- ===== 阶段四：结局剧情 ===== -->
    <div v-else-if="stage === 'ending'" class="stage stage--ending">
      <div class="dialog-card">
        <div class="dialog-card__head">
          <span class="dialog-card__avatar">
            <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round">
              <path d="M12 2l3 6 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1z" />
            </svg>
          </span>
          <span class="dialog-card__speaker">结局 · 情报送达</span>
        </div>
        <p
          class="dialog-card__text"
          :class="{ 'is-skippable': isTyping }"
          @click="onDialogTap"
        >
          {{ displayedText }}<i v-if="isTyping" class="cursor" />
        </p>
        <button
          v-if="isTyping"
          class="dialog-card__skip"
          type="button"
          @click="onDialogTap"
        >
          点击一键展开 ▶
        </button>
      </div>
      <button class="primary-btn" :disabled="isTyping" @click="toReward">查看通关奖励</button>
    </div>

    <!-- ===== 阶段五：通关奖励 ===== -->
    <div v-else class="stage stage--reward">
      <!-- 撒花彩带 -->
      <div class="confetti" aria-hidden="true">
        <span
          v-for="(c, i) in confetti"
          :key="i"
          class="confetti__piece"
          :style="{
            left: c.left,
            background: c.bg,
            animationDelay: c.delay,
            animationDuration: c.dur,
          }"
        />
      </div>
      <p class="reward__caption">
        {{ badgeIsNew ? '恭喜通关，解锁新成就徽章' : '恭喜通关' }}
      </p>
      <div class="reward__badge">
        <span class="reward__badge-circle">
          <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="8" r="6" />
            <path d="M15.5 13l1.5 8-5-3-5 3 1.5-8" />
          </svg>
        </span>
        <span class="reward__badge-name">{{ rewardBadge?.name ?? '成就徽章' }}</span>
      </div>
      <div class="reward__score">
        <span>本局获得</span>
        <strong>{{ score }} 积分</strong>
      </div>
      <div class="reward__actions">
        <button class="reward-btn" @click="onShare">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="18" cy="5" r="3" /><circle cx="6" cy="12" r="3" /><circle cx="18" cy="19" r="3" />
            <path d="M8.6 13.5l6.8 4M15.4 6.5l-6.8 4" />
          </svg>
          分享
        </button>
        <button class="reward-btn" :class="{ 'is-on': collected }" @click="onCollect">
          <svg viewBox="0 0 24 24" :fill="collected ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 2l3 6 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1z" />
          </svg>
          {{ collected ? '已收藏' : '收藏' }}
        </button>
      </div>
      <button class="primary-btn" @click="backToInteractive">返回剧本列表</button>
    </div>

    <!-- 轻提示 -->
    <transition name="fade">
      <div v-if="toastText" class="script-play__toast">{{ toastText }}</div>
    </transition>
    </template>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.script-play {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  overflow: hidden;
  // 剧场氛围背景
  background:
    radial-gradient(120% 100% at 50% 0%, #2b0d14 0%, #150a0e 55%, #0c0608 100%);

  &__toast {
    position: fixed;
    left: 50%;
    top: 50%;
    transform: translate(-50%, -50%);
    padding: 10px 22px;
    border-radius: @radius-base;
    background: rgba(0, 0, 0, 0.8);
    color: #fff;
    font-size: @font-size-base;
    white-space: nowrap;
    z-index: 100;
  }

  &__loading {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 60vh;
    color: rgba(255, 255, 255, 0.6);
    font-size: @font-size-base;
  }
}

// ===== 顶部栏 =====
.topbar {
  position: sticky;
  top: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  z-index: 20;

  &__pill {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    max-width: 70%;
    padding: 6px 14px;
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.12);
    backdrop-filter: blur(8px);
    color: #fff;
    font-size: @font-size-sm;
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;

    .dot {
      flex-shrink: 0;
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: @color-primary;
    }
  }
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  border-radius: @radius-base;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(8px);
  color: #fff;
  cursor: pointer;

  svg {
    width: 18px;
    height: 18px;
  }

  &--round {
    border-radius: 50%;
    font-size: 16px;
    font-weight: 700;
  }
}

// ===== 总进度 =====
.progress-track {
  height: 4px;
  margin: 0 @spacing-md;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.12);
  overflow: hidden;

  i {
    display: block;
    height: 100%;
    border-radius: 2px;
    background: linear-gradient(90deg, @color-primary 0%, @color-primary-hover 100%);
    transition: width 0.5s ease;
  }
}

// ===== 阶段容器 =====
.stage {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: @spacing-lg @spacing-md calc(32px + env(safe-area-inset-bottom));

  &__headline {
    margin: @spacing-md 0 @spacing-xs;
    font-size: 22px;
    font-weight: 700;
    color: #fff;
  }

  &__sub {
    margin: 0 0 @spacing-lg;
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.55);
  }
}

.primary-btn {
  width: 100%;
  margin-top: @spacing-lg;
  padding: 12px 0;
  border: none;
  border-radius: @radius-base;
  background: linear-gradient(135deg, @color-primary 0%, @color-primary-active 100%);
  color: #fff;
  font-size: @font-size-base;
  font-weight: 700;
  letter-spacing: 2px;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(196, 30, 58, 0.4);

  &:disabled {
    opacity: 0.45;
    box-shadow: none;
  }

  &:active:not(:disabled) {
    transform: scale(0.98);
  }
}

// ===== 角色选择 =====
.role-list {
  display: flex;
  flex-direction: column;
  gap: @spacing-md;
  width: 100%;
}

.role-card {
  position: relative;
  padding: @spacing-md;
  border-radius: @radius-lg;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.15);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;

  &.is-picked {
    background: rgba(196, 30, 58, 0.12);
  }

  &:active {
    transform: scale(0.99);
  }

  &__avatar {
    position: absolute;
    top: @spacing-md;
    left: @spacing-md;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    border-radius: 50%;

    svg {
      width: 24px;
      height: 24px;
    }
  }

  &__name {
    margin-left: 58px;
    font-size: @font-size-lg;
    font-weight: 700;
    color: #fff;
  }

  &__title {
    margin: 2px 0 @spacing-sm 58px;
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.6);
  }

  &__desc {
    font-size: @font-size-sm;
    line-height: 1.6;
    color: rgba(255, 255, 255, 0.75);
  }

  &__check {
    position: absolute;
    top: @spacing-md;
    right: @spacing-md;
    padding: 2px 8px;
    border-radius: 10px;
    background: @color-primary;
    color: #fff;
    font-size: @font-size-sm;
  }
}

// ===== 剧情对话卡 =====
.dialog-card {
  width: 100%;
  margin-top: 12vh;
  padding: @spacing-md;
  border-radius: 16px;
  background: @color-bg-card;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.45);

  &__head {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    margin-bottom: @spacing-sm;
  }

  &__avatar {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: @color-primary;

    svg {
      width: 18px;
      height: 18px;
    }
  }

  &__speaker {
    font-size: @font-size-base;
    font-weight: 700;
    color: @color-primary;
  }

  &__text {
    margin: 0;
    min-height: 96px;
    font-size: 15px;
    line-height: 1.8;
    color: @color-text-primary;

    // 打字过程中整块文本可点击快进
    &.is-skippable {
      cursor: pointer;
    }

    .cursor {
      display: inline-block;
      width: 2px;
      height: 14px;
      margin-left: 2px;
      background: @color-primary;
      vertical-align: -2px;
      animation: blink 0.8s step-end infinite;
    }
  }

  &__skip {
    display: block;
    margin: @spacing-xs 0 0;
    padding: 0;
    border: none;
    background: transparent;
    text-align: right;
    font-size: @font-size-sm;
    color: @color-primary;
    cursor: pointer;
  }
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

// ===== 任务循环 =====
.task-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: @spacing-sm;

  &__label {
    font-size: @font-size-lg;
    font-weight: 700;
    color: #fff;
  }

  &__score {
    padding: 3px 10px;
    border-radius: 12px;
    background: rgba(212, 175, 55, 0.18);
    color: @color-accent;
    font-size: @font-size-sm;
    font-weight: 700;
  }

  &__hint {
    width: 100%;
    margin: @spacing-xs 0 0;
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.55);
  }
}

.task-body {
  width: 100%;
  margin-top: @spacing-lg;

  &--action {
    display: flex;
    justify-content: center;
    padding-top: 6vh;
  }
}

// 动作任务：摄像头预览
.pose-cam {
  position: relative;
  width: 60vw;
  max-width: 240px;
  aspect-ratio: 3 / 4;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: @radius-lg;
  background: #000;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.45);

  &__video {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
    // 前置摄像头镜像，符合照镜子的直觉
    transform: scaleX(-1);
  }

  &__skeleton {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
  }

  &__badge {
    position: absolute;
    top: @spacing-sm;
    left: @spacing-sm;
    padding: 2px 10px;
    border-radius: 999px;
    background: rgba(196, 30, 58, 0.85);
    color: #fff;
    font-size: @font-size-sm;
  }
}

.pose-cam__error {
  font-size: @font-size-sm;
  color: @color-danger;
}

// 动作要领引导
.pose-guide {
  width: 100%;
  max-width: 300px;
  padding: @spacing-sm @spacing-md;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: @radius-base;
  background: rgba(255, 255, 255, 0.07);

  &.is-fail {
    border-color: fade(@color-danger, 70%);
    background: fade(@color-danger, 12%);
  }

  &__title {
    margin: 0 0 @spacing-xs;
    font-size: @font-size-sm;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.92);
  }

  &__list {
    margin: 0;
    padding-left: 1.1em;
    font-size: @font-size-sm;
    line-height: 1.7;
    color: rgba(255, 255, 255, 0.72);
  }

  &__extra {
    margin: @spacing-xs 0 0;
    font-size: @font-size-sm;
    line-height: 1.6;
    color: @color-warning;
  }
}

// 动作任务：手势按钮
.gesture-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: @spacing-md;

  &__tip {
    font-size: @font-size-base;
    color: rgba(255, 255, 255, 0.75);
  }
}

.gesture-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  padding: 0;
  border: 4px solid rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  background: linear-gradient(135deg, @color-primary 0%, @color-primary-active 100%);
  box-shadow: 0 6px 24px rgba(196, 30, 58, 0.55);
  cursor: pointer;

  svg {
    width: 36px;
    height: 36px;
  }

  &:active {
    transform: scale(0.92);
  }

  &.is-busy {
    animation: pulse 1s ease-in-out infinite;
  }

  &.is-done {
    background: linear-gradient(135deg, @color-success 0%, darken(@color-success, 8%) 100%);
    box-shadow: 0 6px 24px rgba(82, 196, 26, 0.5);
  }
}

@keyframes pulse {
  50% {
    transform: scale(1.06);
  }
}

// 问答任务
.quiz-card {
  padding: @spacing-md;
  border-radius: @radius-lg;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.15);

  &.is-shake {
    animation: shake 0.4s;
  }

  &__q {
    margin: 0 0 @spacing-md;
    font-size: @font-size-lg;
    font-weight: 600;
    color: #fff;
  }

  &__opt {
    display: block;
    width: 100%;
    margin-bottom: @spacing-sm;
    padding: 11px @spacing-md;
    border: 1px solid rgba(255, 255, 255, 0.2);
    border-radius: @radius-base;
    background: rgba(255, 255, 255, 0.06);
    color: rgba(255, 255, 255, 0.9);
    font-size: @font-size-base;
    text-align: left;
    cursor: pointer;

    &:active {
      background: rgba(255, 255, 255, 0.12);
    }

    &.is-wrong {
      border-color: @color-danger;
      background: rgba(255, 77, 79, 0.15);
      color: @color-danger;
    }

    &.is-right {
      border-color: @color-success;
      background: rgba(82, 196, 26, 0.15);
      color: @color-success;
    }
  }

  &__guide {
    padding: @spacing-sm @spacing-md;
    border-radius: @radius-base;
    background: rgba(250, 173, 20, 0.12);
    border: 1px dashed rgba(250, 173, 20, 0.5);
    font-size: @font-size-sm;
    line-height: 1.6;
    color: @color-warning;
  }
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-6px); }
  75% { transform: translateX(6px); }
}

// 打卡任务
.task-body--checkin {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: @spacing-md;
  padding-top: 6vh;
}

.checkin-spot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: @spacing-sm;
  padding: @spacing-lg @spacing-xl;
  border-radius: @radius-lg;
  background: rgba(255, 255, 255, 0.07);
  border: 1px dashed rgba(255, 255, 255, 0.3);

  &__pin {
    font-size: 40px;
  }

  &__name {
    font-size: @font-size-lg;
    font-weight: 700;
    color: #fff;
  }
}

// 积分飘字
.fly-points {
  position: fixed;
  left: 50%;
  top: 38%;
  transform: translateX(-50%);
  font-size: 22px;
  font-weight: 700;
  color: @color-accent;
  text-shadow: 0 2px 10px rgba(212, 175, 55, 0.6);
  z-index: 60;
}

.fly-leave-active {
  transition: opacity 0.4s, transform 0.4s;
}
.fly-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-30px);
}

// ===== 通关奖励 =====
.stage--reward {
  position: relative;
  justify-content: center;
  overflow: hidden;
}

// ===== 撒花彩带 =====
.confetti {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;

  &__piece {
    position: absolute;
    top: -14px;
    width: 8px;
    height: 14px;
    border-radius: 2px;
    opacity: 0.95;
    animation: confetti-fall 3s ease-in forwards;
  }
}

@keyframes confetti-fall {
  0% {
    transform: translateY(-10vh) rotate(0deg);
    opacity: 1;
  }
  100% {
    transform: translateY(110vh) rotate(720deg);
    opacity: 0.5;
  }
}

// ===== 奖励元素错峰入场 =====
@keyframes reward-caption-in {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

// 徽章飞入：下方升起 + 翻转回弹落定
@keyframes badge-fly-in {
  0% {
    opacity: 0;
    transform: translateY(60px) rotateY(180deg) scale(0.4);
  }
  55% {
    opacity: 1;
    transform: translateY(-8px) rotateY(-20deg) scale(1.12);
  }
  100% {
    opacity: 1;
    transform: translateY(0) rotateY(0) scale(1);
  }
}

@keyframes reward-pop-in {
  0% {
    opacity: 0;
    transform: scale(0.6);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

.reward {
  &__caption {
    margin: 0 0 @spacing-lg;
    font-size: @font-size-lg;
    color: rgba(255, 255, 255, 0.8);
    animation: reward-caption-in 0.45s ease-out both;
  }

  &__badge {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: @spacing-sm;
  }

  &__badge-circle {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 96px;
    height: 96px;
    border-radius: 50%;
    background: linear-gradient(135deg, @color-accent 0%, darken(@color-accent, 15%) 100%);
    box-shadow: 0 0 0 8px rgba(212, 175, 55, 0.15), 0 8px 30px rgba(212, 175, 55, 0.45);
    // 先播飞入入场（transform），落定后接呼吸光环（box-shadow），互不冲突
    animation:
      badge-fly-in 0.75s cubic-bezier(0.3, 1.1, 0.35, 1) 0.15s both,
      glow 1.6s ease-in-out 1s infinite;

    svg {
      width: 44px;
      height: 44px;
    }
  }

  &__badge-name {
    font-size: @font-size-lg;
    font-weight: 700;
    color: @color-accent;
    animation: reward-pop-in 0.4s cubic-bezier(0.22, 1.2, 0.36, 1) 0.6s both;
  }

  &__score {
    display: flex;
    align-items: baseline;
    gap: @spacing-sm;
    margin: @spacing-lg 0;
    color: rgba(255, 255, 255, 0.6);
    font-size: @font-size-base;
    animation: reward-caption-in 0.4s ease-out 0.7s both;

    strong {
      font-size: 28px;
      color: #fff;
    }
  }

  &__actions {
    display: flex;
    gap: @spacing-md;
    width: 100%;
    animation: reward-caption-in 0.4s ease-out 0.85s both;
  }
}

@keyframes glow {
  50% {
    box-shadow: 0 0 0 14px rgba(212, 175, 55, 0.08), 0 8px 40px rgba(212, 175, 55, 0.6);
  }
}

// 奖励页主按钮最后入场
.stage--reward .primary-btn {
  animation: reward-caption-in 0.4s ease-out 1s both;
}

.reward-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 11px 0;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: @radius-base;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  font-size: @font-size-base;
  cursor: pointer;

  svg {
    width: 16px;
    height: 16px;
  }

  &.is-on {
    border-color: @color-accent;
    color: @color-accent;
  }

  &:active {
    transform: scale(0.98);
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
