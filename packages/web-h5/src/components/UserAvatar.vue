<script setup lang="ts">
/**
 * 用户头像：自定义图片（dataURL）优先，否则显示红色主题预设图标
 * 预设配置来源于 profileStorage，与设置页头像选择器保持一致
 */
import { computed } from 'vue'
import { AVATAR_PRESETS, type UserProfile } from '@/utils/profileStorage'

const props = withDefaults(
  defineProps<{
    profile: Pick<UserProfile, 'avatar' | 'presetIndex'>
    size?: number
  }>(),
  { size: 56 },
)

const preset = computed(() => AVATAR_PRESETS[props.profile.presetIndex] ?? AVATAR_PRESETS[0])
const boxStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  background: props.profile.avatar ? '#f0f0f2' : preset.value.gradient,
}))
const iconSize = computed(() => Math.round(props.size * 0.6))
</script>

<template>
  <span class="user-avatar" :style="boxStyle">
    <img v-if="profile.avatar" :src="profile.avatar" alt="用户头像" class="user-avatar__img" />
    <!-- 默认人像 -->
    <svg
      v-else-if="preset.icon === 'person'"
      :width="iconSize"
      :height="iconSize"
      viewBox="0 0 24 24"
      fill="currentColor"
    >
      <path d="M12 12a5 5 0 100-10 5 5 0 000 10zm0 2c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5z" />
    </svg>
    <!-- 星星 -->
    <svg
      v-else-if="preset.icon === 'star'"
      :width="iconSize"
      :height="iconSize"
      viewBox="0 0 24 24"
      fill="currentColor"
    >
      <path d="M12 2l2.9 6.3 6.9.8-5.1 4.7 1.4 6.8L12 17.8 5.9 20.6l1.4-6.8L2.2 9.1l6.9-.8L12 2z" />
    </svg>
    <!-- 旗帜 -->
    <svg
      v-else-if="preset.icon === 'flag'"
      :width="iconSize"
      :height="iconSize"
      viewBox="0 0 24 24"
      fill="currentColor"
    >
      <path d="M5 3h2v18H5V3zm2 3h12l-2 4 2 4H7V6z" />
    </svg>
    <!-- 火炬 -->
    <svg
      v-else-if="preset.icon === 'fire'"
      :width="iconSize"
      :height="iconSize"
      viewBox="0 0 24 24"
      fill="currentColor"
    >
      <path d="M13.5 2s4.5 4 4.5 8.5a6 6 0 01-12 0c0-2 1-3.5 1-3.5s2 2.5 3 2.5c0-3 3.5-7.5 3.5-7.5zM12 22a5 5 0 01-5-5c0-2 1-3.5 2.5-4.5.5 1.5 1.5 2.5 2.5 3 .5-2 1.5-3 2.5-3.5C16 12.5 17 14 17 16a5 5 0 01-5 6z" />
    </svg>
    <!-- 书本 -->
    <svg
      v-else-if="preset.icon === 'book'"
      :width="iconSize"
      :height="iconSize"
      viewBox="0 0 24 24"
      fill="currentColor"
    >
      <path d="M4 4h6a3 3 0 013 3v13a2 2 0 00-2-2H4V4zm16 0h-6a3 3 0 00-3 3v13a2 2 0 012-2h7V4z" />
    </svg>
    <!-- 奖章 -->
    <svg
      v-else
      :width="iconSize"
      :height="iconSize"
      viewBox="0 0 24 24"
      fill="currentColor"
    >
      <path d="M12 2a7 7 0 100 14 7 7 0 000-14zm0 4l1.05 2.12 2.34.34-1.7 1.65.4 2.34-2.09-1.1-2.09 1.1.4-2.34-1.7-1.65 2.34-.34L12 6zM9 17h6l-1 5-2-1.2L10 22l-1-5z" />
    </svg>
  </span>
</template>

<style lang="less" scoped>
.user-avatar {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  overflow: hidden;
  vertical-align: middle;

  &__img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}
</style>
