<script setup lang="ts">
/**
 * AI 智慧讲解员（智能问答）
 * 对话式问答：AI 头像气泡 + 用户气泡 + 来源引用
 * 能力：打字机输出、推荐问题（接口获取）、语音提问（STT）、TTS 播报；接口失败降级本地知识
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  askAi,
  extractSourceTitle,
  getRecommendations,
  resolveTtsUrl,
  speechToText,
} from '@/api/qa'
import { speak, stopSpeak } from '@/utils/narrator'
import {
  RECOMMEND_QUESTIONS,
  WELCOME_MESSAGE,
  getAnswer,
} from '@/mock/qa'
import {
  createSessionId,
  getSession,
  saveSession,
  type QaMessage,
} from '@/utils/qaStorage'
import { playScoreSound, playTapSound } from '@/utils/sound'

const router = useRouter()
const route = useRoute()

const messages = ref<QaMessage[]>([
  { role: 'ai', content: WELCOME_MESSAGE, time: Date.now() },
])
const inputText = ref('')
const typing = ref(false)
const toastText = ref('')
const sessionId = ref('')

/** 推荐问题：接口获取，失败沿用本地推荐 */
const suggestQuestions = ref<string[]>(RECOMMEND_QUESTIONS)

// ===== TTS 播放 / 语音提问（STT）状态 =====
let audioEl: HTMLAudioElement | null = null
let mediaStream: MediaStream | null = null
let mediaRecorder: MediaRecorder | null = null
let recordChunks: Blob[] = []
let recordTimer: ReturnType<typeof setInterval> | null = null
/** 单段录音上限（秒），防止忘记关闭一直录 */
const MAX_RECORD_SECONDS = 60
const recording = ref(false)
const recordSeconds = ref(0)
const sttPending = ref(false)

let typeTimer: ReturnType<typeof setInterval> | null = null
let toastTimer: ReturnType<typeof setTimeout> | null = null

const bodyRef = ref<HTMLElement | null>(null)

// 软键盘高度（0 表示键盘未弹起）。overlap 模式（旧 iOS / 微信 WebView）
// 下视口不随键盘缩放，需用 VisualViewport 手动把输入栏顶到键盘上沿
const keyboardHeight = ref(0)
const pageStyle = computed(() =>
  keyboardHeight.value > 0 ? { bottom: `${keyboardHeight.value}px` } : undefined,
)

// 还没有用户提问时，展示推荐问题
const showSuggest = computed(
  () => !typing.value && !messages.value.some((m) => m.role === 'user'),
)
const canSend = computed(
  () =>
    inputText.value.trim().length > 0 &&
    !typing.value &&
    !recording.value &&
    !sttPending.value,
)

