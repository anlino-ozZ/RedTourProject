<script setup lang="ts">
// 姿态成就扫码领取页（W-T-07）
// 触摸屏全屏特效右下角二维码落地页：/claim?token=xxx
// token 为 5 分钟有效临时凭证（后端签发存 Redis），校验通过后幂等写入 user_achievement
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { AchievementClaimResult, PoseClaimTokenPayload } from '@red-tour-project/common'
import { claimAchievement, login, register } from '@/api'

type Phase = 'loading' | 'invalid' | 'expired' | 'auth' | 'claiming' | 'success' | 'error'

const ACTION_LABELS: Record<string, string> = {
  salute: '敬礼致敬',
  mill: '红嫂推磨',
  wave: '挥手致意',
}

const route = useRoute()
const phase = ref<Phase>('loading')
const token = ref('')
const payload = ref<PoseClaimTokenPayload | null>(null)
const claimResult = ref<AchievementClaimResult | null>(null)
const errorMsg = ref('')

// 登录表单
const authForm = reactive({ username: '', password: '' })
const authHint = ref('')
const submitting = ref(false)
const showLoginForm = ref(false)

const actionLabel = computed(() => {
  const a = payload.value?.action ?? ''
  return ACTION_LABELS[a] ?? '姿态互动'
})
const unlockedAtText = computed(() => {
  if (!claimResult.value?.unlockedAt) return ''
  const d = new Date(claimResult.value.unlockedAt)
  return Number.isNaN(d.getTime())
    ? claimResult.value.unlockedAt
    : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
        d.getDate(),
      ).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(
        d.getMinutes(),
      ).padStart(2, '0')}`
})

function base64urlDecode(text: string): unknown {
  const b64 = text.replace(/-/g, '+').replace(/_/g, '/').padEnd(
    text.length + ((4 - (text.length % 4)) % 4),
    '=',
  )
  return JSON.parse(decodeURIComponent(escape(atob(b64))))
}

function saveToken(user: { token?: string }) {
  if (user.token) localStorage.setItem('rt_token', user.token)
}

async function doClaim() {
  phase.value = 'claiming'
  errorMsg.value = ''
  try {
    claimResult.value = await claimAchievement(token.value)
    phase.value = 'success'
  } catch (e) {
    const err = e as { response?: { status?: number }; message?: string }
    if (err.response?.status === 401) {
      // 未登录 / 登录失效：展示登录引导
      localStorage.removeItem('rt_token')
      authHint.value = '请先登录或一键领取后再试'
      phase.value = 'auth'
    } else {
      errorMsg.value = err.message || '领取失败，请稍后重试'
      phase.value = 'error'
    }
  }
}

// 一键游客：自动注册临时游客账号并领取
async function guestClaim() {
  if (submitting.value) return
  submitting.value = true
  authHint.value = ''
  const rand = Math.random().toString(36).slice(2, 8)
  try {
    const user = await register({
      username: `tourist_${Date.now().toString(36)}${rand}`.slice(0, 20),
      password: 'Tourist2026',
      nickname: `游客${rand}`,
    })
    saveToken(user)
    await doClaim()
  } catch (e) {
    authHint.value = (e as { message?: string }).message || '游客登录失败，请重试'
  } finally {
    submitting.value = false
  }
}

async function accountLogin() {
  if (submitting.value) return
  if (!authForm.username || !authForm.password) {
    authHint.value = '请输入账号和密码'
    return
  }
  submitting.value = true
  authHint.value = ''
  try {
    const user = await login({ username: authForm.username.trim(), password: authForm.password })
    saveToken(user)
    await doClaim()
  } catch (e) {
    authHint.value = (e as { message?: string }).message || '登录失败，请检查账号密码'
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  const raw = route.query.token
  if (typeof raw !== 'string' || !raw) {
    phase.value = 'invalid'
    return
  }
  token.value = raw
  try {
    const obj = base64urlDecode(raw) as PoseClaimTokenPayload
    if (!obj || typeof obj.exp !== 'number' || typeof obj.jti !== 'string') {
      phase.value = 'invalid'
      return
    }
    payload.value = obj
    if (Date.now() > obj.exp) {
      phase.value = 'expired'
      return
    }
  } catch {
    phase.value = 'invalid'
    return
  }

  if (localStorage.getItem('rt_token')) {
    void doClaim()
  } else {
    phase.value = 'auth'
  }
})
</script>

<template>
  <div class="claim-page">
    <!-- 加载中 -->
    <div v-if="phase === 'loading' || phase === 'claiming'" class="claim-card">
      <div class="claim-spinner" />
      <p class="claim-tip">{{ phase === 'claiming' ? '正在领取成就…' : '正在识别二维码…' }}</p>
    </div>

    <!-- 无效链接 -->
    <div v-else-if="phase === 'invalid'" class="claim-card">
      <div class="claim-icon claim-icon--warn">!</div>
      <h1 class="claim-title">无效的领取链接</h1>
      <p class="claim-tip">请在景区触摸屏前重新完成动作，扫描最新的二维码</p>
    </div>

    <!-- 已过期 -->
    <div v-else-if="phase === 'expired'" class="claim-card">
      <div class="claim-icon claim-icon--warn">!</div>
      <h1 class="claim-title">二维码已过期</h1>
      <p class="claim-tip">
        该二维码仅 5 分钟内有效，请回到触摸屏重新做出
        <b>「{{ actionLabel }}」</b>
        动作后再次扫码
      </p>
    </div>

    <!-- 登录引导 -->
    <div v-else-if="phase === 'auth'" class="claim-card">
      <div class="claim-medal">★</div>
      <h1 class="claim-title">领取「{{ actionLabel }}」成就</h1>
      <p class="claim-tip">登录后成就将保存到您的账号</p>

      <button class="claim-primary" type="button" :disabled="submitting" @click="guestClaim">
        {{ submitting ? '请稍候…' : '一键游客领取' }}
      </button>

      <button class="claim-link" type="button" @click="showLoginForm = !showLoginForm">
        {{ showLoginForm ? '收起账号登录' : '已有账号？账号登录' }}
      </button>

      <form v-if="showLoginForm" class="claim-form" @submit.prevent="accountLogin">
        <input
          v-model="authForm.username"
          class="claim-input"
          type="text"
          placeholder="用户名"
          autocomplete="username"
        />
        <input
          v-model="authForm.password"
          class="claim-input"
          type="password"
          placeholder="密码"
          autocomplete="current-password"
        />
        <button class="claim-primary" type="submit" :disabled="submitting">
          {{ submitting ? '登录中…' : '登录并领取' }}
        </button>
      </form>

      <p v-if="authHint" class="claim-hint">{{ authHint }}</p>
    </div>

    <!-- 领取成功 -->
    <div v-else-if="phase === 'success' && claimResult" class="claim-card">
      <div class="claim-medal claim-medal--lit">★</div>
      <h1 class="claim-title">{{ claimResult.achievement.title }}</h1>
      <p class="claim-tip">
        {{ claimResult.firstUnlock ? '恭喜！成就已解锁并保存到您的账号' : '您已解锁过该成就，无需重复领取' }}
      </p>
      <div class="claim-meta">
        <span>成就编号：#{{ claimResult.achievement.id }}</span>
        <span>解锁时间：{{ unlockedAtText }}</span>
      </div>
    </div>

    <!-- 接口异常 -->
    <div v-else class="claim-card">
      <div class="claim-icon claim-icon--warn">!</div>
      <h1 class="claim-title">领取失败</h1>
      <p class="claim-tip">{{ errorMsg || '网络开小差了，请稍后重试' }}</p>
      <button class="claim-primary" type="button" @click="doClaim">重新领取</button>
    </div>
  </div>
</template>

<style lang="less" scoped>
@red: #c41e3a;
@red-dark: #9e1530;
@gold: #d9a83b;

.claim-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at 50% 20%, rgba(196, 30, 58, 0.35) 0%, rgba(12, 8, 8, 0.96) 65%),
    #0c0808;
}

.claim-card {
  width: 100%;
  max-width: 420px;
  padding: 40px 32px 36px;
  border-radius: 20px;
  border: 1px solid rgba(217, 168, 59, 0.4);
  background: linear-gradient(180deg, rgba(40, 18, 20, 0.92) 0%, rgba(20, 12, 12, 0.95) 100%);
  box-shadow: 0 16px 60px rgba(0, 0, 0, 0.55);
  text-align: center;
  color: #fff;
}

.claim-medal {
  width: 88px;
  height: 88px;
  margin: 0 auto 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44px;
  color: rgba(217, 168, 59, 0.55);
  border: 2px solid rgba(217, 168, 59, 0.5);
  background: rgba(217, 168, 59, 0.08);

  &--lit {
    color: #fff3c4;
    border-color: @gold;
    background: radial-gradient(circle, rgba(217, 168, 59, 0.55) 0%, rgba(196, 30, 58, 0.25) 75%);
    box-shadow:
      0 0 32px rgba(217, 168, 59, 0.55),
      inset 0 0 18px rgba(255, 240, 180, 0.4);
    animation: medal-pop 0.6s ease-out;
  }
}

@keyframes medal-pop {
  0% { transform: scale(0.4); opacity: 0; }
  70% { transform: scale(1.12); }
  100% { transform: scale(1); opacity: 1; }
}

.claim-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  font-weight: 700;

  &--warn {
    color: #ffb84d;
    border: 2px solid rgba(255, 184, 77, 0.6);
    background: rgba(255, 184, 77, 0.1);
  }
}

.claim-title {
  margin: 0 0 12px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
}

.claim-tip {
  margin: 0 0 24px;
  font-size: 15px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.72);

  b {
    color: @gold;
  }
}

.claim-primary {
  width: 100%;
  padding: 14px 0;
  border: none;
  border-radius: 999px;
  background: linear-gradient(90deg, @red 0%, @red-dark 100%);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 3px;
  cursor: pointer;
  box-shadow: 0 6px 20px rgba(196, 30, 58, 0.4);
  transition: transform 0.15s ease, opacity 0.15s ease;

  &:active:not(:disabled) {
    transform: scale(0.98);
  }
  &:disabled {
    opacity: 0.6;
    cursor: wait;
  }
}

.claim-link {
  margin-top: 14px;
  padding: 4px 8px;
  border: none;
  background: none;
  color: rgba(217, 168, 59, 0.85);
  font-size: 14px;
  cursor: pointer;
}

.claim-form {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.claim-input {
  width: 100%;
  padding: 13px 16px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(0, 0, 0, 0.3);
  color: #fff;
  font-size: 15px;
  outline: none;
  box-sizing: border-box;

  &::placeholder {
    color: rgba(255, 255, 255, 0.4);
  }
  &:focus {
    border-color: rgba(217, 168, 59, 0.7);
  }
}

.claim-hint {
  margin: 14px 0 0;
  font-size: 13px;
  color: #ff9b9b;
}

.claim-meta {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.55);
}

.claim-spinner {
  width: 44px;
  height: 44px;
  margin: 0 auto 18px;
  border-radius: 50%;
  border: 3px solid rgba(217, 168, 59, 0.25);
  border-top-color: @gold;
  animation: claim-spin 0.9s linear infinite;
}

@keyframes claim-spin {
  to { transform: rotate(360deg); }
}
</style>
