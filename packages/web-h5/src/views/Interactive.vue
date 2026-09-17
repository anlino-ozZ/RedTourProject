<script setup lang="ts">
/**
 * 互动中心（沉浸式）
 * 顶部暗色剧本 Hero（剧场氛围）+ 白色内容区上叠（成就徽章 + 待解锁任务）
 * 数据暂为 Mock
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { mockAchievements, mockTasks, mockScript } from '@/mock/interaction'
import type { AchievementBadge, InteractionTask } from '@/mock/interaction'

const router = useRouter()

const achievements = ref<AchievementBadge[]>(mockAchievements)
const tasks = ref<InteractionTask[]>(mockTasks)

// 当前进行中的剧本（Hero 展示位）
const featuredTask = computed(() => tasks.value.find((t) => t.actionTo))
const earnedCount = computed(() => achievements.value.filter((a) => a.earned).length)

const toastText = ref('')
let toastTimer: ReturnType<typeof setTimeout> | undefined
function showToast(text: string) {
  toastText.value = text
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toastText.value = ''), 1800)
}

function onTask(task: InteractionTask) {
  if (task.actionTo) {
    router.push(task.actionTo)
  } else {
    showToast('该任务即将上线，敬请期待')
  }
}
</script>

<template>
  <div class="interactive">
    <!-- ===== 暗色剧本 Hero（沉浸式剧场氛围） ===== -->
    <div class="hero">
      <div class="hero__art" />
      <div class="hero__mask" />
      <div class="hero__content">
        <div class="hero__head">
          <h1 class="hero__title">互动中心</h1>
          <span class="hero__achieve">{{ earnedCount }}/{{ achievements.length }} 成就</span>
        </div>

        <!-- 剧本展示位 -->
        <div v-if="featuredTask" class="script-banner" @click="onTask(featuredTask)">
          <span class="script-banner__tag">沉浸式剧本</span>
          <h2 class="script-banner__name">红色剧本 · 暗哨行动</h2>
          <p class="script-banner__desc">{{ featuredTask.desc }}</p>
          <div class="script-banner__progress">
            <i :style="{ width: '60%' }" />
          </div>
          <button class="script-banner__cta">进入剧本</button>
        </div>
      </div>
    </div>

    <!-- ===== 白色内容区（上叠圆角） ===== -->
    <div class="panel">
      <!-- 成就徽章 -->
      <div class="panel__section-head">
        <h2 class="panel__section-title">红色成就馆</h2>
        <span class="panel__section-more">集齐徽章点亮初心</span>
      </div>
      <div class="badges">
        <div
          v-for="badge in achievements"
          :key="badge.id"
          class="badge"
          :class="{ 'is-earned': badge.earned }"
        >
          <div
            class="badge__circle"
            :style="badge.earned && badge.color ? { background: badge.color } : undefined"
          >
            <svg
              v-if="badge.earned"
              class="badge__icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#fff"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <circle cx="12" cy="8" r="6" />
              <path d="M15.5 13l1.5 8-5-3-5 3 1.5-8" />
            </svg>
            <svg
              v-else
              class="badge__icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <rect x="4" y="11" width="16" height="10" rx="2" />
              <path d="M8 11V7a4 4 0 018 0v4" />
            </svg>
          </div>
          <span class="badge__name">{{ badge.name }}</span>
        </div>
      </div>

      <!-- 待解锁任务 -->
      <div class="panel__section-head">
        <h2 class="panel__section-title">待解锁任务</h2>
      </div>
      <div class="tasks">
        <div
          v-for="task in tasks"
          :key="task.id"
          class="task-card"
          @click="onTask(task)"
        >
          <div class="task-card__info">
            <div class="task-card__title">{{ task.title }}</div>
            <div class="task-card__desc">{{ task.desc }}</div>
          </div>
          <button
            class="task-card__action"
            :class="{ 'is-todo': task.status === 'todo' }"
          >
            {{ task.actionText }}
          </button>
        </div>
      </div>
    </div>

    <!-- 轻提示 -->
    <transition name="fade">
      <div v-if="toastText" class="interactive__toast">{{ toastText }}</div>
    </transition>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.interactive {
  position: relative;
  min-height: 100%;
  margin: -@spacing-md; // 抵消 Layout 内边距，实现全出血 Hero
  margin-bottom: 0;

  &__toast {
    position: fixed;
    left: 50%;
    bottom: 120px;
    transform: translateX(-50%);
    max-width: 80%;
    padding: 10px 20px;
    border-radius: @radius-base;
    background: rgba(0, 0, 0, 0.75);
    color: #fff;
    font-size: @font-size-base;
    white-space: nowrap;
    z-index: 200;
  }
}

// ===== Hero：剧场氛围 =====
.hero {
  position: relative;
  padding: @spacing-lg @spacing-md @spacing-xl;
  background: radial-gradient(120% 90% at 50% 0%, #3d0d16 0%, #1a0a0e 60%, #120709 100%);
  overflow: hidden;

  &__art {
    position: absolute;
    inset: 0;
    // 剧场暗红展厅场景
    background:
      url('https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=dark%20red%20theatrical%20stage%20curtain%20with%20soft%20spotlight%2C%20vintage%20revolutionary%20museum%20atmosphere%2C%20cinematic%20moody%20lighting%2C%20deep%20crimson%20and%20black%20tones&image_size=landscape_16_9')
        center / cover no-repeat;
    opacity: 0.35;
  }

  &__mask {
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(18, 7, 9, 0.2) 0%, rgba(18, 7, 9, 0.92) 100%);
  }

  &__content {
    position: relative;
    z-index: 1;
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: @spacing-md;
  }

  &__title {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
    color: #fff;
    letter-spacing: 1px;
  }

  &__achieve {
    padding: 4px 10px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.12);
    backdrop-filter: blur(6px);
    color: rgba(255, 255, 255, 0.85);
    font-size: @font-size-sm;
  }
}

// ===== 剧本展示位 =====
.script-banner {
  position: relative;
  padding: @spacing-md;
  border-radius: @radius-lg;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(10px);
  cursor: pointer;

  &:active {
    transform: scale(0.99);
  }

  &__tag {
    display: inline-block;
    padding: 2px 8px;
    margin-bottom: @spacing-sm;
    border-radius: @radius-sm;
    background: @color-primary;
    color: #fff;
    font-size: @font-size-sm;
    font-weight: 600;
  }

  &__name {
    margin: 0 0 4px;
    font-size: 20px;
    font-weight: 700;
    color: #fff;
  }

  &__desc {
    margin: 0 0 @spacing-md;
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.6);
  }

  &__progress {
    height: 6px;
    margin-bottom: @spacing-md;
    border-radius: 3px;
    background: rgba(255, 255, 255, 0.15);
    overflow: hidden;

    i {
      display: block;
      height: 100%;
      border-radius: 3px;
      background: linear-gradient(90deg, @color-primary 0%, @color-primary-hover 100%);
    }
  }

  &__cta {
    width: 100%;
    padding: 10px 0;
    border: none;
    border-radius: @radius-base;
    background: linear-gradient(135deg, @color-primary 0%, @color-primary-active 100%);
    color: #fff;
    font-size: @font-size-base;
    font-weight: 700;
    letter-spacing: 2px;
    cursor: pointer;
    box-shadow: 0 4px 14px rgba(196, 30, 58, 0.4);

    &:active {
      transform: scale(0.98);
    }
  }
}

// ===== 白色内容区 =====
.panel {
  position: relative;
  z-index: 2;
  margin-top: -@spacing-lg; // 上叠 Hero
  padding: @spacing-lg @spacing-md @spacing-md;
  border-radius: @radius-lg @radius-lg 0 0;
  background: @color-bg-page;

  &__section-head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin-bottom: @spacing-md;
  }

  &__section-title {
    margin: 0;
    font-size: @font-size-lg;
    font-weight: 700;
    color: @color-text-primary;
  }

  &__section-more {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  & + .panel {
    margin-top: @spacing-sm;
  }
}

// ===== 成就徽章 =====
.badges {
  display: flex;
  justify-content: space-between;
  padding: @spacing-md @spacing-lg;
  margin-bottom: @spacing-lg;
  border-radius: @radius-lg;
  background: @color-bg-card;
  box-shadow: @shadow-card;
}

.badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: @spacing-sm;

  &__circle {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 60px;
    height: 60px;
    border-radius: 50%;
    background: @color-text-secondary;
  }

  &.is-earned .badge__circle {
    box-shadow: 0 4px 14px rgba(196, 30, 58, 0.25);
  }

  &__icon {
    width: 28px;
    height: 28px;
  }

  &__name {
    font-size: @font-size-sm;
    color: @color-text-secondary;

    .is-earned & {
      color: @color-text-primary;
      font-weight: 600;
    }
  }
}

// ===== 任务卡片 =====
.tasks {
  display: flex;
  flex-direction: column;
  gap: @spacing-md;
}

.task-card {
  display: flex;
  align-items: center;
  gap: @spacing-md;
  padding: @spacing-md;
  background: @color-bg-card;
  border-radius: @radius-lg;
  border-left: 4px solid @color-primary;
  box-shadow: @shadow-card;
  cursor: pointer;

  &:active {
    transform: scale(0.99);
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__title {
    margin-bottom: 4px;
    font-size: 15px;
    font-weight: 600;
    color: @color-text-primary;
  }

  &__desc {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__action {
    flex-shrink: 0;
    padding: 6px 14px;
    border: none;
    border-radius: 14px;
    background: @color-primary;
    color: #fff;
    font-size: @font-size-sm;
    font-weight: 600;
    cursor: pointer;

    &.is-todo {
      background: #d9d9d9;
      color: @color-text-secondary;
    }
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
