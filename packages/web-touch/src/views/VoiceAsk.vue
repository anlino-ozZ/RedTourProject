<script setup lang="ts">
// 语音提问页（W-T-03 MVP 文字版 + W-T-04 TTS 播报 + W-T-08 STT 语音输入）
// 文本/语音输入 → /ask → 大字号答案并朗读
// 按住麦克风说话，停顿 1.5s 自动停止 → A-11 /voice/stt 转写 → 文字自动发送 ask
// 播报优先播放后端 A-10 返回的 audioUrl（mp3），否则用浏览器原生 SpeechSynthesis 兜底
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { askQuestion, getRecommendations, speechToText } from '@/api'
import { useVoiceRecorder, type VoiceRecordResult } from '@/composables/useVoiceRecorder'

const router = useRouter()
function goHome() {
  router.push('/')
}

// ===== 对话数据 =====
interface ChatEntry {
  question: string
  answer: string
  sources: string[]
  audioUrl?: string // A-10 TTS 音频地址（可能为空 → 走语音合成兜底）
  pending: boolean
  failed: boolean
}

const messages = ref<ChatEntry[]>([])
const inputText = ref('')
const sending = ref(false)
const listRef = ref<HTMLElement | null>(null)

const scenicId = Number(import.meta.env.VITE_TOUCH_SCENIC_AREA_ID) || 1

// ===== 推荐问题（A-04 接口，失败时静态兜底） =====
const FALLBACK_RECOMMENDATIONS = [
  '这里出土过什么文物？',
  '告诉我当时那个英雄的故事',
  '如何去往最近的打卡点？',
  '讲解一下这个建筑的风格',
]
const recommendations = ref<string[]>([])

