<script setup lang="ts">
/**
 * 荣誉勋章徽标：绶带 + 抛光金属外圈 + 珐琅内盘 + 白色浮雕图标
 * 勋章墙与勋章详情弹窗共用，避免两处观感不一致
 */
import type { MedalItem } from '@/mock/profile'

withDefaults(
  defineProps<{
    icon: MedalItem['icon']
    earned: boolean
    /** 珐琅底色；未点亮时统一置灰 */
    color?: string
    /** 金属圆盘直径（px） */
    size?: number
  }>(),
  { color: '#c41e3a', size: 54 },
)
</script>

<template>
  <span
    class="medal-badge"
    :class="{ 'is-locked': !earned }"
    :style="{
      '--medal-color': earned ? color : '#b4b4bc',
      '--medal-size': `${size}px`,
    }"
    aria-hidden="true"
  >
    <span class="medal-badge__ribbon" />
    <span class="medal-badge__disc">
      <svg class="medal-badge__glyph" viewBox="0 0 24 24" fill="currentColor">
        <path
          v-if="icon === 'star'"
          d="M12 2l2.9 6.3 6.9.8-5.1 4.7 1.4 6.8L12 17.8 5.9 20.6l1.4-6.8L2.2 9.1l6.9-.8L12 2z"
        />
        <g v-else-if="icon === 'check'" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="8.6" />
          <path d="M8.4 12.4l2.4 2.4 4.8-5.1" stroke-linecap="round" stroke-linejoin="round" />
        </g>
        <path
          v-else-if="icon === 'trophy'"
          d="M6 4h12v2a6 6 0 01-12 0V4zm0 2v0a4 4 0 008 0H6zm2 8h8l-1 6H9l-1-6zm-3-6h2v2a4 4 0 01-2-2zm14 0h-2v2a4 4 0 002-2z"
        />
        <path
          v-else-if="icon === 'map'"
          d="M12 2a7 7 0 00-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 00-7-7zm0 9.5A2.5 2.5 0 1112 6.5a2.5 2.5 0 010 5z"
        />
        <path
          v-else-if="icon === 'fire'"
          d="M13.5 2s4.5 4 4.5 8.5a6 6 0 01-12 0c0-2 1-3.5 1-3.5s2 2.5 3 2.5c0-3 3.5-7.5 3.5-7.5zM12 22a5 5 0 01-5-5c0-2 1-3.5 2.5-4.5.5 1.5 1.5 2.5 2.5 3 .5-2 1.5-3 2.5-3.5C16 12.5 17 14 17 16a5 5 0 01-5 6z"
        />
        <g v-else-if="icon === 'book'" fill="none" stroke="currentColor" stroke-width="1.8">
          <path
            d="M12 6.6C10.4 5.3 8.2 4.9 6 4.9v12.3c2.2 0 4.4.4 6 1.6 1.6-1.2 3.8-1.6 6-1.6V4.9c-2.2 0-4.4.4-6 1.7z"
            stroke-linejoin="round"
          />
          <path d="M12 6.6v12.2" stroke-linecap="round" />
        </g>
        <path v-else-if="icon === 'flag'" d="M5 3h2v18H5V3zm2 3h12l-2 4 2 4H7V6z" />
        <path v-else d="M17 9V7a5 5 0 00-10 0v2H5v13h14V9h-2zm-8-2a3 3 0 016 0v2H9V7z" />
      </svg>
    </span>
  </span>
</template>

<style lang="less" scoped>
// ===== 勋章徽标：绶带 + 金属外圈 + 珐琅内盘 =====
.medal-badge {
  --medal-size: 54px;
  --medal-color: #c41e3a;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  width: var(--medal-size);

  &__ribbon {
    width: calc(var(--medal-size) * 0.56);
    height: calc(var(--medal-size) * 0.32);
    margin-bottom: calc(var(--medal-size) * -0.12);
    background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.34), rgba(0, 0, 0, 0.26)),
      var(--medal-color);
    clip-path: polygon(0 0, 100% 0, 100% 100%, 50% 68%, 0 100%);
  }

  &__disc {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    width: var(--medal-size);
    height: var(--medal-size);
    border-radius: 50%;
    // 抛光金属外圈：斜向多段渐变模拟金属反光
    background: linear-gradient(
      150deg,
      #fdf7de 0%,
      #e8ca78 22%,
      #b8860b 46%,
      #f7e9b7 68%,
      #a97c14 100%
    );
    box-shadow:
      0 2px 5px rgba(122, 88, 12, 0.26),
      inset 0 0 0 1px rgba(255, 255, 255, 0.55);
    transition: transform 0.15s;

    // 珐琅内盘
    &::after {
      content: '';
      position: absolute;
      inset: calc(var(--medal-size) * 0.08);
      border-radius: 50%;
      background:
        radial-gradient(circle at 32% 26%, rgba(255, 255, 255, 0.4), rgba(255, 255, 255, 0) 58%),
        linear-gradient(160deg, rgba(255, 255, 255, 0.18), rgba(0, 0, 0, 0.18)),
        var(--medal-color);
      box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.28);
    }
  }

  &__glyph {
    position: relative;
    z-index: 1;
    width: calc(var(--medal-size) * 0.46);
    height: calc(var(--medal-size) * 0.46);
    color: #fff;
    filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.26));
  }

  // 未点亮：银质盘面 + 灰色珐琅，保留勋章形态
  &.is-locked {
    .medal-badge__ribbon {
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.52), rgba(0, 0, 0, 0.08)),
        #d5d5dd;
    }

    .medal-badge__disc {
      background: linear-gradient(150deg, #f4f4f7 0%, #dcdce3 40%, #f1f1f5 62%, #ccccd6 100%);
      box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.7);

      &::after {
        box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.1);
      }
    }

    .medal-badge__glyph {
      opacity: 0.92;
    }
  }
}
</style>
