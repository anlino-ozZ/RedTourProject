/**
 * 统一音效模块（Web Audio API 合成，零音频素材依赖）
 *
 * 约定：
 * - 单例 AudioContext，懒初始化；首次播放须在用户手势链中触发（浏览器自动播放策略）
 * - 所有音效静默降级，不支持音频的环境不影响业务
 * - 只用于关键结果事件的反馈，不要给每个按钮都加音效
 */

let audioCtx: AudioContext | null = null

/** 全局音效开关读取（与设置页共用 localStorage，避免与 profileStorage 循环依赖） */
function soundEnabled(): boolean {
  try {
    const raw = localStorage.getItem('red-tour:settings')
    if (!raw) return true
    return (JSON.parse(raw) as { soundEnabled?: boolean }).soundEnabled !== false
  } catch {
    return true
  }
}

function getAudioCtx(): AudioContext | null {
  if (!soundEnabled()) return null
  try {
    const Ctx =
      window.AudioContext ??
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Ctx) return null
    if (!audioCtx) audioCtx = new Ctx()
    // 首次播放发生在用户手势中，恢复可能被挂起的上下文
    if (audioCtx.state === 'suspended') void audioCtx.resume()
    return audioCtx
  } catch {
    return null
  }
}

/** 播放单个带淡入淡出的振荡器音符 */
function playTone(
  ctx: AudioContext,
  opts: { freq: number; start: number; dur?: number; peak?: number; type?: OscillatorType },
) {
  const { freq, start, dur = 0.2, peak = 0.22, type = 'sine' } = opts
  const osc = ctx.createOscillator()
  const gain = ctx.createGain()
  osc.type = type
  osc.frequency.setValueAtTime(freq, start)
  gain.gain.setValueAtTime(0.0001, start)
  gain.gain.linearRampToValueAtTime(peak, start + 0.015)
  gain.gain.exponentialRampToValueAtTime(0.001, start + dur)
  osc.connect(gain)
  gain.connect(ctx.destination)
  osc.start(start)
  osc.stop(start + dur + 0.02)
}

/** 轻触：极短促的柔和点击音（关键按钮/选项点选） */
export function playTapSound() {
  const ctx = getAudioCtx()
  if (!ctx) return
  playTone(ctx, { freq: 660, start: ctx.currentTime, dur: 0.07, peak: 0.1 })
}

/** 答对/提交成功：明亮上行三音琶音 C5-E5-G5 */
export function playCorrectSound() {
  const ctx = getAudioCtx()
  if (!ctx) return
  const now = ctx.currentTime
  ;[523.25, 659.25, 783.99].forEach((freq, i) =>
    playTone(ctx, { freq, start: now + i * 0.09, dur: 0.18, peak: 0.22 }),
  )
}

/** 答错/校验失败：A3→Eb3 低沉下行双音 */
export function playWrongSound() {
  const ctx = getAudioCtx()
  if (!ctx) return
  const now = ctx.currentTime
  playTone(ctx, { freq: 220, start: now, dur: 0.18, peak: 0.18, type: 'triangle' })
  playTone(ctx, { freq: 155.56, start: now + 0.14, dur: 0.3, peak: 0.18, type: 'triangle' })
}

/**
 * 积分/到达等正向结果：两音上行 A5→E6"叮咚"
 * @param delaySec 与前一个音效错峰的延迟（避免叠音浑浊）
 */
export function playScoreSound(delaySec = 0) {
  const ctx = getAudioCtx()
  if (!ctx) return
  const now = ctx.currentTime + delaySec
  ;[880, 1318.5].forEach((freq, i) =>
    playTone(ctx, { freq, start: now + i * 0.09, dur: 0.2, peak: 0.25 }),
  )
}

/** 通关：上行四音号角 C5-E5-G5-C6，尾音延长 */
export function playFanfareSound() {
  const ctx = getAudioCtx()
  if (!ctx) return
  const now = ctx.currentTime
  ;[523.25, 659.25, 783.99, 1046.5].forEach((freq, i) =>
    playTone(ctx, { freq, start: now + i * 0.11, dur: i === 3 ? 0.5 : 0.2, peak: 0.22 }),
  )
}

/** 徽章/稀有解锁：高音区闪耀琶音 C6-E6-G6-C7 */
export function playBadgeShineSound() {
  const ctx = getAudioCtx()
  if (!ctx) return
  const now = ctx.currentTime
  ;[1046.5, 1318.5, 1568, 2093].forEach((freq, i) =>
    playTone(ctx, { freq, start: now + i * 0.1, dur: i === 3 ? 0.6 : 0.25, peak: 0.2 }),
  )
}

/** 未解锁/禁用：低柔闷音双响 */
export function playLockedSound() {
  const ctx = getAudioCtx()
  if (!ctx) return
  const now = ctx.currentTime
  playTone(ctx, { freq: 196, start: now, dur: 0.16, peak: 0.12, type: 'triangle' })
  playTone(ctx, { freq: 174.6, start: now + 0.13, dur: 0.22, peak: 0.1, type: 'triangle' })
}
