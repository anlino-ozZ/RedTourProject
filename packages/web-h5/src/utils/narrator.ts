/**
 * 语音讲解播报模块（浏览器 SpeechSynthesis 兜底）
 * - 后端 TTS 音频（audioUrl）优先，本模块仅在 audioUrl 为空或接口失败时兜底
 * - 男/女声按常见中语音色候选名匹配，匹配不到时降级为第一个中语 voice
 * - 静默降级：环境不支持语音合成时立即回调结束，不影响业务流程
 */

export type NarratorGender = 'male' | 'female'

interface SpeakHandlers {
  onStart?: () => void
  onEnd?: () => void
}

export function isSpeechSupported(): boolean {
  return typeof window !== 'undefined' && 'speechSynthesis' in window
}

/** 播音语速：略慢于常速，吐字更清晰 */
const NARRATOR_RATE = 0.95

/** 男/女声音色候选名（Windows/Android/Edge 常见中语 voice） */
const VOICE_CANDIDATES: Record<NarratorGender, RegExp> = {
  male: /yunjian|yunyang|yunxi|yunze|kangkang|danny|liang/i,
  female: /xiaoxiao|xiaoyi|xiaohan|huihui|yaoyao|tingting|ting-ting|meijia/i,
}

function listZhVoices(): SpeechSynthesisVoice[] {
  return window.speechSynthesis
    .getVoices()
    .filter((v) => /^zh\b|zh[-_]CN|zh[-_]Hans|Chinese/i.test(v.lang) || /中文|普通话/.test(v.name))
}

function pickVoice(gender: NarratorGender): SpeechSynthesisVoice | null {
  const voices = listZhVoices()
  if (voices.length === 0) return null
  const matched = voices.find((v) => VOICE_CANDIDATES[gender].test(v.name))
  return matched ?? voices[0]
}

/**
 * 语音播报一段文本
 * @returns 是否成功开始播报（不支持/无可用音色返回 false）
 */
export function speak(
  text: string,
  gender: NarratorGender,
  handlers: SpeakHandlers = {},
): boolean {
  if (!isSpeechSupported() || !text) {
    handlers.onEnd?.()
    return false
  }
  // 先停止上一次播报，避免多段语音叠加
  window.speechSynthesis.cancel()

  const utter = new SpeechSynthesisUtterance(text)
  const voice = pickVoice(gender)
  if (voice) utter.voice = voice
  utter.lang = 'zh-CN'
  utter.rate = NARRATOR_RATE
  // 男声略低沉、女声明亮，增强"播音员"听感
  utter.pitch = gender === 'male' ? 0.9 : 1.05
  utter.onstart = () => handlers.onStart?.()
  utter.onend = () => handlers.onEnd?.()
  utter.onerror = () => handlers.onEnd?.()

  // Chrome 首次调用 voices 列表可能为空，监听加载完成后重取由 utter.voice 缺省兜底
  window.speechSynthesis.speak(utter)
  return true
}

/** 停止播报 */
export function stopSpeak(): void {
  if (isSpeechSupported()) window.speechSynthesis.cancel()
}