function scrollToBottom() {
  nextTick(() => {
    const el = bodyRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function showToast(text: string) {
  toastText.value = text
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toastText.value = ''), 1800)
}

/** 保存当前会话（含欢迎语与全部消息） */
function persist(firstQuestion?: string) {
  if (!sessionId.value) return
  saveSession({
    id: sessionId.value,
    title: firstQuestion ?? messages.value.find((m) => m.role === 'user')?.content ?? '新对话',
    messages: messages.value,
    updatedAt: Date.now(),
  })
}

/**
 * 提问：推入用户消息 → 调用 /ask → 打字机输出 → 播报语音
 * 接口失败时降级本地知识库（mock），保证问答始终可用
 * @param silent 路由带入问题等非用户手势触发时不播放发送音
 */
async function ask(raw?: string, silent = false) {
  const text = (raw ?? inputText.value).trim()
  if (!text || typing.value) return
  if (!silent) playTapSound()

  // 新一轮提问前停止上一轮的音频/播报
  stopPlaying()

  if (!sessionId.value) sessionId.value = createSessionId()
  const isFirstQuestion = !messages.value.some((m) => m.role === 'user')

  messages.value.push({ role: 'user', content: text, time: Date.now() })
  inputText.value = ''
  scrollToBottom()

  // AI 占位消息：请求期间显示"思考中"，返回后逐字输出
  const aiMsg: QaMessage = { role: 'ai', content: '', time: Date.now() }
  messages.value.push(aiMsg)
  typing.value = true
  scrollToBottom()

  let answerText = ''
  let audioUrl: string | null = null
  try {
    const res = await askAi({ question: text, useVoice: true })
    answerText = res.answer
    audioUrl = res.audioUrl
    aiMsg.source = extractSourceTitle(res.sources)
  } catch {
    // 后端未启动 / 接口异常：拦截器已提示错误，此处降级本地知识
    const fallback = getAnswer(text)
    answerText = fallback.content
    aiMsg.source = fallback.source?.title
  }
  if (!answerText) answerText = getAnswer(text).content

  let i = 0
  typeTimer = setInterval(() => {
    // 每次追加 2 个字，长回答也能较快播完
    i = Math.min(i + 2, answerText.length)
    aiMsg.content = answerText.slice(0, i)
    scrollToBottom()
    if (i >= answerText.length) {
      if (typeTimer) clearInterval(typeTimer)
      typeTimer = null
      typing.value = false
      persist(isFirstQuestion ? text : undefined)
      // AI 回答输出完成：柔和"叮咚"提示可读
      playScoreSound()
      scrollToBottom()
      // 语音播报：后端 TTS 音频优先，未生成时浏览器合成朗读
      if (audioUrl) {
        playTtsAudio(audioUrl, answerText)
      } else {
        speak(answerText, 'male')
      }
    }
  }, 30)
}

/** 播放后端 TTS 音频；音频不可达时退回浏览器合成朗读 */
function playTtsAudio(url: string, fallbackText: string) {
  audioEl?.pause()
  audioEl = new Audio(resolveTtsUrl(url))
  audioEl.onerror = () => speak(fallbackText, 'male')
  audioEl.play().catch(() => speak(fallbackText, 'male'))
}

/** 停止当前播报（音频 + 语音合成） */
function stopPlaying() {
  stopSpeak()
  if (audioEl) {
    audioEl.onerror = null
    audioEl.pause()
    audioEl.src = ''
    audioEl = null
  }
}

// ===== 语音提问：MediaRecorder 录音 → /stt 转文字 → 自动提问 =====

/** 点击麦克风：开始/结束录音 */
async function toggleRecord() {
  if (sttPending.value) return
  if (recording.value) {
    stopRecording()
    return
  }
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
    showToast('当前环境不支持语音输入')
    return
  }
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
  } catch {
    showToast('无法访问麦克风，请检查权限')
    return
  }
  recordChunks = []
  mediaRecorder = new MediaRecorder(mediaStream)
  mediaRecorder.ondataavailable = (e) => {
    if (e.data.size > 0) recordChunks.push(e.data)
  }
  mediaRecorder.onstop = handleRecordStop
  mediaRecorder.start()
  recording.value = true
  recordSeconds.value = 0
  recordTimer = setInterval(() => {
    recordSeconds.value++
    if (recordSeconds.value >= MAX_RECORD_SECONDS) stopRecording()
  }, 1000)
}

function stopRecording() {
  if (recordTimer) {
    clearInterval(recordTimer)
    recordTimer = null
  }
  recording.value = false
  if (mediaRecorder && mediaRecorder.state !== 'inactive') mediaRecorder.stop()
}

/** 录音停止：释放设备 → 上传识别 → 识别文本直接提问 */
async function handleRecordStop() {
  mediaStream?.getTracks().forEach((t) => t.stop())
  mediaStream = null
  mediaRecorder = null
  const blob = new Blob(recordChunks, { type: recordChunks[0]?.type || 'audio/webm' })
  recordChunks = []
  if (blob.size < 1024) {
    showToast('没有录到声音，请重试')
    return
  }
  sttPending.value = true
  try {
    const res = await speechToText(blob)
    const text = (res.text ?? '').trim()
    if (text) {
      ask(text)
    } else {
      showToast('没有听清，请再说一次')
    }
  } catch {
    showToast('语音识别失败，请重试')
  } finally {
    sttPending.value = false
  }
}

function onMainButton() {
  // 录音中点击发送键：结束录音并进入识别
  if (recording.value) {
    stopRecording()
    return
  }
  if (canSend.value) {
    ask()
  } else if (!typing.value && !sttPending.value) {
    showToast('试试下面的推荐问题吧')
  }
}

