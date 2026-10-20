<script setup lang="ts">
/**
 * H5 登录/注册页（游客端）
 * 仅面向游客：登录 + 注册
 * 管理员登录走 web-admin 后台（由其他同学开发）
 * 纯 UI，提交逻辑为 mock，后续接入 API
 */
import { ref, computed, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RedButton } from '@red-tour-project/common'
import { playCorrectSound, playTapSound, playWrongSound } from '@/utils/sound'
import { useUserStore } from '@/store/user'
import { ApiError } from '@/api/request'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 被登录保护拦截后，登录成功回到原目标页 */
const loginRedirect = computed(() => {
  const target = route.query.redirect
  return typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')
    ? target
    : '/'
})

type Mode = 'login' | 'register' | 'forgot'
const mode = ref<Mode>('login')

const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const showPassword = ref(false)

const isRegister = computed(() => mode.value === 'register')
// 注册时密码不一致的内联提示
const passwordMismatch = computed(
  () => isRegister.value && confirmPassword.value.length > 0 && password.value !== confirmPassword.value,
)

// ===== 忘记密码（演示流程：验证身份 → 设置新密码，均为 mock）=====
const phone = ref('')
const smsCode = ref('')
const phoneError = ref('')
const smsError = ref('')
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const forgotStep = ref(1)
const forgotNew = ref('')
const forgotConfirm = ref('')
const forgotNewError = ref('')
const forgotConfirmError = ref('')
// 重置成功后回到登录页的成功提示
const resetSuccess = ref(false)

const isPhoneValid = computed(() => /^1\d{10}$/.test(phone.value))

