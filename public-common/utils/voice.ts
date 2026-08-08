/**
 * 语音工具：基于浏览器 Web Speech API 的语音合成（TTS）
 * 用于游客端 / 触摸屏端讲解语音播放
 */

let currentUtterance: SpeechSynthesisUtterance | null = null

/** 是否支持语音合成 */
export const isVoiceSupported =
  typeof window !== 'undefined' && 'speechSynthesis' in window

/**
 * 播放语音
 * @param text 待播报文本
 * @param options 速率 / 音调 / 音量
 */
export function speakText(
  text: string,
  options: { rate?: number; pitch?: number; volume?: number } = {},
): void {
  if (!isVoiceSupported) {
    console.warn('[voice] 当前环境不支持语音合成')
    return
  }
  stopSpeak()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'zh-CN'
  utterance.rate = options.rate ?? 1
  utterance.pitch = options.pitch ?? 1
  utterance.volume = options.volume ?? 1
  currentUtterance = utterance
  window.speechSynthesis.speak(utterance)
}

/** 停止语音播放 */
export function stopSpeak(): void {
  if (isVoiceSupported) {
    window.speechSynthesis.cancel()
  }
  currentUtterance = null
}

/** 是否正在播报 */
export function isSpeaking(): boolean {
  return isVoiceSupported && window.speechSynthesis.speaking
}