function openHistory() {
  router.push('/qa/history')
}

/** VisualViewport 变化 → 计算软键盘高度（布局视口与可视视口的差值） */
function onViewportChange() {
  const vv = window.visualViewport
  if (!vv) return
  // offsetTop：可视视口相对布局视口的下移量（部分浏览器键盘弹起会伴随滚动）
  const kb = Math.max(0, window.innerHeight - vv.height - vv.offsetTop)
  // 小于 120px 视为未弹键盘（过滤浏览器底栏等小幅变化）
  keyboardHeight.value = kb > 120 ? kb : 0
  if (keyboardHeight.value) scrollToBottom()
}

onMounted(() => {
  // 恢复历史会话
  const id = route.query.id
  if (typeof id === 'string') {
    const session = getSession(id)
    if (session) {
      sessionId.value = session.id
      messages.value = session.messages
      scrollToBottom()
    }
  }
  // 从首页"大家都在问"等入口带入问题
  const q = route.query.q
  if (typeof q === 'string' && q.trim()) {
    // 路由带入的问题非用户手势触发，不播发送音
    ask(q.trim(), true)
  }

  // 推荐问题：后端获取，失败静默沿用本地推荐
  getRecommendations()
    .then((list) => {
      if (Array.isArray(list) && list.length > 0) suggestQuestions.value = list
    })
    .catch(() => {})

  window.visualViewport?.addEventListener('resize', onViewportChange)
  window.visualViewport?.addEventListener('scroll', onViewportChange)
})

onUnmounted(() => {
  if (typeTimer) clearInterval(typeTimer)
  if (toastTimer) clearTimeout(toastTimer)
  // 停止播报与录音
  stopPlaying()
  if (mediaRecorder) mediaRecorder.onstop = null
  stopRecording()
  mediaStream?.getTracks().forEach((t) => t.stop())
  mediaStream = null
  window.visualViewport?.removeEventListener('resize', onViewportChange)
  window.visualViewport?.removeEventListener('scroll', onViewportChange)
})
</script>