async function loadRecommendations() {
  try {
    const list = await getRecommendations(scenicId)
    const qs = (list ?? []).map((r) => r.question).filter(Boolean)
    recommendations.value = qs.length ? qs.slice(0, 6) : FALLBACK_RECOMMENDATIONS
  } catch {
    recommendations.value = FALLBACK_RECOMMENDATIONS
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// ===== 提问 =====
async function send(preset?: string) {
  const question = (preset ?? inputText.value).trim()
  if (!question || sending.value) return
  inputText.value = ''
  stopSpeech() // 新提问时停止上一条播报
  sending.value = true

  const entry: ChatEntry = { question, answer: '', sources: [], pending: true, failed: false }
  messages.value.push(entry)
  scrollToBottom()

  try {
    // useVoice:true 让 AI 引擎生成 TTS 音频（A-10），音频就绪后可播 mp3
    const res = await askQuestion({ question, scenicAreaId: scenicId, useVoice: true })
    entry.answer = res?.answer || '抱歉，我没有理解您的问题，请换个问法试试。'
    entry.sources = res?.sources ?? []
    entry.audioUrl = res?.audioUrl ?? undefined
    // 答案到达即自动播报（audioUrl 优先，SpeechSynthesis 兜底）
    speak(entry, messages.value.indexOf(entry))
  } catch {
    entry.failed = true
    entry.answer = '网络开小差了，请稍后再试一次。'
  } finally {
    entry.pending = false
    sending.value = false
    scrollToBottom()
  }
}

// ===== 语音输入 STT（W-T-08） =====
const transcribing = ref(false)
const voiceHint = ref('') // 录音/识别过程中的提示或错误文案（非阻断）
let hintTimer: ReturnType<typeof setTimeout> | null = null

function showVoiceHint(text: string, autoClear = 3500) {
  voiceHint.value = text
  if (hintTimer) clearTimeout(hintTimer)
  if (autoClear) hintTimer = setTimeout(() => (voiceHint.value = ''), autoClear)
}

function extFromMime(mime: string): string {
  if (mime.includes('mp4')) return '.m4a'
  if (mime.includes('ogg')) return '.ogg'
  if (mime.includes('webm')) return '.webm'
  return '.webm'
}

const recorder = useVoiceRecorder({
  onStop: (result: VoiceRecordResult) => {
    void handleRecordResult(result)
  },
  onError: (_code, message) => {
    showVoiceHint(message, 5000)
  },
})

const isRecording = computed(() => recorder.state.value === 'recording')
const isRequestingMic = computed(() => recorder.state.value === 'requesting')
const voiceBusy = computed(() => isRecording.value || isRequestingMic.value || transcribing.value)
const recordSeconds = computed(() => Math.floor(recorder.elapsedMs.value / 1000))

// 按住说话：pointerdown 启动（捕获指针保证移出按钮也能收到 pointerup）
async function onVoiceDown(e: PointerEvent) {
  if (sending.value || transcribing.value || isRecording.value || isRequestingMic.value) return
  e.preventDefault()
  ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
  voiceHint.value = ''
  stopSpeech() // 录音前停止 TTS，避免播报声被录入
  await recorder.start()
}

// 松手立即结束（静音自动结束走 composable 的 onStop 回调，同一入口不重复）
function onVoiceUp() {
  if (isRecording.value) void recorder.stop()
}

async function handleRecordResult(result: VoiceRecordResult) {
  // 录得太短 / 没检测到人声 / 空音频：不调用接口，直接提示重试
  if (
    result.blob.size === 0 ||
    result.durationMs < 500 ||
    !result.speechDetected
  ) {
    showVoiceHint('没有听到声音，请按住麦克风按钮重新说一次')
    return
  }

  transcribing.value = true
  try {
    const res = await speechToText(result.blob, `audio${extFromMime(result.mimeType)}`)
    const text = (res?.text ?? '').trim()
    if (!text) {
      showVoiceHint('没有听清，请靠近麦克风再说一次')
      return
    }
    // 文字填入输入框并自动发送（与键盘输入走同一条 send 链路）
    inputText.value = text
    await send()
  } catch {
    // request 拦截器已弹错误提示，这里补一条就地引导
    showVoiceHint('语音识别失败，请稍后重试或改用文字输入', 5000)
  } finally {
    transcribing.value = false
  }
}

// ===== 语音播报（W-T-04） =====
// 每个条目独立的播放状态：idle / loading / playing / paused
const speakingId = ref<number>(-1)
const audioMode = ref<'audio' | 'speech'>('speech') // 实际使用的播报方式
const audioEl = ref<HTMLAudioElement | null>(null)

function isSpeaking(entry: ChatEntry, i: number) {
  return speakingId.value === i
}
const pausedSpeech = ref(false)

// 停止语音合成与残余 mp3
function stopSpeech() {
  speakingId.value = -1
  if (speechSynthesis) speechSynthesis.cancel()
  pausedSpeech.value = false
  if (audioEl.value) {
    audioEl.value.pause()
    audioEl.value.currentTime = 0
  }
}

function speak(entry: ChatEntry, index: number) {
  const text = entry.answer
  if (!text) return

  // 已播到该条且是 speech 模式 → 直接 toggle
  if (speakingId.value === index && audioMode.value === 'speech' && !entry.audioUrl) {
    if (speechSynthesis.speaking || pausedSpeech.value) {
      speechSynthesis.cancel()
      pausedSpeech.value = false
      speakingId.value = -1
    }
    return
  }

  // 播放其它条目或重新播：先停掉当前
  stopSpeech()
  speakingId.value = index

  // 1. 优先播后端 mp3（A-10）
  if (entry.audioUrl) {
    audioMode.value = 'audio'
    const audio = audioEl.value ?? (audioEl.value = new Audio())
    audio.src = entry.audioUrl
    audio.play().catch(() => {
      // mp3 播放失败 → 兜底语音合成
      speechSynthesisSpeak(text, index)
    })
    audio.onended = () => {
      speakingId.value = -1
    }
    return
  }

  // 2. 浏览器原生 SpeechSynthesis 兜底
  speechSynthesisSpeak(text, index)
}

function speechSynthesisSpeak(text: string, index: number) {
  if (!('speechSynthesis' in window)) return
  audioMode.value = 'speech'
  const utter = new SpeechSynthesisUtterance(text)
  utter.lang = 'zh-CN'
  utter.rate = 0.95

  // 尽量选中文音色
  const zhVoice = speechSynthesis.getVoices().find((v) => /zh|chinese|c mandarin/i.test(v.lang + v.name))
  if (zhVoice) utter.voice = zhVoice

  utter.onend = () => {
    speakingId.value = -1
    pausedSpeech.value = false
  }
  utter.onerror = () => {
    speakingId.value = -1
    pausedSpeech.value = false
  }
  speechSynthesis.cancel()
  speechSynthesis.speak(utter)
}

// 供模板显示的按钮文案：未播 → 播放；播报中 → 停止
function speechLabel(entry: ChatEntry, i: number) {
  return isSpeaking(entry, i) ? '停止' : '播放'
}

onBeforeUnmount(() => {
  stopSpeech()
  if (hintTimer) clearTimeout(hintTimer)
})

onMounted(() => {
  loadRecommendations()
  // 预热浏览器语音列表（Chrome 异步加载，确保播报时能选到中文音色）
  if ('speechSynthesis' in window) {
    speechSynthesis.getVoices()
    speechSynthesis.onvoiceschanged = () => speechSynthesis.getVoices()
  }
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

    <div class="voice-ask">
      <!-- 对话区 -->
      <div ref="listRef" class="chat-list">
        <div v-if="!messages.length" class="chat-empty">
          <h1 class="chat-empty__title">有什么想了解的？</h1>
          <p class="chat-empty__desc">输入您感兴趣的历史细节，AI 导览助手为您解答</p>
        </div>

        <div v-for="(msg, i) in messages" :key="i" class="chat-item">
          <!-- 游客提问 -->
          <div class="chat-question">
            <span class="chat-question__icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="8" r="4" />
                <path d="M4 21c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5" />
              </svg>
            </span>
            <span class="chat-question__text">{{ msg.question }}</span>
          </div>

          <!-- AI 回答 -->
          <div class="chat-answer">
            <span class="chat-answer__icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="5" y="7" width="14" height="11" rx="3" />
                <path d="M12 7V4" />
                <circle cx="12" cy="3" r="1" />
                <path d="M9 12h.01M15 12h.01" />
                <path d="M9.5 15.5h5" />
              </svg>
            </span>
            <div class="chat-answer__body">
              <template v-if="msg.pending">
                <span class="thinking">
                  正在思考
                  <i class="dot">·</i><i class="dot">·</i><i class="dot">·</i>
                </span>
              </template>
              <template v-else>
                <p class="chat-answer__text" :class="{ 'chat-answer__text--failed': msg.failed }">
                  {{ msg.answer }}
                </p>
                <div v-if="msg.sources.length" class="chat-answer__sources">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M4 19V5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z" />
                    <path d="M4 19a2 2 0 0 0 2 2h13" />
                  </svg>
                  来源：{{ msg.sources.join('；') }}
                </div>
              </template>
            </div>
            <!-- 播报控制（W-T-04）：播放/停止 -->
            <button
              v-if="!msg.pending && !msg.failed"
              class="chat-answer__speak"
              :class="{ 'chat-answer__speak--on': isSpeaking(msg, i) }"
              type="button"
              @click="speak(msg, i)"
            >
              <svg v-if="isSpeaking(msg, i)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="7" y="7" width="10" height="10" rx="2" />
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M11 5 6 9H3v6h3l5 4V5z" />
                <path d="M15.5 8.5a5 5 0 0 1 0 7" />
                <path d="M18.5 6a9 9 0 0 1 0 12" />
              </svg>
              {{ speechLabel(msg, i) }}
            </button>
          </div>
        </div>
      </div>

      <!-- 语音状态条（W-T-08）-->
      <div v-if="voiceBusy || voiceHint" class="voice-status" :class="{ 'voice-status--hint': !voiceBusy && voiceHint }">
        <template v-if="isRequestingMic">
          <span class="voice-status__spinner" />
          <span class="voice-status__text">正在开启麦克风…</span>
        </template>
        <template v-else-if="isRecording">
          <span class="voice-status__wave">
            <i
              v-for="n in 7"
              :key="n"
              :style="{
                height: `${Math.round(18 + recorder.volume.value * 100 * (0.55 + 0.45 * Math.abs(Math.sin(n))))}%`,
              }"
            />
          </span>
          <span class="voice-status__text">
            {{ recorder.speechDetected.value ? '正在录音 · 松开发送，停顿 1.5 秒自动发送' : '正在聆听，请开始说话…' }}
            <b class="voice-status__time">{{ recordSeconds }}s</b>
          </span>
        </template>
        <template v-else-if="transcribing">
          <span class="voice-status__spinner" />
          <span class="voice-status__text">正在识别语音…</span>
        </template>
        <template v-else>
          <span class="voice-status__dot" />
          <span class="voice-status__text">{{ voiceHint }}</span>
        </template>
      </div>

      <!-- 输入区 -->
      <div class="ask-bar">
        <!-- 按住说话（W-T-08）-->
        <button
          v-if="recorder.supported"
          class="ask-bar__mic"
          :class="{
            'ask-bar__mic--on': isRecording,
            'ask-bar__mic--waiting': isRequestingMic,
            'ask-bar__mic--busy': transcribing,
          }"
          type="button"
          :disabled="sending || transcribing || isRequestingMic"
          aria-label="按住说话"
          @pointerdown="onVoiceDown"
          @pointerup="onVoiceUp"
          @pointercancel="onVoiceUp"
          @contextmenu.prevent
        >
          <svg v-if="!transcribing" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="9" y="2.5" width="6" height="11" rx="3" />
            <path d="M5 11a7 7 0 0 0 14 0" />
            <path d="M12 18v3" />
          </svg>
          <svg v-else class="ask-bar__mic-loading" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round">
            <path d="M21 12a9 9 0 1 1-6.2-8.56" />
          </svg>
          <span v-if="isRecording" class="ask-bar__mic-ring" />
        </button>

        <input
          v-model="inputText"
          class="ask-bar__input"
          type="text"
          :placeholder="isRecording ? '正在聆听您的问题…' : '请输入您想了解的问题，或按住左侧麦克风说话…'"
          :disabled="isRecording || isRequestingMic"
          @keyup.enter="send()"
        />
        <button
          class="ask-bar__send"
          type="button"
          :disabled="sending || voiceBusy || !inputText.trim()"
          @click="send()"
        >
          发送
        </button>
      </div>

      <!-- 推荐问题 -->
      <div class="recommend">
        <button
          v-for="q in recommendations"
          :key="q"
          class="recommend__chip"
          type="button"
          :disabled="sending || voiceBusy"
          @click="send(q)"
        >
          {{ q }}
        </button>
      </div>
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

.voice-ask {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 8px 40px 32px;
}

// ===== 对话列表 =====
.chat-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 8px 24px;
}

.chat-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;

  &__title {
    margin: 0;
    font-size: 3rem;
    font-weight: 700;
  }
  &__desc {
    margin: 20px 0 0;
    font-size: 1.6rem;
    color: @color-touch-text-secondary;
  }
}

