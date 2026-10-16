<script setup lang="ts">
/**
 * 姿态互动（H5 单帧识别）
 * 与触摸屏端同源：调用后端 /pose/recognize 识别游客动作（敬礼 / 推磨 / 挥手），
 * 识别成功后展示结果并播放该动作关联的讲解（audioUrl 由后端 TTS 生成）。
 */
import { computed, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  POSE_ACTION_LABEL,
  captureFrameFromVideo,
  recognizePose,
  resolvePoseAudioUrl,
  type PoseRecognizeResult,
} from '@/api/pose'
import { playCorrectSound, playTapSound, playWrongSound } from '@/utils/sound'

const router = useRouter()

const videoRef = ref<HTMLVideoElement | null>(null)
const cameraOn = ref(false)
const cameraError = ref('')
const recognizing = ref(false)
const recognizeError = ref('')
const result = ref<PoseRecognizeResult | null>(null)

let mediaStream: MediaStream | null = null

const actionLabel = computed(() =>
  result.value ? POSE_ACTION_LABEL[result.value.action] || result.value.action : '',
)
const confidenceText = computed(() =>
  result.value ? `${Math.round(result.value.confidence * 100)}%` : '',
)
const trigger = computed(() => result.value?.triggerContent ?? null)

/** 关闭摄像头并释放设备（离开页面时也要调用，避免摄像头指示灯长亮） */
function stopCamera() {
  mediaStream?.getTracks().forEach((track) => track.stop())
  mediaStream = null
  cameraOn.value = false
}

async function startCamera() {
  if (!navigator.mediaDevices?.getUserMedia) {
    cameraError.value = '当前浏览器不支持摄像头调用，请更换浏览器或使用 HTTPS 访问'
    return
  }
  cameraError.value = ''
  playTapSound()
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: 640, height: 480 },
      audio: false,
    })
    if (videoRef.value) {
      videoRef.value.srcObject = mediaStream
      await videoRef.value.play()
    }
    cameraOn.value = true
  } catch {
    cameraError.value = '无法打开摄像头，请检查浏览器是否已授权摄像头权限'
    stopCamera()
  }
}

/** 识别一次：截当前帧 → POST /pose/recognize → 展示动作与讲解 */
async function onRecognize() {
  if (recognizing.value) return
  const video = videoRef.value
  if (!cameraOn.value || !video) {
    recognizeError.value = '请先开启摄像头'
    playWrongSound()
    return
  }
  const frame = captureFrameFromVideo(video)
  if (!frame) {
    recognizeError.value = '画面获取失败，请稍后重试'
    playWrongSound()
    return
  }
  recognizing.value = true
  recognizeError.value = ''
  playTapSound()
  try {
    const res = await recognizePose(frame)
    result.value = res
    if (res.action === 'unknown') playWrongSound()
    else playCorrectSound()
  } catch {
    recognizeError.value = '识别失败，请确认后端服务已启动后重试'
    playWrongSound()
  } finally {
    recognizing.value = false
  }
}

/** 播放后端返回的讲解音频 */
function playNarration() {
  const url = trigger.value?.audioUrl
  if (!url) return
  playTapSound()
  new Audio(resolvePoseAudioUrl(url)).play().catch(() => {
    // 浏览器未授权自动播放等原因播放失败时静默处理，用户可再点一次
  })
}

onUnmounted(stopCamera)
</script>

