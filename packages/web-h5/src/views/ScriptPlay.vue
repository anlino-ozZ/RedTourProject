<script setup lang="ts">
/**
 * 红色剧本：暗哨行动（沉浸式剧本互动系统）
 * 流程：选择角色 → 剧情开场 → 任务循环（动作/问答/打卡，失败重试+引导）
 *      → 全部完成 → 结局剧情 → 解锁成就徽章 + 积分 + 分享/收藏
 * 数据暂为 Mock，动作识别/定位打卡后续接入 ai-engine
 */
import { computed, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { mockScript } from '@/mock/interaction'
import type { ScriptTask } from '@/mock/interaction'

const router = useRouter()

// ===== 流程状态机 =====
type Stage = 'role' | 'intro' | 'tasks' | 'ending' | 'reward'
const stage = ref<Stage>('role')
const roleId = ref('')
const role = computed(() => mockScript.roles.find((r) => r.id === roleId.value))

const taskIndex = ref(0)
const currentTask = computed(() => mockScript.tasks[taskIndex.value] as ScriptTask)
const score = ref(0)
const overallProgress = computed(() => {
  if (stage.value === 'role' || stage.value === 'intro') return 0
  if (stage.value === 'ending' || stage.value === 'reward') return 100
  return Math.round((taskIndex.value / mockScript.tasks.length) * 100)
})

const stageLabel = computed(() => {
  switch (stage.value) {
    case 'role': return '选择角色'
    case 'intro': return '剧情开场'
    case 'tasks': return `任务 ${taskIndex.value + 1}/${mockScript.tasks.length}`
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

// ===== 打字机（开场/结局共用） =====
const displayedText = ref('')
const isTyping = ref(false)
let typeTimer: ReturnType<typeof setInterval> | undefined
function typewriter(text: string, done?: () => void) {
  displayedText.value = ''
  isTyping.value = true
  let i = 0
  if (typeTimer) clearInterval(typeTimer)
  typeTimer = setInterval(() => {
    i++
    displayedText.value = text.slice(0, i)
    if (i >= text.length) {
      clearInterval(typeTimer)
      isTyping.value = false
      done?.()
    }
  }, 40)
}

// ===== 选择角色 =====
function pickRole(id: string) {
  roleId.value = id
}

function startScript() {
  if (!roleId.value) {
    showToast('请先选择一个角色')
    return
  }
  stage.value = 'intro'
  typewriter(mockScript.intro)
}

// ===== 任务循环 =====
// 动作任务
const actionState = ref<'idle' | 'recognizing' | 'done'>('idle')
// 问答任务
const quizPicked = ref(-1)
const quizWrong = ref(false)
const quizCorrect = ref(false)
const showGuide = ref(false)
// 打卡任务
const checkinState = ref<'idle' | 'locating' | 'done'>('idle')
// 积分飘字
const flyPoints = ref('')

function flyup(points: number) {
  score.value += points
  flyPoints.value = `+${points} 积分`
  setTimeout(() => (flyPoints.value = ''), 1200)
}

/** 任务完成 → 推进剧情 + 获得积分；所有任务完成 → 结局 */
function completeTask() {
  const task = currentTask.value
  flyup(task.points)
  showToast(`任务完成，剧情推进！`)
  setTimeout(() => {
    // 重置本任务状态
    actionState.value = 'idle'
    quizPicked.value = -1
    quizWrong.value = false
    quizCorrect.value = false
    showGuide.value = false
    checkinState.value = 'idle'
    if (taskIndex.value < mockScript.tasks.length - 1) {
      taskIndex.value++
    } else {
      stage.value = 'ending'
      typewriter(mockScript.ending)
    }
  }, 1000)
}

/** 动作任务：模拟姿势识别（识别中 1.2s → 成功） */
function doAction() {
  if (actionState.value !== 'idle') return
  actionState.value = 'recognizing'
  setTimeout(() => {
    actionState.value = 'done'
    completeTask()
  }, 1200)
}

/** 问答任务：答错 → 提示重试 + 引导帮助；答对 → 过 */
function pickQuiz(i: number) {
  if (quizCorrect.value) return
  quizPicked.value = i
  const quiz = currentTask.value.quiz!
  if (i === quiz.answer) {
    quizCorrect.value = true
    completeTask()
  } else {
    quizWrong.value = true
    showGuide.value = true
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
}

function onShare() {
  showToast('分享海报已生成，快去发给小伙伴吧')
}

function onCollect() {
  collected.value = !collected.value
  showToast(collected.value ? '已收藏剧本' : '已取消收藏')
}

function backToInteractive() {
  router.push('/interactive')
}

function onClose() {
  if (window.history.length > 1) router.back()
  else router.push('/interactive')
}

function onHelp() {
  showToast('按任务提示完成动作/答题/打卡即可推进剧情')
}

onUnmounted(() => {
  if (typeTimer) clearInterval(typeTimer)
  if (toastTimer) clearTimeout(toastTimer)
})
</script>

<template>
  <div class="script-play">
    <!-- ===== 顶部操作栏 ===== -->
    <div class="topbar">
      <button class="icon-btn" aria-label="退出剧本" @click="onClose">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M18 6L6 18M6 6l12 12" />
        </svg>
      </button>
      <span class="topbar__pill">
        <i class="dot" />{{ mockScript.title }} · {{ stageLabel }}
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
          v-for="r in mockScript.roles"
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
        <p class="dialog-card__text">{{ displayedText }}<i v-if="isTyping" class="cursor" /></p>
      </div>
      <button class="primary-btn" :disabled="isTyping" @click="stage = 'tasks'">
        开始第一个任务
      </button>
    </div>

    <!-- ===== 阶段三：任务循环 ===== -->
    <div v-else-if="stage === 'tasks'" class="stage stage--tasks">
      <div class="task-head">
        <span class="task-head__label">{{ currentTask.title }}</span>
        <span class="task-head__score">积分 {{ score }}</span>
      </div>
      <p class="task-head__hint">{{ currentTask.hint }}</p>

      <!-- 动作任务 -->
      <div v-if="currentTask.type === 'action'" class="task-body task-body--action">
        <div class="gesture-wrap">
          <button
            class="gesture-btn"
            :class="{ 'is-done': actionState === 'done', 'is-busy': actionState === 'recognizing' }"
            aria-label="行军礼"
            @click="doAction"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 11V6a2 2 0 00-4 0v5M14 10V4a2 2 0 00-4 0v6M10 10.5V6a2 2 0 00-4 0v8" />
              <path d="M18 8a2 2 0 114 0v6a8 8 0 01-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 012.83-2.82L7 15" />
            </svg>
          </button>
          <span class="gesture-wrap__tip">
            {{ actionState === 'idle' && '点击按钮，面向屏幕行一个军礼'
              || actionState === 'recognizing' && '姿势识别中…'
              || '识别成功！' }}
          </span>
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
        <p class="dialog-card__text">{{ displayedText }}<i v-if="isTyping" class="cursor" /></p>
      </div>
      <button class="primary-btn" :disabled="isTyping" @click="toReward">查看通关奖励</button>
    </div>

    <!-- ===== 阶段五：通关奖励 ===== -->
    <div v-else class="stage stage--reward">
      <p class="reward__caption">恭喜通关，解锁成就徽章</p>
      <div class="reward__badge">
        <span class="reward__badge-circle">
          <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="8" r="6" />
            <path d="M15.5 13l1.5 8-5-3-5 3 1.5-8" />
          </svg>
        </span>
        <span class="reward__badge-name">{{ mockScript.reward.badge.name }}</span>
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
      <button class="primary-btn" @click="backToInteractive">返回互动中心</button>
    </div>

    <!-- 轻提示 -->
    <transition name="fade">
      <div v-if="toastText" class="script-play__toast">{{ toastText }}</div>
    </transition>
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
  justify-content: center;
}

.reward {
  &__caption {
    margin: 0 0 @spacing-lg;
    font-size: @font-size-lg;
    color: rgba(255, 255, 255, 0.8);
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
    animation: glow 1.6s ease-in-out infinite;

    svg {
      width: 44px;
      height: 44px;
    }
  }

  &__badge-name {
    font-size: @font-size-lg;
    font-weight: 700;
    color: @color-accent;
  }

  &__score {
    display: flex;
    align-items: baseline;
    gap: @spacing-sm;
    margin: @spacing-lg 0;
    color: rgba(255, 255, 255, 0.6);
    font-size: @font-size-base;

    strong {
      font-size: 28px;
      color: #fff;
    }
  }

  &__actions {
    display: flex;
    gap: @spacing-md;
    width: 100%;
  }
}

@keyframes glow {
  50% {
    box-shadow: 0 0 0 14px rgba(212, 175, 55, 0.08), 0 8px 40px rgba(212, 175, 55, 0.6);
  }
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
