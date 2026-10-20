/**
 * 导览景点打卡接口
 *
 * 对接后端真实接口：POST /routes/{id}/checkin。
 */
import axios from 'axios'
import type { CheckinRequest, CheckinResult } from '@red-tour-project/common'
import request, { ApiError } from './request'

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
