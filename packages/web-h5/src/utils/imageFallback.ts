/** 后端图片缺失 / 加载失败时的统一兜底 */
export const COVER_FALLBACK = '/images/placeholder-cover.svg'

/** 列表/轮播图：失败后切换到设计占位图，避免反复触发 */
export function applyCoverFallback(event: Event): void {
  const img = event.currentTarget as HTMLImageElement | null
  if (!img || img.dataset.fallback === '1') return
  img.dataset.fallback = '1'
  img.src = COVER_FALLBACK
}

/** 已有文字底图的场景：直接隐藏破图，露出底层内容 */
export function hideBrokenImage(event: Event): void {
  const img = event.currentTarget as HTMLImageElement | null
  if (img) img.style.display = 'none'
}