/** 发送验证码（UI 占位：mock 倒计时，接入后替换为短信接口） */
function sendCode() {
  if (countdown.value > 0) return
  if (!isPhoneValid.value) {
    phoneError.value = '请输入正确的 11 位手机号'
    playWrongSound()
    return
  }
  phoneError.value = ''
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
  playCorrectSound()
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

function enterForgot() {
  mode.value = 'forgot'
  forgotStep.value = 1
  phoneError.value = ''
  smsError.value = ''
  forgotNewError.value = ''
  forgotConfirmError.value = ''
  resetSuccess.value = false
  playTapSound()
}

function backToLogin() {
  mode.value = 'login'
  forgotStep.value = 1
  phone.value = ''
  smsCode.value = ''
  forgotNew.value = ''
  forgotConfirm.value = ''
  resetSuccess.value = false
  playTapSound()
}

// ===== 注册校验（登录仅要求非空）=====
// 与后端 LoginRequest / RegisterRequest 的校验规则保持一致
const USERNAME_MIN = 3
const USERNAME_MAX = 20
const PATTERN_USERNAME = /^[a-zA-Z0-9_]+$/
const PASSWORD_MIN = 6
const PASSWORD_MAX = 32

const usernameError = ref('')
const passwordError = ref('')
// 接口返回的失败原因（如"用户名或密码错误"），展示在提交按钮下方
const submitError = ref('')
const submitShake = ref(0)

/** 校验当前表单：直接把错误写到对应字段，返回是否通过 */
function validate(): boolean {
  let ok = true
  usernameError.value = ''
  passwordError.value = ''
  phoneError.value = ''
  smsError.value = ''
  if (isRegister.value) {
    const name = username.value.trim()
    if (name.length < USERNAME_MIN || name.length > USERNAME_MAX) {
      usernameError.value = `用户名需 ${USERNAME_MIN}-${USERNAME_MAX} 个字符`
      ok = false
    } else if (!PATTERN_USERNAME.test(name)) {
      usernameError.value = '用户名仅允许字母、数字或下划线'
      ok = false
    } else if (password.value.length < PASSWORD_MIN || password.value.length > PASSWORD_MAX) {
      passwordError.value = `密码需 ${PASSWORD_MIN}-${PASSWORD_MAX} 位`
      ok = false
    } else if (password.value !== confirmPassword.value) {
      passwordError.value = '两次密码不一致，请重新输入'
      ok = false
    }
    return ok
  }
  return ok
}

/** 提交按钮可用性 */
const canSubmit = computed(() => {
  if (mode.value === 'forgot') {
    if (forgotStep.value === 1) return isPhoneValid.value && smsCode.value.length === 6
    return forgotNew.value.length >= PASSWORD_MIN && forgotNew.value === forgotConfirm.value
  }
  if (isRegister.value) {
    return !!username.value && !!password.value && !!confirmPassword.value && !passwordMismatch.value
  }
  return !!username.value && !!password.value
})

function selectMode(m: Mode) {
  mode.value = m
  // 切换模式时清掉上一次的校验错误，避免误导
  usernameError.value = ''
  passwordError.value = ''
  submitError.value = ''
  phoneError.value = ''
  smsError.value = ''
}

function goBack() {
  // 忘记密码流程中，返回键先回到登录页
  if (mode.value === 'forgot') {
    backToLogin()
    return
  }
  // 尝试回到上一页，无历史则回首页
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}

async function onSubmit() {
  if (loading.value || !canSubmit.value) return

  // ===== 忘记密码：两步分步提交 =====
  if (mode.value === 'forgot') {
    if (forgotStep.value === 1) {
      if (!isPhoneValid.value) {
        phoneError.value = '请输入正确的 11 位手机号'
        playWrongSound()
        submitShake.value++
        return
      }
      if (smsCode.value.length !== 6) {
        smsError.value = '请输入 6 位验证码'
        playWrongSound()
        submitShake.value++
        return
      }
      // 演示环境：任意 6 位验证码可通过；接入后由短信接口校验
      smsError.value = ''
      forgotStep.value = 2
      playCorrectSound()
      return
    }
    forgotNewError.value = ''
    forgotConfirmError.value = ''
    if (forgotNew.value.length < PASSWORD_MIN) {
      forgotNewError.value = `密码至少 ${PASSWORD_MIN} 位`
      playWrongSound()
      submitShake.value++
      return
    }
    if (forgotNew.value !== forgotConfirm.value) {
      forgotConfirmError.value = '两次密码不一致，请重新输入'
      playWrongSound()
      submitShake.value++
      return
    }
    // mock 重置成功：清空流程态，回到登录页并提示
    loading.value = true
    setTimeout(() => {
      loading.value = false
      phone.value = ''
      smsCode.value = ''
      forgotNew.value = ''
      forgotConfirm.value = ''
      forgotStep.value = 1
      resetSuccess.value = true
      mode.value = 'login'
      playCorrectSound()
    }, 600)
    return
  }

  if (!validate()) {
    playWrongSound()
    // 触发表单抖动，错误已写到对应字段下方
    submitShake.value++
    return
  }
  // 登录 / 注册：调用真实接口（后端失败原因由 axios 拦截器提示，这里再在表单内提示一次）
  submitError.value = ''
  loading.value = true
  try {
    const payload = { username: username.value.trim(), password: password.value }
    if (isRegister.value) {
      await userStore.register(payload)
    } else {
      await userStore.login(payload)
    }
    // 登录/注册成功：上行琶音后再跳转
    playCorrectSound()
    router.push(loginRedirect.value)
  } catch (err) {
    // 业务错误展示后端 message（如"用户名或密码错误"）；网络异常等给通用文案
    submitError.value = err instanceof ApiError ? err.message : '网络异常，请稍后重试'
    playWrongSound()
    submitShake.value++
  } finally {
    loading.value = false
  }
}

function togglePassword() {
  showPassword.value = !showPassword.value
  playTapSound()
}
</script>

<template>
  <div class="auth">
    <!-- 返回按钮（不登录也能退回） -->
    <button class="auth__back" @click="goBack" aria-label="返回">←</button>

    <!-- 顶部红色 Hero -->
    <div class="auth__hero">
      <i class="auth__spot auth__spot--1" />
      <i class="auth__spot auth__spot--2" />
      <i class="auth__spot auth__spot--3" />
      <!-- 远山剪影（红色研学 · 山峦意象） -->
      <svg class="auth__hills" viewBox="0 0 375 80" preserveAspectRatio="none" aria-hidden="true">
        <path d="M0 52 Q46 30 92 44 T187 40 T281 48 T375 34 V80 H0 Z" fill="rgba(255,255,255,0.09)" />
        <path d="M0 66 Q60 48 122 58 T245 56 T375 62 V80 H0 Z" fill="rgba(255,255,255,0.07)" />
      </svg>
      <div class="auth__logo">★</div>
      <div class="auth__brand">红色文旅</div>
      <h1 class="auth__title">星火照山河</h1>
      <p class="auth__subtitle">循星火之迹 · 赴山河之约</p>
    </div>

    <!-- 表单卡片 -->
    <div class="auth__card">
      <!-- 模式切换：登录 / 注册 -->
      <div v-if="mode !== 'forgot'" class="auth__seg">
        <button
          type="button"
          class="auth__seg-item"
          :class="{ 'is-active': mode === 'login' }"
          @click="selectMode('login')"
        >
          登录
        </button>
        <button
          type="button"
          class="auth__seg-item"
          :class="{ 'is-active': mode === 'register' }"
          @click="selectMode('register')"
        >
        注册
        </button>
      </div>

      <!-- 表单 -->
      <form class="auth__form" :class="{ 'is-shake': submitShake > 0 }" :key="submitShake" @submit.prevent="onSubmit">
        <!-- 忘记密码：验证身份 → 设置新密码 -->
        <template v-if="mode === 'forgot'">
          <div class="auth__steps">
            <span class="auth__step" :class="{ 'is-active': forgotStep >= 1 }">① 验证身份</span>
            <span class="auth__steps-line" />
            <span class="auth__step" :class="{ 'is-active': forgotStep >= 2 }">② 设置新密码</span>
          </div>

          <label v-if="forgotStep === 1" class="auth__field">
            <span class="auth__label">手机号</span>
            <span class="auth__input-wrap">
              <i class="auth__field-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="7" y="2" width="10" height="20" rx="2" />
                  <path d="M11 18h2" />
                </svg>
              </i>
              <input
                v-model="phone"
                type="tel"
                class="auth__input"
                :class="{ 'is-error': !!phoneError }"
                placeholder="请输入绑定手机号"
                maxlength="11"
                inputmode="numeric"
                autocomplete="tel"
                @input="phoneError = ''"
              />
            </span>
            <span v-if="phoneError" class="auth__error">{{ phoneError }}</span>
          </label>

          <label v-if="forgotStep === 1" class="auth__field">
            <span class="auth__label">验证码</span>
            <span class="auth__input-wrap">
              <i class="auth__field-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M4 6h16v12H4z" />
                  <path d="M4 7l8 6 8-6" />
                </svg>
              </i>
              <input
                v-model="smsCode"
                type="text"
                class="auth__input"
                :class="{ 'is-error': !!smsError }"
                placeholder="请输入验证码"
                maxlength="6"
                inputmode="numeric"
                autocomplete="one-time-code"
                @input="smsError = ''"
              />
              <button type="button" class="auth__send" :disabled="countdown > 0 || !isPhoneValid" @click="sendCode">
                {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
              </button>
            </span>
            <span v-if="smsError" class="auth__error">{{ smsError }}</span>
            <span class="auth__hint">验证码将发送至该手机号（演示环境任意 6 位数字可通过）</span>
          </label>

          <label v-if="forgotStep === 2" class="auth__field">
            <span class="auth__label">新密码</span>
            <span class="auth__input-wrap">
              <i class="auth__field-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" />
                  <path d="M7 11V7a5 5 0 0110 0v4" />
                </svg>
              </i>
              <input
                v-model="forgotNew"
                type="password"
                class="auth__input"
                :class="{ 'is-error': !!forgotNewError }"
                placeholder="请输入新密码（至少 6 位）"
                autocomplete="new-password"
                @input="forgotNewError = ''"
              />
            </span>
            <span v-if="forgotNewError" class="auth__error">{{ forgotNewError }}</span>
          </label>

          <label v-if="forgotStep === 2" class="auth__field">
            <span class="auth__label">确认新密码</span>
            <span class="auth__input-wrap">
              <i class="auth__field-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                  <path d="M9 12l2 2 4-4" />
                </svg>
              </i>
              <input
                v-model="forgotConfirm"
                type="password"
                class="auth__input"
                :class="{ 'is-error': !!forgotConfirmError }"
                placeholder="请再次输入新密码"
                autocomplete="new-password"
                @input="forgotConfirmError = ''"
              />
            </span>
            <span v-if="forgotConfirmError" class="auth__error">{{ forgotConfirmError }}</span>
          </label>
        </template>

        <label v-if="mode !== 'forgot'" class="auth__field">
          <span class="auth__label">用户名</span>
          <span class="auth__input-wrap">
            <i class="auth__field-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
            </i>
            <input
              v-model="username"
              type="text"
              class="auth__input"
              :class="{ 'is-error': !!usernameError }"
              placeholder="请输入用户名"
              maxlength="20"
              autocomplete="username"
              @input="usernameError = ''"
            />
          </span>
          <span v-if="usernameError" class="auth__error">{{ usernameError }}</span>
        </label>

        <label v-if="mode !== 'forgot'" class="auth__field">
          <span class="auth__label">密码</span>
          <span class="auth__input-wrap">
            <i class="auth__field-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="11" width="18" height="11" rx="2" />
                <path d="M7 11V7a5 5 0 0110 0v4" />
              </svg>
            </i>
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              class="auth__input"
              :class="{ 'is-error': !!passwordError }"
              placeholder="请输入密码"
              :autocomplete="isRegister ? 'new-password' : 'current-password'"
              @input="passwordError = ''"
            />
            <button
              type="button"
              class="auth__eye"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="togglePassword"
            >
              <!-- 眼睛（可见） -->
              <svg v-if="showPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                <circle cx="12" cy="12" r="3" />
              </svg>
              <!-- 眼睛（隐藏） -->
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24" />
                <path d="M1 1l22 22" />
              </svg>
            </button>
          </span>
          <span v-if="passwordError" class="auth__error">{{ passwordError }}</span>
        </label>

        <!-- 忘记密码入口（仅登录模式） -->
        <div v-if="mode === 'login'" class="auth__forgot-row">
          <button type="button" class="auth__forgot" @click="enterForgot">忘记密码？</button>
        </div>

        <label v-if="isRegister" class="auth__field">
          <span class="auth__label">确认密码</span>
          <span class="auth__input-wrap">
            <i class="auth__field-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                <path d="M9 12l2 2 4-4" />
              </svg>
            </i>
            <input
              v-model="confirmPassword"
              type="password"
              class="auth__input"
              :class="{ 'is-error': passwordMismatch }"
              placeholder="请再次输入密码"
              autocomplete="new-password"
            />
          </span>
          <span v-if="passwordMismatch" class="auth__error">两次密码不一致，请重新输入</span>
        </label>

        <p v-if="isRegister" class="auth__hint">注册即代表同意《用户协议》与《隐私政策》</p>

        <!-- 提交统一走 form 的 submit（RedButton 原生 button 默认 type=submit），避免 click + submit 双重触发 -->
        <RedButton
          size="large"
          :loading="loading"
          :disabled="!canSubmit"
          class="auth__submit"
        >
          {{ mode === 'forgot' ? (forgotStep === 1 ? '下一步' : '重置密码') : (isRegister ? '注册' : '登录') }}
        </RedButton>

        <!-- 接口失败原因（后端 message，如"用户名或密码错误"） -->
        <p v-if="submitError" class="auth__error auth__error--submit">{{ submitError }}</p>
      </form>

      <!-- 重置成功提示 + 辅助链接 -->
      <p v-if="mode === 'login' && resetSuccess" class="auth__hint auth__hint--ok">✓ 密码已重置，请使用新密码登录</p>
      <div class="auth__footer">
        <span v-if="mode === 'forgot'" class="auth__link" @click="backToLogin">
          想起来了？返回登录
        </span>
        <span v-else-if="!isRegister" class="auth__link" @click="selectMode('register')">
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
  // 页面底部一抹暖红光晕，避免下半屏过空
  background:
    radial-gradient(120% 50% at 50% 100%, rgba(196, 30, 58, 0.07) 0%, transparent 65%),
    @color-bg-page;
  overflow: hidden;
  -webkit-tap-highlight-color: transparent;

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
    position: relative;
    overflow: hidden;
    padding: 46px @spacing-lg 60px;
    background:
      radial-gradient(90% 120% at 85% -10%, rgba(255, 122, 89, 0.35) 0%, transparent 55%),
      radial-gradient(120% 140% at 0% 110%, rgba(122, 15, 26, 0.5) 0%, transparent 60%),
      linear-gradient(150deg, #e0333f 0%, @color-primary 45%, @color-primary-active 100%);
    color: #fff;
    text-align: center;
    border-radius: 0 0 @radius-lg @radius-lg;
  }

  // 远山剪影
  &__hills {
    position: absolute;
    left: 0;
    right: 0;
    bottom: -1px;
    width: 100%;
    height: 64px;
    pointer-events: none;
  }

  // 装饰光斑：柔化大面积纯红
  &__spot {
    position: absolute;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(255, 255, 255, 0.16) 0%, transparent 70%);
    pointer-events: none;

    &--1 {
      width: 220px;
      height: 220px;
      top: -80px;
      left: -60px;
      animation: auth-float 7s ease-in-out infinite alternate;
    }
    &--2 {
      width: 160px;
      height: 160px;
      right: -40px;
      top: 20px;
      animation: auth-float 9s ease-in-out infinite alternate-reverse;
    }
    &--3 {
      width: 120px;
      height: 120px;
      bottom: -50px;
      left: 30%;
      background: radial-gradient(circle, rgba(212, 175, 55, 0.22) 0%, transparent 70%);
      animation: auth-float 8s ease-in-out infinite alternate;
    }
  }
  &__logo {
    position: relative;
    z-index: 1;
    width: 60px;
    height: 60px;
    margin: 0 auto @spacing-sm;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: linear-gradient(160deg, rgba(255, 255, 255, 0.22), rgba(255, 255, 255, 0.1));
    font-size: 30px;
    color: #f5d073;
    text-shadow: 0 0 14px rgba(245, 208, 115, 0.85);
    box-shadow: 0 6px 22px rgba(0, 0, 0, 0.18);
    animation: auth-rise 0.5s ease-out both;

    // 呼吸光环
    &::after {
      content: '';
      position: absolute;
      inset: -6px;
      border-radius: 50%;
      border: 1.5px solid rgba(245, 208, 115, 0.55);
      animation: auth-ring 2.8s ease-out 0.8s infinite;
      pointer-events: none;
    }
  }
  &__brand {
    position: relative;
    z-index: 1;
    display: inline-block;
    margin-bottom: @spacing-sm;
    padding: 3px 14px;
    border: 1px solid rgba(255, 255, 255, 0.45);
    border-radius: 20px;
    font-family: 'Kaiti SC', 'STKaiti', 'KaiTi', 'SimSun', serif;
    font-size: @font-size-sm;
    letter-spacing: 4px;
    text-indent: 4px; // 抵消末字间距，保证视觉居中
    color: rgba(255, 255, 255, 0.92);
    animation: auth-rise 0.5s ease-out 0.04s both;
  }
  &__title {
    position: relative;
    z-index: 1;
    font-family: 'Kaiti SC', 'STKaiti', 'KaiTi', 'SimSun', serif;
    font-size: 32px;
    font-weight: 400; // 楷体无粗字重，700 会仿真加粗发虚，用常规字重保持笔画干净
    letter-spacing: 8px;
    text-indent: 8px;
    margin-bottom: @spacing-xs;
    text-shadow: 0 2px 12px rgba(0, 0, 0, 0.18);
    animation: auth-rise 0.5s ease-out 0.1s both;
  }
  &__subtitle {
    position: relative;
    z-index: 1;
    font-family: 'Kaiti SC', 'STKaiti', 'KaiTi', 'SimSun', serif;
    font-size: @font-size-base;
    letter-spacing: 3px;
    text-indent: 3px;
    opacity: 0.95;
    animation: auth-rise 0.5s ease-out 0.16s both;
  }

  &__card {
    margin: -@spacing-xl @spacing-md 0;
    padding: @spacing-lg @spacing-md;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: @shadow-card;
    animation: auth-rise 0.55s cubic-bezier(0.22, 0.8, 0.36, 1) 0.2s both;
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

    // 校验失败：整体抖动提示
    &.is-shake {
      animation: auth-shake 0.4s cubic-bezier(0.36, 0.07, 0.19, 0.97);
    }
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
    flex: 1;
    min-width: 0;
    height: 44px;
    padding: 0 @spacing-md;
    border: 1px solid @color-border;
    border-radius: @radius-base;
    background: @color-bg-card;
    font-size: @font-size-base;
    color: @color-text-primary;
    transition: border-color 0.2s, box-shadow 0.2s;
    &::placeholder {
      color: @color-text-secondary;
    }
    &:focus {
      border-color: @color-primary;
      box-shadow: 0 0 0 3px fade(@color-primary, 12%);
      outline: none;
    }
    &.is-error {
      border-color: @color-danger;
    }
  }

  // 忘记密码：步骤指示
  &__steps {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: @spacing-sm;
    margin-bottom: @spacing-md;
  }
  &__step {
    font-size: @font-size-sm;
    color: @color-text-secondary;

    &.is-active {
      color: @color-primary;
      font-weight: 600;
    }
  }
  &__steps-line {
    width: 32px;
    height: 1px;
    background: @color-border;
  }
  // 忘记密码入口
  &__forgot-row {
    display: flex;
    justify-content: flex-end;
    margin-top: -@spacing-xs;
    margin-bottom: @spacing-sm;
  }
  &__forgot {
    border: none;
    background: transparent;
    padding: 2px;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    cursor: pointer;

    &:active {
      color: @color-primary;
    }
  }
  // 重置成功提示
  &__hint--ok {
    color: @color-success;
    opacity: 1;
    text-align: center;
  }

  // 验证码发送按钮（内嵌在输入框右侧）
  &__send {
    flex-shrink: 0;
    margin-left: 8px;
    border: none;
    background: @color-primary-light;
    color: @color-primary;
    font-size: @font-size-sm;
    font-weight: 600;
    padding: 8px 12px;
    border-radius: 14px;
    cursor: pointer;
    white-space: nowrap;

    &:disabled {
      opacity: 0.55;
      cursor: not-allowed;
    }
    &:not(:disabled):active {
      background: rgba(196, 30, 58, 0.18);
    }
  }
  &__hint {
    margin-top: @spacing-xs;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    opacity: 0.85;
  }

  // 密码输入容器（输入框 + 可见性切换）
  &__input-wrap {
    position: relative;
    display: flex;
    align-items: center;
    width: 100%;

    .auth__input {
      flex: 1;
      // 左侧前导图标、右侧留出眼睛按钮位置
      padding-left: 42px;
      padding-right: 44px;
    }
  }

  &__field-icon {
    position: absolute;
    left: 14px;
    top: 50%;
    transform: translateY(-50%);
    width: 18px;
    height: 18px;
    color: @color-text-secondary;
    pointer-events: none;

    svg {
      width: 100%;
      height: 100%;
    }
  }

  &__eye {
    position: absolute;
    right: 5px;
    top: 50%;
    transform: translateY(-50%);
    width: 36px;
    height: 36px;
    border: none;
    border-radius: 50%;
    background: transparent;
    color: @color-text-secondary;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;

    svg {
      width: 20px;
      height: 20px;
    }

    &:active {
      color: @color-primary;
      background: rgba(0, 0, 0, 0.05);
    }
  }

  &__hint {
    margin: -@spacing-xs 0 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    text-align: center;
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

// ===== 动画 =====
@keyframes auth-rise {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes auth-float {
  from {
    transform: translate(0, 0);
  }
  to {
    transform: translate(24px, 14px);
  }
}

@keyframes auth-shake {
  10%, 90% {
    transform: translateX(-2px);
  }
  20%, 80% {
    transform: translateX(4px);
  }
  30%, 50%, 70% {
    transform: translateX(-6px);
  }
  40%, 60% {
    transform: translateX(6px);
  }
}

@keyframes auth-ring {
  0% {
    transform: scale(0.92);
    opacity: 0.9;
  }
  70% {
    transform: scale(1.28);
    opacity: 0;
  }
  100% {
    transform: scale(1.28);
    opacity: 0;
  }
}
</style>