<template>
  <div class="qa-page" :style="pageStyle">
    <!-- 顶部白色标题栏 -->
    <header class="qa-header">
      <h1 class="qa-header__title">AI 智慧讲解员</h1>
      <button class="qa-header__history" aria-label="历史记录" @click="openHistory">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
          stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 5h7M14 19h7" />
          <circle cx="12" cy="5" r="2" />
          <circle cx="12" cy="19" r="2" />
          <path d="M12 7v10" />
        </svg>
      </button>
    </header>

    <!-- 消息流 -->
    <div ref="bodyRef" class="qa-body">
      <div
        v-for="(m, idx) in messages"
        :key="idx"
        class="qa-msg"
        :class="m.role === 'user' ? 'qa-msg--user' : 'qa-msg--ai'"
      >
        <!-- AI 头像 -->
        <span v-if="m.role === 'ai'" class="qa-avatar qa-avatar--ai">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
            stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 3v2" />
            <circle cx="12" cy="4.5" r="1" fill="currentColor" stroke="none" />
            <rect x="5" y="7" width="14" height="12" rx="3" />
            <path d="M9 12h.01M15 12h.01" />
            <path d="M9 16h6" />
          </svg>
        </span>

        <!-- 气泡 -->
        <div class="qa-bubble">
          <template v-if="m.content">
            {{ m.content }}
            <span v-if="typing && idx === messages.length - 1" class="qa-bubble__caret" />
          </template>
          <span v-else-if="typing && idx === messages.length - 1" class="qa-dots">
            <i /><i /><i />
          </span>

          <!-- 引用来源 -->
          <div v-if="m.source" class="qa-source">
            <span class="qa-source__text">来源：{{ m.source }}</span>
            <svg class="qa-source__arrow" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round" @click="showToast('正在打开来源文献…')">
              <path d="M9 6l6 6-6 6" />
            </svg>
          </div>
        </div>

        <!-- 用户头像 -->
        <span v-if="m.role === 'user'" class="qa-avatar qa-avatar--user">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
            stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="8" r="3.5" />
            <path d="M5 20c0-3.5 3-5.5 7-5.5s7 2 7 5.5" />
          </svg>
        </span>
      </div>

      <!-- 推荐问题（首轮对话前） -->
      <div v-if="showSuggest" class="qa-suggest">
        <p class="qa-suggest__label">你可以问我</p>
        <div class="qa-suggest__list">
          <button
            v-for="q in suggestQuestions"
            :key="q"
            class="qa-suggest__item"
            @click="ask(q)"
          >
            <span class="qa-suggest__icon">?</span>
            {{ q }}
          </button>
        </div>
      </div>
    </div>

    <!-- 底部输入栏 -->
    <div class="qa-input">
      <button
        class="qa-input__mic"
        :class="{
          'is-recording': recording,
          'is-pending': sttPending,
        }"
        :aria-label="recording ? '结束录音' : '语音输入'"
        @click="toggleRecord"
      >
        <!-- 录音中显示停止方块，识别中显示旋转圈 -->
        <svg v-if="recording" viewBox="0 0 24 24" fill="none" stroke="currentColor"
          stroke-width="2" stroke-linecap="round">
          <rect x="7" y="7" width="10" height="10" rx="2" fill="currentColor" />
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
          stroke-linecap="round" stroke-linejoin="round">
          <rect x="9" y="3" width="6" height="11" rx="3" />
          <path d="M5 11a7 7 0 0014 0" />
          <path d="M12 18v3" />
        </svg>
      </button>
      <input
        v-model="inputText"
        class="qa-input__field"
        type="text"
        :disabled="recording || sttPending"
        :placeholder="
          recording
            ? `正在录音 ${recordSeconds}s，点击麦克风结束`
            : sttPending
              ? '语音识别中...'
              : '输入您想问的问题...'
        "
        @focus="scrollToBottom"
        @keyup.enter="ask()"
      />
      <button
        class="qa-input__send"
        :class="{ 'is-active': canSend }"
        :aria-label="canSend ? '发送' : '推荐问题'"
        @click="onMainButton"
      >
        <svg v-if="canSend" viewBox="0 0 24 24" fill="none" stroke="currentColor"
          stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor"
          stroke-width="2.2" stroke-linecap="round">
          <path d="M12 5v14M5 12h14" />
        </svg>
      </button>
    </div>

    <!-- 轻提示 -->
    <Transition name="qa-toast">
      <div v-if="toastText" class="qa-toast">{{ toastText }}</div>
    </Transition>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.qa-page {
  // 铺满 Layout 内容区、底部止于凸起 CTA 顶部（tabbar 56px + 凸出 18px）
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: calc(74px + env(safe-area-inset-bottom));
  z-index: 10;
  display: flex;
  flex-direction: column;
  background: @color-bg-page;
}

// ===== 顶部标题栏 =====
.qa-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  padding: 0 @spacing-md;
  background: @color-bg-card;
  border-bottom: 1px solid #f0f0f0;

  &__title {
    margin: 0;
    font-size: 19px;
    font-weight: 700;
    color: @color-text-primary;
  }
  &__history {
    width: 36px;
    height: 36px;
    border: none;
    border-radius: 50%;
    background: transparent;
    color: @color-text-regular;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;

    svg {
      width: 22px;
      height: 22px;
    }
    &:active {
      background: @color-primary-light;
      color: @color-primary;
    }
  }
}

// ===== 消息流 =====
.qa-body {
  flex: 1;
  overflow-y: auto;
  padding: @spacing-md;
  -webkit-overflow-scrolling: touch;
}

.qa-msg {
  display: flex;
  align-items: flex-start;
  gap: @spacing-sm;
  margin-bottom: @spacing-lg;

  // 用户消息 DOM 顺序为 [气泡][头像]，靠右排列即：气泡在左、头像在最右
  &--user {
    justify-content: flex-end;
  }
}

.qa-avatar {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;

  svg {
    width: 22px;
    height: 22px;
  }

  &--ai {
    background: @color-primary;
    color: #fff;
  }
  &--user {
    background: #d8d8dd;
    color: #fff;
  }
}

