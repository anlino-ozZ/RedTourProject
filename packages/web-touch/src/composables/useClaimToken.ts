// 姿态成就领取临时 token（W-T-07）
// 动作达标后由触摸屏签发 5 分钟有效临时 token，编码进 H5 扫码 URL，
// 游客 H5 端凭 token 调 POST /achievements/claim 写入 user_achievement.unlocked_at。
//
// 当前后端签发接口未就绪：前端按契约本地生成 payload（base64url，非防篡改签名），
// H5 仅用 exp 做过期预判，最终有效性/幂等以后端 claim 接口校验为准。
// 后端就绪后只需把 issue() 内部替换为 POST /pose/claim-token（Redis 存 jti，TTL 5 分钟），
// payload 结构与 H5 解码逻辑保持不变。
import { computed, onBeforeUnmount, ref } from 'vue'
import type { PoseClaimTokenPayload } from '@red-tour-project/common'

/** token 有效期：5 分钟 */
export const CLAIM_TOKEN_TTL_MS = 5 * 60 * 1000

function base64urlEncode(input: unknown): string {
  const json = JSON.stringify(input)
  // unescape(encodeURIComponent()) 保证中文等非 ASCII 字符安全进入 btoa
  const b64 = btoa(unescape(encodeURIComponent(json)))
  return b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function randomJti(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

export function useClaimToken() {
  const payload = ref<PoseClaimTokenPayload | null>(null)
  /** 供 qrcode 渲染的完整 H5 落地 URL */
  const claimUrl = ref('')
  /** 剩余有效毫秒 */
  const remainingMs = ref(0)

  let tickTimer: ReturnType<typeof setInterval> | null = null

  const expired = computed(() => remainingMs.value <= 0)
  /** 倒计时文本 mm:ss */
  const countdown = computed(() => {
    const total = Math.ceil(remainingMs.value / 1000)
    const m = Math.floor(total / 60)
    const s = total % 60
    return `${m}:${String(s).padStart(2, '0')}`
  })

  function stopTicker() {
    if (tickTimer) {
      clearInterval(tickTimer)
      tickTimer = null
    }
  }

  function startTicker() {
    stopTicker()
    tickTimer = setInterval(() => {
      if (!payload.value) {
        remainingMs.value = 0
        stopTicker()
        return
      }
      remainingMs.value = Math.max(0, payload.value.exp - Date.now())
      if (remainingMs.value === 0) stopTicker()
    }, 500)
  }

  /**
   * 签发一枚新的领取 token 并生成扫码 URL
   * 后端接口就绪后改为：const { data } = await request.post('/pose/claim-token', { action })
   */
  function issue(action: string) {
    const now = Date.now()
    const next: PoseClaimTokenPayload = {
      jti: randomJti(),
      action,
      scenicAreaId: Number(import.meta.env.VITE_TOUCH_SCENIC_AREA_ID) || 1,
      deviceId: import.meta.env.VITE_TOUCH_DEVICE_NO || 'RT-TS-05',
      iat: now,
      exp: now + CLAIM_TOKEN_TTL_MS,
    }
    payload.value = next
    const h5Base = (import.meta.env.VITE_H5_BASE_URL || window.location.origin).replace(/\/+$/, '')
    claimUrl.value = `${h5Base}/claim?token=${base64urlEncode(next)}`
    remainingMs.value = CLAIM_TOKEN_TTL_MS
    startTicker()
  }

  function clear() {
    stopTicker()
    payload.value = null
    claimUrl.value = ''
    remainingMs.value = 0
  }

  onBeforeUnmount(clear)

  return { payload, claimUrl, remainingMs, countdown, expired, issue, clear }
}