<template>
  <div class="pose">
    <!-- ===== 摄像头预览 ===== -->
    <div class="pose__stage">
      <video ref="videoRef" class="pose__video" playsinline muted />
      <div v-if="!cameraOn" class="pose__placeholder">
        <p class="pose__placeholder-title">开启摄像头开始互动</p>
        <p class="pose__placeholder-desc">站到镜头前，做出敬礼 / 推磨 / 挥手的动作</p>
      </div>
      <div v-else class="pose__guide">
        <span class="pose__guide-item">敬礼</span>
        <span class="pose__guide-item">推磨</span>
        <span class="pose__guide-item">挥手</span>
      </div>
    </div>

    <p v-if="cameraError" class="pose__error">{{ cameraError }}</p>

    <!-- ===== 操作区 ===== -->
    <div class="pose__actions">
      <button
        v-if="!cameraOn"
        type="button"
        class="pose__btn pose__btn--primary"
        @click="startCamera"
      >
        开启摄像头
      </button>
      <template v-else>
        <button
          type="button"
          class="pose__btn pose__btn--primary"
          :disabled="recognizing"
          @click="onRecognize"
        >
          {{ recognizing ? '识别中…' : '识别我的动作' }}
        </button>
        <button type="button" class="pose__btn" @click="stopCamera">关闭摄像头</button>
      </template>
    </div>

    <p v-if="recognizeError" class="pose__error">{{ recognizeError }}</p>

    <!-- ===== 识别结果 ===== -->
    <div v-if="result" class="pose-result" :class="{ 'is-unknown': result.action === 'unknown' }">
      <div class="pose-result__head">
        <span class="pose-result__label">识别结果</span>
        <span class="pose-result__conf">置信度 {{ confidenceText }}</span>
      </div>
      <div class="pose-result__action">{{ actionLabel }}</div>

      <div v-if="trigger" class="pose-result__content">
        <div class="pose-result__title">{{ trigger.title }}</div>
        <button
          v-if="trigger.audioUrl"
          type="button"
          class="pose-result__play"
          @click="playNarration"
        >
          播放讲解
        </button>
      </div>
      <p v-else class="pose-result__tip">没识别到标准动作，按提示再试一次吧</p>

      <button type="button" class="pose-result__more" @click="router.push('/qa')">
        去智能问答里继续了解 ›
      </button>
    </div>

    <p class="pose__note">
      说明：H5 端为单帧识别（拍照识别），展馆触摸屏端另提供实时推流姿态互动；
      识别失败时请确认后端 /pose/recognize 服务可用。
    </p>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.pose {
  padding: @spacing-md;
  background: @color-bg-page;
  min-height: 100%;

  &__stage {
    position: relative;
    width: 100%;
    aspect-ratio: 4 / 3;
    border-radius: @radius-lg;
    overflow: hidden;
    background: #1a0a0e;
    box-shadow: @shadow-card;
  }

  &__video {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  &__placeholder {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: @spacing-sm;
    padding: @spacing-md;
    text-align: center;
    color: rgba(255, 255, 255, 0.88);
  }

  &__placeholder-title {
    margin: 0;
    font-size: @font-size-lg;
    font-weight: 600;
  }

  &__placeholder-desc {
    margin: 0;
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.6);
  }

  &__guide {
    position: absolute;
    left: 0;
    right: 0;
    bottom: @spacing-sm;
    display: flex;
    justify-content: center;
    gap: @spacing-sm;
  }

  &__guide-item {
    padding: 2px @spacing-sm;
    border-radius: 999px;
    background: rgba(196, 30, 58, 0.85);
    color: #fff;
    font-size: @font-size-sm;
  }

  &__actions {
    display: flex;
    gap: @spacing-sm;
    margin-top: @spacing-md;
  }

  &__btn {
    flex: 1;
    height: 44px;
    border: 1px solid @color-border;
    border-radius: @radius-base;
    background: @color-bg-card;
    color: @color-text-primary;
    font-size: @font-size-base;

    &--primary {
      background: @color-primary;
      border-color: @color-primary;
      color: #fff;
      font-weight: 600;

      &:disabled {
        opacity: 0.6;
      }
    }
  }

  &__error {
    margin: @spacing-sm 0 0;
    color: @color-danger;
    font-size: @font-size-sm;
  }

  &__note {
    margin: @spacing-lg 0 0;
    color: @color-text-secondary;
    font-size: @font-size-sm;
    line-height: 1.6;
  }
}

.pose-result {
  margin-top: @spacing-md;
  padding: @spacing-md;
  border-radius: @radius-lg;
  background: @color-bg-card;
  box-shadow: @shadow-card;

  &.is-unknown {
    border: 1px solid @color-border;
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__label {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__conf {
    font-size: @font-size-sm;
    color: @color-primary;
  }

  &__action {
    margin: @spacing-sm 0;
    font-size: 28px;
    font-weight: 700;
    color: @color-primary;
  }

  &__content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: @spacing-sm;
    padding: @spacing-sm @spacing-md;
    border-radius: @radius-base;
    background: @color-primary-light;
  }

  &__title {
    font-size: @font-size-base;
    color: @color-text-primary;
  }

  &__play {
    padding: 6px @spacing-md;
    border: none;
    border-radius: 999px;
    background: @color-primary;
    color: #fff;
    font-size: @font-size-sm;
  }

  &__tip {
    margin: 0;
    color: @color-text-secondary;
    font-size: @font-size-sm;
  }

  &__more {
    margin-top: @spacing-md;
    padding: 0;
    border: none;
    background: none;
    color: @color-primary;
    font-size: @font-size-sm;
  }
}
</style>