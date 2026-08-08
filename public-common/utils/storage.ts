/**
 * 本地存储工具：封装 localStorage，自动 JSON 序列化
 * 用于游客端游览记录、收藏、成就等离线数据
 */

/** 读取本地存储（自动 JSON 解析） */
export function getStorage<T>(key: string, defaultValue: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : defaultValue
  } catch {
    return defaultValue
  }
}

/** 写入本地存储（自动 JSON 序列化） */
export function setStorage<T>(key: string, value: T): void {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch (e) {
    console.warn('[storage] 写入失败', key, e)
  }
}

/** 移除指定本地存储 */
export function removeStorage(key: string): void {
  localStorage.removeItem(key)
}

/** 清空所有本地存储 */
export function clearStorage(): void {
  localStorage.clear()
}
