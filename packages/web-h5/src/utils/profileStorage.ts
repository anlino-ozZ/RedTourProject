/**
 * 用户资料与通用设置（localStorage 持久化）
 * 当前账号体系未接入，资料仅存本机；后续接入用户中心接口后迁移到服务端
 */
import { mockUser } from '@/mock/profile'

const PROFILE_KEY = 'red-tour:profile'
const SETTINGS_KEY = 'red-tour:settings'

export interface UserProfile {
  nickname: string
  slogan: string
  /** 自定义头像（压缩后的 dataURL）；为空时使用 presetIndex 对应的预设头像 */
  avatar: string
  /** 预设头像序号 */
  presetIndex: number
}

/** 字体缩放档位：标准 / 大号（老年友好）/ 特大 */
export type FontScale = 'normal' | 'large' | 'xlarge'

export interface AppSettings {
  /** 全局音效开关 */
  soundEnabled: boolean
  /** 全局字体大小档位 */
  fontScale: FontScale
}

export const DEFAULT_PROFILE: UserProfile = {
  nickname: mockUser.nickname,
  slogan: mockUser.slogan,
  avatar: '',
  presetIndex: 0,
}

export const DEFAULT_SETTINGS: AppSettings = {
  soundEnabled: true,
  fontScale: 'normal',
}

/** 预设头像：红色主题渐变 + 白色图标（与头部默认人像风格一致） */
export const AVATAR_PRESETS: { gradient: string; icon: 'person' | 'star' | 'flag' | 'fire' | 'book' | 'medal' }[] = [
  { gradient: 'linear-gradient(135deg,#c41e3a,#8a1126)', icon: 'person' },
  { gradient: 'linear-gradient(135deg,#d4af37,#a67c1a)', icon: 'star' },
  { gradient: 'linear-gradient(135deg,#2f7cf6,#1a4fa0)', icon: 'flag' },
  { gradient: 'linear-gradient(135deg,#fa8c16,#c2570a)', icon: 'fire' },
  { gradient: 'linear-gradient(135deg,#52c41a,#2b7d0a)', icon: 'book' },
  { gradient: 'linear-gradient(135deg,#722ed1,#4b1789)', icon: 'medal' },
]

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return fallback
    return { ...fallback, ...(JSON.parse(raw) as object) } as T
  } catch {
    return fallback
  }
}

/** 读取用户资料（与默认值合并，旧版本缺字段也安全） */
export function getProfile(): UserProfile {
  return read(PROFILE_KEY, DEFAULT_PROFILE)
}

/** 局部更新并持久化用户资料 */
export function saveProfile(patch: Partial<UserProfile>): UserProfile {
  const next = { ...getProfile(), ...patch }
  try {
    localStorage.setItem(PROFILE_KEY, JSON.stringify(next))
  } catch {
    // localStorage 超限（头像过大）等异常静默处理，不影响页面使用
  }
  return next
}

/** 读取通用设置 */
export function getSettings(): AppSettings {
  return read(SETTINGS_KEY, DEFAULT_SETTINGS)
}

/** 局部更新并持久化通用设置 */
export function saveSettings(patch: Partial<AppSettings>): AppSettings {
  const next = { ...getSettings(), ...patch }
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(next))
  return next
}

/**
 * 将用户选择的图片文件压缩为方形小头像 dataURL（最长边 256px，JPEG 0.82）
 * 避免原图过大撑爆 localStorage
 */
export function fileToAvatarDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    if (!file.type.startsWith('image/')) {
      reject(new Error('请选择图片文件'))
      return
    }
    const reader = new FileReader()
    reader.onerror = () => reject(new Error('图片读取失败'))
    reader.onload = () => {
      const img = new Image()
      img.onerror = () => reject(new Error('图片解析失败'))
      img.onload = () => {
        const size = 256
        const side = Math.min(img.width, img.height)
        const canvas = document.createElement('canvas')
        canvas.width = size
        canvas.height = size
        const ctx = canvas.getContext('2d')
        if (!ctx) {
          reject(new Error('当前环境不支持图片处理'))
          return
        }
        // 居中裁剪为正方形再缩放
        ctx.drawImage(
          img,
          (img.width - side) / 2,
          (img.height - side) / 2,
          side,
          side,
          0,
          0,
          size,
          size,
        )
        resolve(canvas.toDataURL('image/jpeg', 0.82))
      }
      img.src = String(reader.result)
    }
    reader.readAsDataURL(file)
  })
}
