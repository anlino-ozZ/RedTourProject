/**
 * 触摸屏全局空闲定时器
 * 监听用户操作（点击/触摸/按键/滑动等），超过指定时长无操作则触发 idle 回调
 * 用于 60s 无操作自动返回首页
 */
import { ref, onBeforeUnmount } from 'vue'

// 默认空闲超时：60 秒
const DEFAULT_IDLE_TIMEOUT = 60_000

// 监听的用户活动事件
const ACTIVITY_EVENTS: (keyof WindowEventMap)[] = [
  'mousedown',
  'mousemove',
  'touchstart',
  'keydown',
  'click',
  'scroll',
  'wheel',
]

/**
 * 空闲定时器
 * @param timeout 超时毫秒数，默认 60000
 * @param onIdle  超时回调
 */
export function useIdleTimer(timeout = DEFAULT_IDLE_TIMEOUT, onIdle?: () => void) {
  const isIdle = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null

  const clear = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  const start = () => {
    clear()
    isIdle.value = false
    timer = setTimeout(() => {
      isIdle.value = true
      onIdle?.()
    }, timeout)
  }

  const reset = () => {
    if (isIdle.value) {
      isIdle.value = false
    }
    start()
  }

  const stop = () => {
    clear()
    isIdle.value = false
  }

  const onActivity = () => reset()

  const startListening = () => {
    ACTIVITY_EVENTS.forEach((ev) => {
      window.addEventListener(ev, onActivity, { passive: true })
    })
    start()
  }

  const stopListening = () => {
    ACTIVITY_EVENTS.forEach((ev) => {
      window.removeEventListener(ev, onActivity)
    })
    stop()
  }

  // 组件卸载时自动清理
  onBeforeUnmount(() => {
    stopListening()
  })

  return {
    isIdle,
    start: startListening,
    stop: stopListening,
    reset,
  }
}
