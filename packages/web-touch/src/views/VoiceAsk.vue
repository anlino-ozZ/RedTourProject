<script setup lang="ts">
// 语音提问页（W-T-03 MVP 文字版 + W-T-04 TTS 播报）：文本输入 → /ask → 大字号答案并朗读
// 播报优先播放后端 A-10 返回的 audioUrl（mp3），否则用浏览器原生 SpeechSynthesis 兜底
// STT 语音输入待 A-11 就绪后接入（W-T-08）
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { askQuestion, getRecommendations } from '@/api'

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

onBeforeUnmount(stopSpeech)

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

      <!-- 输入区 -->
      <div class="ask-bar">
        <input
          v-model="inputText"
          class="ask-bar__input"
          type="text"
          placeholder="请输入您想了解的问题…"
          @keyup.enter="send()"
        />
        <button
          class="ask-bar__send"
          type="button"
          :disabled="sending || !inputText.trim()"
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
          :disabled="sending"
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
