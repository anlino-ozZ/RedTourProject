<script setup lang="ts">
/**
 * H5 登录/注册页（游客端）
 * 仅面向游客：登录 + 注册
 * 管理员登录走 web-admin 后台（由其他同学开发）
 * 纯 UI，提交逻辑为 mock，后续接入 API
 */
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { RedButton } from '@red-tour-project/common'

const router = useRouter()

type Mode = 'login' | 'register'
const mode = ref<Mode>('login')

const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)

const isRegister = computed(() => mode.value === 'register')
// 注册时密码不一致的内联提示
const passwordMismatch = computed(
  () => isRegister.value && confirmPassword.value.length > 0 && password.value !== confirmPassword.value,
)

function selectMode(m: Mode) {
  mode.value = m
}

function goBack() {
  // 尝试回到上一页，无历史则回首页
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}

function onSubmit() {
  if (loading.value) return
  if (!username.value || !password.value) return
  if (isRegister.value && password.value !== confirmPassword.value) return
  loading.value = true
  // UI 占位：模拟提交，后续替换为真实接口
  setTimeout(() => {
    loading.value = false
    router.push('/')
  }, 600)
}
</script>

<template>
  <div class="auth">
    <!-- 返回按钮（不登录也能退回） -->
    <button class="auth__back" @click="goBack" aria-label="返回">←</button>

    <!-- 顶部红色 Hero -->
    <div class="auth__hero">
      <div class="auth__logo">★</div>
      <h1 class="auth__title">红色文旅</h1>
      <p class="auth__subtitle">智能导览 · 探索红色足迹</p>
    </div>

    <!-- 表单卡片 -->
    <div class="auth__card">
      <!-- 模式切换：登录 / 注册 -->
      <div class="auth__seg">
        <button
          class="auth__seg-item"
          :class="{ 'is-active': mode === 'login' }"
          @click="selectMode('login')"
        >
          登录
        </button>
        <button
          class="auth__seg-item"
          :class="{ 'is-active': mode === 'register' }"
          @click="selectMode('register')"
        >
          注册
        </button>
      </div>

      <!-- 表单 -->
      <form class="auth__form" @submit.prevent="onSubmit">
        <label class="auth__field">
          <span class="auth__label">用户名</span>
          <input
            v-model="username"
            type="text"
            class="auth__input"
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </label>

        <label class="auth__field">
          <span class="auth__label">密码</span>
          <input
            v-model="password"
            type="password"
            class="auth__input"
            placeholder="请输入密码"
            :autocomplete="isRegister ? 'new-password' : 'current-password'"
          />
        </label>

        <label v-if="isRegister" class="auth__field">
          <span class="auth__label">确认密码</span>
          <input
            v-model="confirmPassword"
            type="password"
            class="auth__input"
            :class="{ 'is-error': passwordMismatch }"
            placeholder="请再次输入密码"
            autocomplete="new-password"
          />
          <span v-if="passwordMismatch" class="auth__error">两次密码不一致，请重新输入</span>
        </label>

        <!-- 提交统一走 form 的 submit（RedButton 原生 button 默认 type=submit），避免 click + submit 双重触发 -->
        <RedButton
          size="large"
          :loading="loading"
          :disabled="
            !username || !password || (isRegister && !confirmPassword) || passwordMismatch
          "
          class="auth__submit"
        >
          {{ isRegister ? '注册' : '登录' }}
        </RedButton>
      </form>

      <!-- 辅助链接 -->
      <div class="auth__footer">
        <span v-if="!isRegister" class="auth__link" @click="selectMode('register')">
          没有账号？去注册
        </span>
        <span v-else class="auth__link" @click="selectMode('login')">
          已有账号？去登录
        </span>
      </div>
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.auth {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: @color-bg-page;
  overflow: hidden;

  &__back {
    position: absolute;
    top: @spacing-md;
    left: @spacing-md;
    z-index: 10;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    border: none;
    background: rgba(255, 255, 255, 0.2);
    color: #fff;
    font-size: 20px;
    line-height: 1;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background 0.2s;
    &:hover {
      background: rgba(255, 255, 255, 0.3);
    }
  }

  &__hero {
    padding: 48px @spacing-lg 48px;
    background: linear-gradient(135deg, @color-primary 0%, @color-primary-active 100%);
    color: #fff;
    text-align: center;
    border-radius: 0 0 @radius-lg @radius-lg;
  }
  &__logo {
    width: 56px;
    height: 56px;
    margin: 0 auto @spacing-sm;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.15);
    font-size: 28px;
    color: @color-accent;
  }
  &__title {
    font-size: 24px;
    font-weight: 700;
    letter-spacing: 2px;
    margin-bottom: @spacing-xs;
  }
  &__subtitle {
    font-size: @font-size-sm;
    opacity: 0.9;
  }

  &__card {
    margin: -@spacing-xl @spacing-md 0;
    padding: @spacing-lg @spacing-md;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: @shadow-card;
  }

  // 模式切换分段控制器
  &__seg {
    display: flex;
    background: @color-bg-page;
    border-radius: @radius-base;
    padding: 3px;
    margin-bottom: @spacing-lg;
    &-item {
      flex: 1;
      height: 38px;
      border: none;
      background: transparent;
      border-radius: @radius-sm;
      font-size: @font-size-base;
      color: @color-text-secondary;
      cursor: pointer;
      transition: all 0.2s;
      &.is-active {
        background: @color-bg-card;
        color: @color-primary;
        font-weight: 600;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
      }
    }
  }

  // 表单
  &__form {
    display: flex;
    flex-direction: column;
    gap: @spacing-md;
  }
  &__field {
    display: flex;
    flex-direction: column;
    gap: @spacing-xs;
  }
  &__label {
    font-size: @font-size-sm;
    color: @color-text-regular;
  }
  &__input {
    height: 44px;
    padding: 0 @spacing-md;
    border: 1px solid @color-border;
    border-radius: @radius-base;
    background: @color-bg-card;
    font-size: @font-size-base;
    color: @color-text-primary;
    transition: border-color 0.2s;
    &::placeholder {
      color: @color-text-secondary;
    }
    &:focus {
      border-color: @color-primary;
      outline: none;
    }
    &.is-error {
      border-color: @color-danger;
    }
  }
  &__error {
    font-size: @font-size-sm;
    color: @color-danger;
  }
  &__submit {
    width: 100%;
    margin-top: @spacing-sm;
  }

  &__footer {
    margin-top: @spacing-md;
    text-align: center;
  }
  &__link {
    font-size: @font-size-sm;
    color: @color-primary;
    cursor: pointer;
  }
}
</style>