.qa-bubble {
  position: relative;
  max-width: 76%;
  padding: 11px 14px;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);

  .qa-msg--ai & {
    background: @color-bg-card;
    color: @color-text-primary;
    border-radius: 4px 16px 16px 16px;
  }
  .qa-msg--user & {
    background: @color-primary;
    color: #fff;
    border-radius: 16px 4px 16px 16px;
  }

  // 打字机光标
  &__caret {
    display: inline-block;
    width: 2px;
    height: 15px;
    margin-left: 2px;
    vertical-align: -2px;
    background: @color-primary;
    animation: qa-blink 0.9s steps(1) infinite;
  }
}

// "思考中"三点动画
.qa-dots {
  display: inline-flex;
  gap: 4px;
  padding: 2px 0;

  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: @color-text-secondary;
    animation: qa-bounce 1.2s infinite ease-in-out;
  }
  i:nth-child(2) {
    animation-delay: 0.15s;
  }
  i:nth-child(3) {
    animation-delay: 0.3s;
  }
}

// ===== 引用来源 =====
.qa-source {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: @spacing-sm;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px dashed #e5e5e5;

  &__text {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }
  &__arrow {
    width: 16px;
    height: 16px;
    color: @color-primary;
    cursor: pointer;
    flex-shrink: 0;
  }
}

// ===== 推荐问题 =====
.qa-suggest {
  margin-top: -@spacing-sm;

  &__label {
    margin: 0 0 @spacing-sm 2px;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }
  &__list {
    display: flex;
    flex-direction: column;
    gap: @spacing-sm;
  }
  &__item {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 12px 14px;
    text-align: left;
    font-size: 14px;
    color: @color-text-regular;
    background: @color-bg-card;
    border: 1px solid #f0f0f0;
    border-radius: @radius-lg;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
    cursor: pointer;

    &:active {
      background: @color-primary-light;
      border-color: fade(@color-primary, 30%);
    }
  }
  &__icon {
    flex-shrink: 0;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: @color-primary-light;
    color: @color-primary;
    font-size: 13px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

// ===== 底部输入栏 =====
.qa-input {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px @spacing-md calc(10px + env(safe-area-inset-bottom));
  background: @color-bg-card;
  border-top: 1px solid #f0f0f0;

  &__mic {
    flex-shrink: 0;
    width: 34px;
    height: 34px;
    border: none;
    background: transparent;
    color: @color-text-secondary;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;

    svg {
      width: 24px;
      height: 24px;
    }
    &:active {
      color: @color-primary;
    }

    // 录音中：红色脉冲提示
    &.is-recording {
      color: #e02020;
      animation: qa-pulse 1.2s infinite ease-in-out;
    }
    // 识别中：整体呼吸淡显
    &.is-pending {
      color: @color-primary;
      animation: qa-pulse 1.2s infinite ease-in-out;
      pointer-events: none;
    }
  }

  &__field {
    flex: 1;
    height: 40px;
    padding: 0 16px;
    border: none;
    border-radius: 20px;
    background: @color-bg-page;
    font-size: 15px;
    color: @color-text-primary;
    outline: none;

    &::placeholder {
      color: #b0b0b5;
    }
  }

  &__send {
    flex-shrink: 0;
    width: 38px;
    height: 38px;
    border-radius: 50%;
    border: 1.5px solid @color-primary;
    background: @color-bg-card;
    color: @color-primary;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.15s;

    svg {
      width: 20px;
      height: 20px;
    }

    &.is-active {
      background: @color-primary;
      color: #fff;
      box-shadow: 0 3px 10px rgba(196, 30, 58, 0.35);
    }
    &:active {
      transform: scale(0.92);
    }
  }
}

// ===== Toast =====
.qa-toast {
  position: absolute;
  left: 50%;
  bottom: 72px;
  transform: translateX(-50%);
  padding: 9px 18px;
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  font-size: @font-size-base;
  border-radius: 20px;
  white-space: nowrap;
  z-index: 20;
  pointer-events: none;
}
.qa-toast-enter-active,
.qa-toast-leave-active {
  transition: opacity 0.25s;
}
.qa-toast-enter-from,
.qa-toast-leave-to {
  opacity: 0;
}

@keyframes qa-blink {
  0%,
  50% {
    opacity: 1;
  }
  51%,
  100% {
    opacity: 0;
  }
}
@keyframes qa-bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}
@keyframes qa-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}
</style>
