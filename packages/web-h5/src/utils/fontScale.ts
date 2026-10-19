/**
 * 全局字体大小（适老模式）
 * 在 <html> 上挂 fs-normal / fs-large / fs-xlarge 类，
 * 具体缩放比例见 src/styles/font-scale.css
 */
import type { FontScale } from './profileStorage'

const CLASSES = ['fs-normal', 'fs-large', 'fs-xlarge'] as const

export function applyFontScale(scale: FontScale) {
  const root = document.documentElement
  root.classList.remove(...CLASSES)
  root.classList.add(`fs-${scale}`)
}
