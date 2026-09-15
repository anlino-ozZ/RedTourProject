<script setup lang="ts">
/**
 * AI 智慧讲解员（智能问答）
 * 对话式问答：AI 头像气泡 + 用户气泡 + 来源引用
 * 能力：打字机输出、推荐问题、本地历史会话；数据暂为 Mock
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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

const router = useRouter()
const route = useRoute()

const messages = ref<QaMessage[]>([
  { role: 'ai', content: WELCOME_MESSAGE, time: Date.now() },
])
const inputText = ref('')
const typing = ref(false)
const toastText = ref('')
const sessionId = ref('')

let typeTimer: ReturnType<typeof setInterval> | null = null
let thinkTimer: ReturnType<typeof setTimeout> | null = null
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
const canSend = computed(() => inputText.value.trim().length > 0 && !typing.value)

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

/** 提问：推入用户消息 → AI 打字机回答 */
function ask(raw?: string) {
  const text = (raw ?? inputText.value).trim()
  if (!text || typing.value) return

  if (!sessionId.value) sessionId.value = createSessionId()
  const isFirstQuestion = !messages.value.some((m) => m.role === 'user')

  messages.value.push({ role: 'user', content: text, time: Date.now() })
  inputText.value = ''
  scrollToBottom()

  // AI 占位消息：先"思考"再逐字输出
  const aiMsg: QaMessage = { role: 'ai', content: '', time: Date.now() }
  messages.value.push(aiMsg)
  typing.value = true
  scrollToBottom()

  const answer = getAnswer(text)

  thinkTimer = setTimeout(() => {
    let i = 0
    typeTimer = setInterval(() => {
      // 每次追加 2 个字，长回答也能较快播完
      i = Math.min(i + 2, answer.content.length)
      aiMsg.content = answer.content.slice(0, i)
      scrollToBottom()
      if (i >= answer.content.length) {
        if (typeTimer) clearInterval(typeTimer)
        typeTimer = null
        aiMsg.source = answer.source?.title
        typing.value = false
        persist(isFirstQuestion ? text : undefined)
        scrollToBottom()
      }
    }, 30)
  }, 500)
}

function onMic() {
  showToast('语音提问即将上线，敬请期待')
}

function onMainButton() {
  if (canSend.value) {
    ask()
  } else if (!typing.value) {
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
    ask(q.trim())
  }

  window.visualViewport?.addEventListener('resize', onViewportChange)
  window.visualViewport?.addEventListener('scroll', onViewportChange)
})

onUnmounted(() => {
  if (typeTimer) clearInterval(typeTimer)
  if (thinkTimer) clearTimeout(thinkTimer)
  if (toastTimer) clearTimeout(toastTimer)
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
            v-for="q in RECOMMEND_QUESTIONS"
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
      <button class="qa-input__mic" aria-label="语音输入" @click="onMic">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
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
        placeholder="输入您想问的问题..."
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
</style>