.chat-item {
  margin-bottom: 36px;
}

// 游客提问行
.chat-question {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;

  &__icon {
    flex-shrink: 0;
    width: 52px;
    height: 52px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    background: linear-gradient(160deg, #d3222f 0%, @color-primary 60%, #9e1530 100%);

    svg {
      width: 28px;
      height: 28px;
    }
  }
  &__text {
    font-size: 1.9rem;
    font-weight: 700;
  }
}

// AI 回答卡片
.chat-answer {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 32px 36px;
  border-radius: @radius-lg;
  border: 1px solid @color-touch-border;
  background-color: @color-touch-panel;

  &__icon {
    flex-shrink: 0;
    width: 52px;
    height: 52px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: @color-touch-gold;
    border: 2px solid @color-touch-gold;
    background-color: rgba(245, 197, 66, 0.08);

    svg {
      width: 28px;
      height: 28px;
    }
  }
  &__body {
    flex: 1;
    min-width: 0;
  }
  // 大字号答案
  &__text {
    margin: 0;
    font-size: 1.8rem;
    line-height: 1.95;
    color: #e6e6e6;
    white-space: pre-wrap;

    &--failed {
      color: #ff8080;
    }
  }
  &__sources {
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px solid @color-touch-border;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 1.3rem;
    color: @color-touch-text-secondary;
  }
  // 播报按钮（W-T-04）
  &__speak {
    flex-shrink: 0;
    min-width: 120px;
    min-height: 64px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding: 0 24px;
    border-radius: 16px;
    border: 1px solid @color-touch-border;
    background-color: @color-touch-panel-light;
    color: @color-touch-text;
    font-size: 1.6rem;
    cursor: pointer;
    transition: all 0.2s ease;

    svg {
      width: 24px;
      height: 24px;
    }

    &:hover {
      border-color: @color-touch-gold;
      color: @color-touch-gold;
    }
    &--on {
      border-color: @color-primary;
      background: linear-gradient(160deg, #d3222f 0%, @color-primary 55%, #9e1530 100%);
      color: #fff;
      box-shadow: 0 0 20px rgba(196, 30, 58, 0.4);
    }
  }
}

// 思考中动画
.thinking {
  font-size: 1.7rem;
  color: @color-touch-text-secondary;

  .dot {
    font-style: normal;
    margin-left: 4px;
    animation: dot-blink 1.2s infinite;

    &:nth-child(2) {
      animation-delay: 0.2s;
    }
    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}
@keyframes dot-blink {
  0%,
  60%,
  100% {
    opacity: 0.2;
  }
  30% {
    opacity: 1;
  }
}

// ===== 语音状态条（W-T-08） =====
.voice-status {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin: 0 8px 4px;
  padding: 16px 28px;
  border-radius: 16px;
  border: 1px solid rgba(245, 197, 66, 0.45);
  background-color: rgba(245, 197, 66, 0.08);
  color: @color-touch-gold;
  font-size: 1.6rem;
  letter-spacing: 1px;

  &__text {
    display: inline-flex;
    align-items: center;
    gap: 14px;
  }
  &__time {
    font-variant-numeric: tabular-nums;
    font-size: 1.5rem;
    color: #fff;
  }
  &__dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background-color: #ffb84d;
  }
  &__spinner {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    border: 3px solid rgba(245, 197, 66, 0.25);
    border-top-color: @color-touch-gold;
    animation: stt-spin 0.9s linear infinite;
  }
  &__wave {
    display: flex;
    align-items: center;
    gap: 5px;
    height: 32px;

    i {
      width: 6px;
      min-height: 6px;
      border-radius: 3px;
      background: linear-gradient(180deg, #f5d572 0%, @color-primary 100%);
      transition: height 0.08s linear;
    }
  }
  // 纯提示/错误态（非录音中）
  &--hint {
    border-color: rgba(255, 152, 152, 0.45);
    background-color: rgba(196, 30, 58, 0.1);
    color: #ffb3b3;
  }
}

@keyframes stt-spin {
  to { transform: rotate(360deg); }
}

@keyframes mic-pulse {
  0% {
    transform: scale(1);
    opacity: 0.8;
  }
  100% {
    transform: scale(1.28);
    opacity: 0;
  }
}

// ===== 输入区 =====
.ask-bar {
  flex-shrink: 0;
  display: flex;
  gap: 20px;
  padding: 20px 8px;

  &__input {
    flex: 1;
    min-width: 0;
    height: 84px;
    padding: 0 32px;
    border-radius: 20px;
    border: 2px solid @color-touch-border;
    background-color: @color-touch-panel;
    color: @color-touch-text;
    font-size: 1.8rem;
    outline: none;
    transition: border-color 0.2s ease;

    &::placeholder {
      color: #6b6b6b;
    }
    &:focus {
      border-color: @color-primary;
    }
    &:disabled {
      opacity: 0.7;
    }
  }
  // 按住说话麦克风（W-T-08）
  &__mic {
    position: relative;
    flex-shrink: 0;
    width: 84px;
    height: 84px;
    border-radius: 50%;
    border: 2px solid @color-touch-gold;
    background-color: @color-touch-panel-light;
    color: @color-touch-gold;
    cursor: pointer;
    user-select: none;
    touch-action: none; // 禁止长按滚动/系统手势
    display: inline-flex;
    align-items: center;
    justify-content: center;
    transition: transform 0.15s ease, box-shadow 0.2s ease, background-color 0.2s ease;

    svg {
      position: relative;
      z-index: 1;
      width: 38px;
      height: 38px;
    }

    &:hover:not(:disabled) {
      background-color: rgba(245, 197, 66, 0.12);
      box-shadow: 0 0 18px rgba(245, 197, 66, 0.35);
    }
    // 录音中：红色填充 + 扩散光环
    &--on {
      border-color: @color-primary;
      background: linear-gradient(160deg, #d3222f 0%, @color-primary 55%, #9e1530 100%);
      color: #fff;
      transform: scale(1.06);
      box-shadow: 0 0 24px rgba(196, 30, 58, 0.55);
    }
    &--waiting,
    &--busy {
      opacity: 0.75;
      cursor: wait;
    }
    &:disabled {
      cursor: not-allowed;
    }
    &-ring {
      position: absolute;
      inset: -8px;
      border-radius: 50%;
      border: 2px solid rgba(196, 30, 58, 0.5);
      animation: mic-pulse 1.4s ease-out infinite;
      pointer-events: none;
    }
    &-loading {
      animation: stt-spin 0.9s linear infinite;
    }
  }
  &__send {
    flex-shrink: 0;
    min-width: 160px;
    border: 2px solid @color-touch-gold;
    border-radius: 20px;
    background: linear-gradient(160deg, #d3222f 0%, @color-primary 55%, #9e1530 100%);
    color: #fff;
    font-size: 1.9rem;
    font-weight: 700;
    letter-spacing: 6px;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover:not(:disabled) {
      filter: brightness(1.1);
      box-shadow: 0 0 24px rgba(196, 30, 58, 0.45);
    }
    &:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
  }
}

// ===== 推荐问题 =====
.recommend {
  flex-shrink: 0;
  display: flex;
  gap: 20px;
  padding: 4px 8px 0;
  overflow-x: auto;

  &__chip {
    flex-shrink: 0;
    padding: 20px 32px;
    border-radius: 999px;
    border: 1px solid @color-touch-border;
    background-color: @color-touch-panel-light;
    color: @color-touch-text;
    font-size: 1.5rem;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover:not(:disabled) {
      border-color: @color-primary;
      color: @color-primary-hover;
    }
    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }
}
</style>
