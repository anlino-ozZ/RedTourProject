/**
 * 浏览器定位组合式逻辑
 * - 统一处理安全上下文、权限、超时与错误文案
 * - watch=true 时持续跟随位置（导航页蓝点），否则只取一次（推荐页出发位置）
 */
import { onBeforeUnmount, ref } from 'vue'
import {
  geoFailureMessage,
  isSecureGeoContext,
  type GeoPoint,
} from '@/utils/geo'

export type GeoStatus = 'idle' | 'locating' | 'ok' | 'fail'

export interface UseGeolocationOptions {
  /** 持续监听位置变化，默认 false（单次定位） */
  watch?: boolean
  /** 创建后立即发起定位，默认 true */
  immediate?: boolean
}

export function useGeolocation(options: UseGeolocationOptions = {}) {
  const position = ref<GeoPoint | null>(null)
  const status = ref<GeoStatus>('idle')
  const errorMessage = ref('')
  const supported = typeof navigator !== 'undefined' && 'geolocation' in navigator

  let watchId: number | null = null
  let disposed = false

  function stop() {
    if (watchId !== null && supported) {
      navigator.geolocation.clearWatch(watchId)
    }
    watchId = null
  }

  function onSuccess(pos: GeolocationPosition) {
    if (disposed) return
    position.value = {
      latitude: pos.coords.latitude,
      longitude: pos.coords.longitude,
      accuracy: Number.isFinite(pos.coords.accuracy) ? pos.coords.accuracy : undefined,
    }
    status.value = 'ok'
    errorMessage.value = ''
  }

  function onError(error: GeolocationPositionError) {
    if (disposed) return
    status.value = 'fail'
    errorMessage.value = geoFailureMessage(error)
  }

  function start() {
    if (!supported) {
      status.value = 'fail'
      errorMessage.value = '当前浏览器不支持定位，请更换浏览器或手动选择出发景点'
      return
    }
    if (!isSecureGeoContext()) {
      status.value = 'fail'
      errorMessage.value =
        '当前页面不是 HTTPS，浏览器会禁用定位；请使用 localhost 或 HTTPS 访问后再试'
      return
    }
    status.value = 'locating'
    errorMessage.value = ''
    const geoOptions: PositionOptions = {
      enableHighAccuracy: true,
      timeout: 15000,
      maximumAge: 5000,
    }
    if (options.watch) {
      stop()
      watchId = navigator.geolocation.watchPosition(onSuccess, onError, geoOptions)
    } else {
      navigator.geolocation.getCurrentPosition(onSuccess, onError, geoOptions)
    }
  }

  if (options.immediate !== false) start()

  onBeforeUnmount(() => {
    disposed = true
    stop()
  })

  return { position, status, errorMessage, supported, start, stop }
}