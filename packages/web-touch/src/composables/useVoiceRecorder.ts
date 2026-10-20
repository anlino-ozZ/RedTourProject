// 按住说话录音 + 结束静音自动停止（W-T-08，对接 A-11 /voice/stt）
// - MediaRecorder 采集（优先 webm/opus），Web Audio AnalyserNode 实时算 RMS 音量
// - 检测到说话后，尾部静音连续 1.5s 自动停止；也可松手立即停止
// - 最长 30s 兜底自动停止，防止异常长录音
import { onBeforeUnmount, ref } from 'vue'

export type RecorderState = 'idle' | 'requesting' | 'recording'
export type StopReason = 'manual' | 'silence' | 'maxduration'
export type RecorderErrorCode =
  | 'unsupported'
  | 'permission-denied'
  | 'device-busy'
  | 'no-device'
  | 'unknown'

export interface VoiceRecordResult {
  blob: Blob
  durationMs: number
  mimeType: string
  /** 录音过程中是否检测到有效人声（松手过快/环境静音时为 false） */
  speechDetected: boolean
  reason: StopReason
}

interface UseVoiceRecorderOptions {
  /** 录音正常结束（手动/静音/超时统一入口，仅回调一次） */
  onStop?: (r: VoiceRecordResult) => void
  /** 录音启动或采集异常 */
  onError?: (code: RecorderErrorCode, message: string) => void
  /** 尾部静音判定时长（毫秒） */
  silenceTimeout?: number
  /** 最长录音时长（毫秒） */
  maxDuration?: number
}

/** 人声 RMS 门限（0-1，时域 8bit 振幅均方根） */
const VOICE_RMS_MIN = 0.012
/** 环境底噪采样窗口（启动后前 400ms） */
const NOISE_SAMPLE_MS = 400
const VAD_TICK_MS = 80

// 浏览器 MediaRecorder 兼容性候选（Chrome/Edge webm，Safari mp4）
const MIME_CANDIDATES = [
  'audio/webm;codecs=opus',
  'audio/webm',
  'audio/mp4;codecs=mp4a.40.2',
  'audio/mp4',
]

