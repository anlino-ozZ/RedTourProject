import request from './request'
import type {
  AchievementClaimResult,
  LoginParams,
  RegisterParams,
  UserInfo,
} from '@red-tour-project/common'

// 占位示例接口，后续按模块拆分（如 scenic / route / user 等）
export function getHealth() {
  return request.get('/health')
}

// ===== 认证 =====
/** 账号密码登录（A-01） */
export function login(data: LoginParams) {
  return request.post<unknown, UserInfo>('/auth/login', data)
}

/** 游客自助注册，成功直接返回 token（A-02） */
export function register(data: RegisterParams) {
  return request.post<unknown, UserInfo>('/auth/register', data)
}

// ===== 成就（W-T-07） =====
/**
 * 凭触摸屏二维码中的临时 token 领取姿态成就
 * 后端校验：token 存 Redis 且未过期（5 分钟）→ 幂等写入 user_achievement.unlocked_at
 */
export function claimAchievement(token: string) {
  return request.post<unknown, AchievementClaimResult>('/achievements/claim', { token })
}
