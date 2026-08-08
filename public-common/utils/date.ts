/**
 * 日期工具：格式化日期 / 时间 / 相对时间
 */

/** 格式化日期，默认 YYYY-MM-DD */
export function formatDate(date: Date | number | string, fmt = 'YYYY-MM-DD'): string {
  const d = new Date(date)
  const map: Record<string, string> = {
    YYYY: String(d.getFullYear()),
    MM: String(d.getMonth() + 1).padStart(2, '0'),
    DD: String(d.getDate()).padStart(2, '0'),
    HH: String(d.getHours()).padStart(2, '0'),
    mm: String(d.getMinutes()).padStart(2, '0'),
    ss: String(d.getSeconds()).padStart(2, '0'),
  }
  return fmt.replace(/YYYY|MM|DD|HH|mm|ss/g, (m) => map[m])
}

/** 格式化时间，默认 HH:mm:ss */
export function formatTime(date: Date | number | string, fmt = 'HH:mm:ss'): string {
  return formatDate(date, fmt)
}

/** 相对时间（如「3 分钟前」） */
export function fromNow(date: Date | number | string): string {
  const diff = Date.now() - new Date(date).getTime()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return formatDate(date)
}
