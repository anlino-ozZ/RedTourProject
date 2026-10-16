/**
 * 姿态识别摄像头组合式逻辑
 * 抽出自姿态互动页的通用部分：开启/关闭前置摄像头、截取当前帧并调用
 * POST /pose/recognize，返回后端识别结果与骨架关键点。
 * 组件卸载时会自动释放摄像头；中途切换任务请主动调用 stopCamera()。
 */
import { nextTick, onUnmounted, ref } from 'vue'
import { captureFrame, recognizePose, type PoseRecognizeResult } from '@/api/pose'

/** COCO 17 点，坐标为相对截帧画面的 0~1 归一化值 */
export type NormalizedKeypoints = number[][]

export function usePoseCamera() {
  const videoRef = ref<HTMLVideoElement | null>(null)
  /** 摄像头面板是否开启（为 true 时模板必须渲染 <video ref="videoRef">） */
  const cameraOn = ref(false)
  const cameraError = ref('')
  const recognizing = ref(false)
  const recognizeError = ref('')
  /** 最近一次识别出的骨架关键点，供预览叠加绘制 */
  const keypoints = ref<NormalizedKeypoints>([])
  /** 是否处于连续识别中 */
  const scanning = ref(false)

  let mediaStream: MediaStream | null = null
  let scanTimer: number | null = null

  /** 关闭摄像头并释放设备，避免摄像头指示灯长亮 */
  function stopCamera() {
    stopScan()
    mediaStream?.getTracks().forEach((track) => track.stop())
    mediaStream = null
    if (videoRef.value) videoRef.value.srcObject = null
    keypoints.value = []
    cameraOn.value = false
  }

  /** 开启摄像头：先让 <video> 渲染，再绑定媒体流 */
  async function startCamera(): Promise<boolean> {
    if (cameraOn.value) return true
    if (!navigator.mediaDevices?.getUserMedia) {
      cameraError.value = '当前浏览器不支持摄像头调用，请更换浏览器或使用 HTTPS 访问'
      return false
    }
    cameraError.value = ''
    cameraOn.value = true
    await nextTick()
    try {
      mediaStream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: 640, height: 480 },
        audio: false,
      })
      const video = videoRef.value
      if (!video) throw new Error('video element not mounted')
      video.srcObject = mediaStream
      await video.play()
      return true
    } catch {
      cameraError.value = '无法打开摄像头，请检查浏览器是否已授权摄像头权限'
      stopCamera()
      return false
    }
  }

  /** 把后端像素坐标转成 0~1 归一化坐标，点数不足 17 时返回空数组（避免骨架错位） */
  function toNormalized(points: unknown, width: number, height: number): NormalizedKeypoints {
    if (!Array.isArray(points) || points.length !== 17) return []
    const result: NormalizedKeypoints = []
    for (const point of points) {
      if (!Array.isArray(point) || point.length < 2) return []
      const x = Number(point[0])
      const y = Number(point[1])
      if (!Number.isFinite(x) || !Number.isFinite(y)) return []
      result.push([x / width, y / height])
    }
    return result
  }

  /** 截取当前帧识别一次；失败返回 null（原因写入 recognizeError） */
  async function recognizeOnce(): Promise<PoseRecognizeResult | null> {
    const video = videoRef.value
    if (!cameraOn.value || !video) {
      recognizeError.value = '请先开启摄像头'
      return null
    }
    const frame = captureFrame(video)
    if (!frame) {
      recognizeError.value = '画面获取失败，请稍后重试'
      return null
    }
    recognizing.value = true
    recognizeError.value = ''
    try {
      const res = await recognizePose(frame.data)
      keypoints.value = toNormalized(res.keypoints, frame.width, frame.height)
      return res
    } catch {
      recognizeError.value = '识别失败，请确认后端服务已启动后重试'
      keypoints.value = []
      return null
    } finally {
      recognizing.value = false
    }
  }

  /** 停止连续识别 */
  function stopScan() {
    if (scanTimer !== null) {
      window.clearTimeout(scanTimer)
      scanTimer = null
    }
    scanning.value = false
  }

  /**
   * 连续识别：按固定间隔截帧识别，骨架实时刷新。
   * onResult 返回 true 表示已命中目标动作，自动结束识别。
   */
  function startScan(onResult: (res: PoseRecognizeResult) => boolean) {
    stopScan()
    scanning.value = true
    const tick = async () => {
      if (!scanning.value) return
      const res = await recognizeOnce()
      if (!scanning.value) return
      if (res && onResult(res)) {
        stopScan()
        return
      }
      if (scanning.value) scanTimer = window.setTimeout(tick, 250)
    }
    void tick()
  }

  onUnmounted(stopCamera)

  return {
    videoRef,
    cameraOn,
    cameraError,
    recognizing,
    recognizeError,
    keypoints,
    scanning,
    startCamera,
    stopCamera,
    recognizeOnce,
    startScan,
    stopScan,
  }
}
