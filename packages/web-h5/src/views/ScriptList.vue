<script setup lang="ts">
/**
 * 剧本选择页：列出全部剧本（可玩/已通关/即将上线）
 * 通关状态由 achievementStorage 中奖励徽章的解锁记录推导
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { mockScripts, getBadgeById } from '@/mock/interaction'
import type { Script } from '@/mock/interaction'
import { getUnlockedBadges } from '@/utils/achievementStorage'
import { playTapSound, playLockedSound } from '@/utils/sound'

const router = useRouter()
const unlockedMap = getUnlockedBadges()

/** 是否已通关（奖励徽章已解锁即视为通关） */
function isCleared(script: Script) {
  return !!script.reward.badgeId && !!unlockedMap[script.reward.badgeId]
}

const playableCount = computed(() => mockScripts.filter((s) => !s.locked).length)

function rewardBadgeName(script: Script) {
  return script.reward.badgeId ? (getBadgeById(script.reward.badgeId)?.name ?? '') : ''
}

function onPick(script: Script) {
  if (script.locked) {
    playLockedSound()
    return
  }
  playTapSound()
  router.push({ path: '/interactive/script', query: { scriptId: script.id } })
}
</script>

<template>
  <div class="script-list">
    <!-- 返回互动中心（Layout 顶部标题栏无返回能力，子页自实现） -->
    <button class="script-list__back" @click="router.back()">
      <span class="script-list__back-arrow">←</span>
      <span>返回互动中心</span>
    </button>

    <p class="script-list__tip">共 {{ playableCount }} 部剧本可体验，通关可点亮对应成就徽章</p>

    <div
      v-for="s in mockScripts"
      :key="s.id"
      class="script-card"
      :class="{ 'is-locked': s.locked }"
      role="button"
      tabindex="0"
      @click="onPick(s)"
      @keyup.enter="onPick(s)"
    >
      <!-- 主题色封面 -->
      <div
        class="script-card__cover"
        :style="{ background: `linear-gradient(135deg, ${s.color} 0%, #1a0a0e 130%)` }"
      >
        <span v-if="isCleared(s)" class="script-card__cleared">✓ 已通关</span>
        <span v-else-if="s.locked" class="script-card__soon">即将上线</span>
        <h3 class="script-card__title">{{ s.title }}</h3>
        <p class="script-card__subtitle">{{ s.subtitle }}</p>
      </div>

      <!-- 信息区 -->
      <div class="script-card__body">
        <div class="script-card__meta">
          <span v-if="!s.locked">{{ s.tasks.length }} 个任务 · {{ s.reward.points }} 积分</span>
          <span v-else>内容筹备中</span>
          <span v-if="rewardBadgeName(s)" class="script-card__reward">
            🏅 {{ rewardBadgeName(s) }}
          </span>
        </div>
        <button
          class="script-card__cta"
          :class="{ 'is-cleared': isCleared(s) }"
          :disabled="s.locked"
        >
          {{ s.locked ? '敬请期待' : isCleared(s) ? '再玩一次' : '开始剧本' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.script-list {
  display: flex;
  flex-direction: column;
  gap: @spacing-md;

  &__back {
    display: inline-flex;
    align-items: center;
    align-self: flex-start;
    gap: 4px;
    padding: 8px 16px;
    border: 1.5px solid @color-primary;
    border-radius: 20px;
    background: @color-primary-light;
    color: @color-primary;
    font-size: @font-size-base;
    font-weight: 600;
    cursor: pointer;

    &:active {
      background: @color-primary;
      color: #fff;
    }
  }

  &__tip {
    margin: 0 0 @spacing-xs;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }
}

.script-card {
  border-radius: @radius-lg;
  background: @color-bg-card;
  box-shadow: @shadow-card;
  overflow: hidden;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;

  &:active {
    transform: scale(0.99);
  }

  &.is-locked {
    .script-card__cover {
      filter: grayscale(0.75) brightness(0.8);
    }

    .script-card__cta {
      background: #d9d9d9;
      color: @color-text-secondary;
      cursor: not-allowed;
    }
  }

  &__cover {
    position: relative;
    padding: @spacing-lg @spacing-md @spacing-md;
    min-height: 96px;
    display: flex;
    flex-direction: column;
    justify-content: flex-end;
  }

  &__cleared,
  &__soon {
    position: absolute;
    top: @spacing-sm;
    right: @spacing-sm;
    padding: 3px 10px;
    border-radius: 12px;
    font-size: @font-size-sm;
    font-weight: 600;
  }

  &__cleared {
    background: rgba(212, 175, 55, 0.92);
    color: #5a4310;
  }

  &__soon {
    background: rgba(255, 255, 255, 0.18);
    color: rgba(255, 255, 255, 0.85);
  }

  &__title {
    margin: 0 0 2px;
    font-size: 20px;
    font-weight: 700;
    color: #fff;
    letter-spacing: 1px;
  }

  &__subtitle {
    margin: 0;
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.65);
  }

  &__body {
    display: flex;
    align-items: center;
    gap: @spacing-md;
    padding: @spacing-md;
  }

  &__meta {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__reward {
    color: #a07f1c;
    font-weight: 600;
  }

  &__cta {
    flex-shrink: 0;
    padding: 8px 18px;
    border: none;
    border-radius: 16px;
    background: @color-primary;
    color: #fff;
    font-size: @font-size-base;
    font-weight: 600;
    cursor: pointer;

    &.is-cleared {
      background: rgba(196, 30, 58, 0.12);
      color: @color-primary;
    }

    &:active {
      transform: scale(0.96);
    }
  }
}
</style>