export function useVoiceRecorder(opts: UseVoiceRecorderOptions = {}) {
  const silenceTimeout = opts.silenceTimeout ?? 1500
  const maxDuration = opts.maxDuration ?? 30000

  const supported =
    typeof navigator !== 'undefined' &&
    !!navigator.mediaDevices?.getUserMedia &&
    typeof MediaRecorder !== 'undefined'

  const state = ref<RecorderState>('idle')
  /** 实时音量 0-1（驱动 UI 声波） */
  const volume = ref(0)
  /** 是否已检测到人声（区分"正在聆听"与"正在录音"） */
  const speechDetected = ref(false)
  /** 录音秒数 */
  const elapsedMs = ref(0)
  const errorCode = ref<RecorderErrorCode | null>(null)

  let stream: MediaStream | null = null
  let recorder: MediaRecorder | null = null
  let audioCtx: AudioContext | null = null
  let chunks: Blob[] = []
  let vadTimer: ReturnType<typeof setInterval> | null = null
  let uiTimer: ReturnType<typeof setInterval> | null = null
  let startedAt = 0
  let lastVoiceAt = 0
  let noiseFloor = 0
  let noiseSamples = 0
  let noiseSum = 0
  let finalized = false
  let activeMime = ''

  function pickMimeType(): string {
    for (const mime of MIME_CANDIDATES) {
      if (MediaRecorder.isTypeSupported?.(mime)) return mime
    }
    return ''
  }

  function rmsFromAnalyser(analyser: AnalyserNode): number {
    const buf = new Uint8Array(analyser.fftSize)
    analyser.getByteTimeDomainData(buf)
    let sum = 0
    for (let i = 0; i < buf.length; i++) {
      const v = (buf[i] - 128) / 128
      sum += v * v
    }
    return Math.sqrt(sum / buf.length)
  }

  function cleanupStream() {
    stream?.getTracks().forEach((t) => t.stop())
    stream = null
    if (audioCtx) {
      void audioCtx.close().catch(() => {})
      audioCtx = null
    }
  }

  function fail(code: RecorderErrorCode, message: string) {
    errorCode.value = code
    state.value = 'idle'
    cleanupStream()
    opts.onError?.(code, message)
  }

  function mapMediaError(err: unknown): { code: RecorderErrorCode; message: string } {
    const name = err instanceof DOMException ? err.name : ''
    if (name === 'NotAllowedError' || name === 'SecurityError') {
      return { code: 'permission-denied', message: '麦克风权限被拒绝，请在系统设置中允许后重试' }
    }
    if (name === 'NotFoundError' || name === 'OverconstrainedError') {
      return { code: 'no-device', message: '未检测到麦克风设备' }
    }
    if (name === 'NotReadableError' || name === 'AbortError') {
      return { code: 'device-busy', message: '麦克风被占用，请关闭其他录音程序后重试' }
    }
    return { code: 'unknown', message: '无法启动麦克风，请稍后重试' }
  }

  async function start() {
    if (!supported) {
      fail('unsupported', '当前浏览器不支持语音录音')
      return
    }
    if (state.value !== 'idle') return

    errorCode.value = null
    volume.value = 0
    speechDetected.value = false
    elapsedMs.value = 0
    chunks = []
    finalized = false
    state.value = 'requesting'

    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          channelCount: 1,
        },
      })
    } catch (err) {
      const mapped = mapMediaError(err)
      fail(mapped.code, mapped.message)
      return
    }

    // 音量分析链路（不影响录音编码）
    try {
      audioCtx = new AudioContext()
      const source = audioCtx.createMediaStreamSource(stream)
      const analyser = audioCtx.createAnalyser()
      analyser.fftSize = 512
      analyser.smoothingTimeConstant = 0.3
      source.connect(analyser)

      activeMime = pickMimeType()
      recorder = activeMime ? new MediaRecorder(stream, { mimeType: activeMime }) : new MediaRecorder(stream)
      recorder.ondataavailable = (e: BlobEvent) => {
        if (e.data.size > 0) chunks.push(e.data)
      }

      startedAt = Date.now()
      lastVoiceAt = 0
      noiseFloor = 0
      noiseSamples = 0
      noiseSum = 0

      recorder.start(200)
      state.value = 'recording'
      startTimers(analyser)
    } catch {
      fail('unknown', '录音初始化失败，请稍后重试')
    }
  }

  function startTimers(analyser: AnalyserNode) {
    vadTimer = setInterval(() => {
      const rms = rmsFromAnalyser(analyser)
      // UI 音量做轻度放大，便于观察
      volume.value = Math.min(1, rms * 3.2)

      const now = Date.now()
      elapsedMs.value = now - startedAt

      // 前 400ms 采集环境底噪，动态门限避免展厅空调声误触发
      if (now - startedAt < NOISE_SAMPLE_MS) {
        noiseSum += rms
        noiseSamples += 1
        noiseFloor = noiseSum / noiseSamples
        return
      }
      const threshold = Math.max(VOICE_RMS_MIN, noiseFloor * 2.5 + 0.008)

      if (rms >= threshold) {
        if (!speechDetected.value) speechDetected.value = true
        lastVoiceAt = now
        return
      }

      // 说过话之后：尾部静音满 1.5s 自动停止（未说话时持续聆听，不触发）
      if (speechDetected.value && lastVoiceAt && now - lastVoiceAt >= silenceTimeout) {
        void finalize('silence')
      } else if (elapsedMs.value >= maxDuration) {
        void finalize('maxduration')
      }
    }, VAD_TICK_MS)

    // 声波 UI 在无语音时也保持轻微回落动画
    uiTimer = setInterval(() => {
      if (state.value === 'recording') elapsedMs.value = Date.now() - startedAt
    }, 200)
  }

  function buildResult(reason: StopReason): VoiceRecordResult {
    const mime = recorder?.mimeType || activeMime || 'audio/webm'
    return {
      blob: new Blob(chunks, { type: mime }),
      durationMs: Date.now() - startedAt,
      mimeType: mime,
      speechDetected: speechDetected.value,
      reason,
    }
  }

  async function finalize(reason: StopReason): Promise<VoiceRecordResult | null> {
    if (finalized || state.value !== 'recording') return null
    finalized = true

    if (vadTimer) {
      clearInterval(vadTimer)
      vadTimer = null
    }
    if (uiTimer) {
      clearInterval(uiTimer)
      uiTimer = null
    }
    volume.value = 0

    const result = buildResult(reason)

    await new Promise<void>((resolve) => {
      if (!recorder || recorder.state === 'inactive') {
        resolve()
        return
      }
      recorder.onstop = () => resolve()
      try {
        recorder.stop()
      } catch {
        resolve()
      }
    })

    cleanupStream()
    state.value = 'idle'
    opts.onStop?.(result)
    return result
  }

  /** 松手立即停止 */
  function stop() {
    return finalize('manual')
  }

  onBeforeUnmount(() => {
    if (vadTimer) clearInterval(vadTimer)
    if (uiTimer) clearInterval(uiTimer)
    finalized = true
    try {
      if (recorder && recorder.state !== 'inactive') recorder.stop()
    } catch {
      // 卸载时忽略
    }
    cleanupStream()
  })

  return {
    supported,
    state,
    volume,
    speechDetected,
    elapsedMs,
    errorCode,
    start,
    stop,
  }
}
