/**
 * 姿态识别摄像头组合式逻辑
 * 抽出自姿态互动页的通用部分：开启/关闭前置摄像头、截取当前帧并调用
 * POST /pose/recognize，返回后端识别结果。
 * 组件卸载时会自动释放摄像头；中途切换任务请主动调用 stopCamera()。
 */
import { nextTick, onUnmounted, ref } from 'vue'
import { captureFrameFromVideo, recognizePose, type PoseRecognizeResult } from '@/api/pose'

export function usePoseCamera() {
  const videoRef = ref<HTMLVideoElement | null>(null)
  /** 摄像头面板是否开启（为 true 时模板必须渲染 <video ref="videoRef">） */
  const cameraOn = ref(false)
  const cameraError = ref('')
  const recognizing = ref(false)
  const recognizeError = ref('')

  let mediaStream: MediaStream | null = null

  /** 关闭摄像头并释放设备，避免摄像头指示灯长亮 */
  function stopCamera() {
    mediaStream?.getTracks().forEach((track) => track.stop())
    mediaStream = null
    if (videoRef.value) videoRef.value.srcObject = null
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

  /** 截取当前帧识别一次；失败返回 null（原因写入 recognizeError） */
  async function recognizeOnce(): Promise<PoseRecognizeResult | null> {
    const video = videoRef.value
    if (!cameraOn.value || !video) {
      recognizeError.value = '请先开启摄像头'
      return null
    }
    const frame = captureFrameFromVideo(video)
    if (!frame) {
      recognizeError.value = '画面获取失败，请稍后重试'
      return null
    }
    recognizing.value = true
    recognizeError.value = ''
    try {
      return await recognizePose(frame)
    } catch {
      recognizeError.value = '识别失败，请确认后端服务已启动后重试'
      return null
    } finally {
      recognizing.value = false
    }
  }

  onUnmounted(stopCamera)

  return {
    videoRef,
    cameraOn,
    cameraError,
    recognizing,
    recognizeError,
    startCamera,
    stopCamera,
    recognizeOnce,
  }
}
