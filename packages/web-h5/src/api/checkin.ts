/**
 * 导览景点打卡接口
 *
 * H-11 任务表主路径：POST /checkpoints/check
 * docs/05 全局接口文档另定义了 POST /routes/{id}/checkin，
 * 主接口 404/405 时自动走文档路径，避免前后端联调路径不一致。
 */
import axios from 'axios'
import type { CheckinRequest, CheckinResult } from '@red-tour-project/common'
import request, { ApiError } from './request'

const CHECKIN_PRIMARY_ENDPOINT = '/checkpoints/check'

/** 主接口不存在时是否值得尝试文档里的兼容路径 */
function isEndpointMissing(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false
  const status = error.response?.status
  return status === 404 || status === 405
}

function normalizeResult(
  payload: CheckinResult | null | undefined,
  requestBody: CheckinRequest,
): CheckinResult {
  return {
    spotId: payload?.spotId ?? requestBody.spotId,
    checked: payload?.checked ?? true,
    progress: payload?.progress ?? '',
    achievementUnlocked: payload?.achievementUnlocked ?? null,
  }
}

/**
 * 景点打卡。
 * 调用方需保证用户已登录，并由浏览器定位取得当前经纬度。
 */
export async function checkinRouteSpot(
  routeId: number,
  body: CheckinRequest,
): Promise<CheckinResult> {
  try {
    const payload = (await request.post(
      CHECKIN_PRIMARY_ENDPOINT,
      { routeId, ...body },
      { silent: true },
    )) as CheckinResult | null
    return normalizeResult(payload, body)
  } catch (error) {
    if (!isEndpointMissing(error)) throw error
  }

  const payload = (await request.post(
    `/routes/${routeId}/checkin`,
    body,
    { silent: true },
  )) as CheckinResult | null
  return normalizeResult(payload, body)
}

/** 后端返回“不在打卡范围内”（业务码 400 / HTTP 400） */
export function isCheckinOutOfRange(error: unknown): boolean {
  if (error instanceof ApiError) return error.code === 400
  if (!axios.isAxiosError(error)) return false
  if (error.response?.status === 400) return true
  const data = error.response?.data as { code?: number } | undefined
  return data?.code === 400
}
