<script setup lang="ts">
/**
 * AI 问答 —— 历史会话列表
 * 会话存储于 localStorage，支持恢复 / 单条删除 / 清空
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  clearSessions,
  deleteSession,
  listSessions,
  type QaSession,
} from '@/utils/qaStorage'

const router = useRouter()
const sessions = ref<QaSession[]>([])

function refresh() {
  sessions.value = listSessions()
}

/** 简单的相对时间格式化 */
function formatTime(ts: number): string {
  const d = new Date(ts)
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const sameDay = d.toDateString() === now.toDateString()
  if (sameDay) return `今天 ${hm}`
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (d.toDateString() === yesterday.toDateString()) return `昨天 ${hm}`
  return `${d.getMonth() + 1}月${d.getDate()}日 ${hm}`
}

function openSession(s: QaSession) {
  router.push(`/qa?id=${s.id}`)
}

function removeSession(s: QaSession, e: Event) {
  e.stopPropagation()
  if (window.confirm(`确定删除对话「${s.title}」吗？`)) {
    deleteSession(s.id)
    refresh()
  }
}

function clearAll() {
  if (sessions.value.length === 0) return
  if (window.confirm('确定清空全部历史记录吗？此操作不可恢复。')) {
    clearSessions()
    refresh()
  }
}

onMounted(refresh)
</script>

<template>
  <div class="qa-history">
    <!-- 顶部栏 -->
    <header class="qa-history__header">
      <button class="qa-history__back" aria-label="返回" @click="router.back()">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round">
          <path d="M15 6l-6 6 6 6" />
        </svg>
      </button>
      <h1 class="qa-history__title">历史记录</h1>
      <button class="qa-history__new" aria-label="新对话" @click="router.push('/qa')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
          stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15a2 2 0 01-2 2H8l-4 4V5a2 2 0 012-2h13a2 2 0 012 2z" />
          <path d="M12 8v6M9 11h6" />
        </svg>
      </button>
    </header>

    <!-- 列表 -->
    <main v-if="sessions.length" class="qa-history__body">
      <div
        v-for="s in sessions"
        :key="s.id"
        class="qa-history__item"
        @click="openSession(s)"
      >
        <div class="qa-history__item-main">
          <p class="qa-history__item-title">{{ s.title }}</p>
          <p class="qa-history__item-meta">
            {{ formatTime(s.updatedAt) }} · {{ s.messages.length }} 条对话
          </p>
        </div>
        <button
          class="qa-history__item-del"
          aria-label="删除"
          @click="removeSession(s, $event)"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
            stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 6h18M8 6V4a1 1 0 011-1h6a1 1 0 011 1v2m2 0v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6" />
          <path d="M10 11v6M14 11v6" />
          </svg>
        </button>
      </div>

      <button class="qa-history__clear" @click="clearAll">清空全部历史</button>
    </main>

    <!-- 空状态 -->
    <main v-else class="qa-history__empty">
      <span class="qa-history__empty-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
          stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15a2 2 0 01-2 2H8l-4 4V5a2 2 0 012-2h13a2 2 0 012 2z" />
        </svg>
      </span>
      <p class="qa-history__empty-text">还没有对话记录</p>
      <button class="qa-history__empty-btn" @click="router.push('/qa')">去提问</button>
    </main>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.qa-history {
  min-height: 100vh;
  background: @color-bg-page;
  display: flex;
  flex-direction: column;

  &__header {
    position: sticky;
    top: 0;
    z-index: 5;
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 52px;
    padding: 0 @spacing-sm;
    background: @color-bg-card;
    border-bottom: 1px solid #f0f0f0;
  }

  &__back,
  &__new {
    width: 38px;
    height: 38px;
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

  &__title {
    margin: 0;
    font-size: @font-size-lg;
    font-weight: 700;
    color: @color-text-primary;
  }

  &__body {
    flex: 1;
    padding: @spacing-md;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    padding: 14px;
    margin-bottom: @spacing-sm;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
    cursor: pointer;

    &:active {
      background: #fafafa;
    }

    &-main {
      flex: 1;
      min-width: 0;
    }

    &-title {
      margin: 0 0 6px;
      font-size: 15px;
      font-weight: 600;
      color: @color-text-primary;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &-meta {
      margin: 0;
      font-size: @font-size-sm;
      color: @color-text-secondary;
    }

    &-del {
      flex-shrink: 0;
      width: 34px;
      height: 34px;
      border: none;
      border-radius: 50%;
      background: transparent;
      color: @color-text-secondary;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;

      svg {
        width: 19px;
        height: 19px;
      }
      &:active {
        background: @color-primary-light;
        color: @color-danger;
      }
    }
  }

  &__clear {
    display: block;
    margin: @spacing-lg auto 0;
    padding: 8px 20px;
    border: none;
    background: transparent;
    color: @color-text-secondary;
    font-size: @font-size-base;
    cursor: pointer;

    &:active {
      color: @color-danger;
    }
  }

  // 空状态
  &__empty {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: @spacing-md;

    &-icon {
      width: 72px;
      height: 72px;
      border-radius: 50%;
      background: @color-primary-light;
      color: @color-primary;
      display: flex;
      align-items: center;
      justify-content: center;

      svg {
        width: 36px;
        height: 36px;
      }
    }

    &-text {
      margin: 0;
      color: @color-text-secondary;
      font-size: @font-size-base;
    }

    &-btn {
      padding: 10px 36px;
      border: none;
      border-radius: 22px;
      background: @color-primary;
      color: #fff;
      font-size: @font-size-base;
      font-weight: 600;
      cursor: pointer;
      box-shadow: 0 4px 12px rgba(196, 30, 58, 0.3);

      &:active {
        transform: scale(0.96);
      }
    }
  }
}
</style>
