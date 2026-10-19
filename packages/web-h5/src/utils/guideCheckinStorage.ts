/**
 * 导览打卡本地持久层
 *
 * PRD 要求打卡先落本地，在线时同步后端。这里按路线保存：
 * - checkedSpotIds：已经打卡成功的景点（含离线待同步）
 * - pending：尚未同步到后端的打卡记录（携带打卡时经纬度）
 */

export interface PendingGuideCheckin {
  spotId: number
  longitude: number
  latitude: number
  checkedAt: number
}

export interface GuideCheckinRouteState {
  checkedSpotIds: number[]
  pending: PendingGuideCheckin[]
}

const KEY_PREFIX = 'rt_guide_checkins_v1:'

function keyOf(routeId: number): string {
  return `${KEY_PREFIX}${routeId}`
}

function emptyState(): GuideCheckinRouteState {
  return { checkedSpotIds: [], pending: [] }
}

export function getGuideCheckins(routeId: number): GuideCheckinRouteState {
  try {
    const raw = localStorage.getItem(keyOf(routeId))
    if (!raw) return emptyState()
    const parsed = JSON.parse(raw) as Partial<GuideCheckinRouteState>
    const checkedSpotIds = Array.isArray(parsed.checkedSpotIds)
      ? parsed.checkedSpotIds.map(Number).filter(Number.isFinite)
      : []
    const pending = Array.isArray(parsed.pending)
      ? parsed.pending.flatMap((item): PendingGuideCheckin[] => {
          const record = item as Partial<PendingGuideCheckin> | null
          if (!record) return []
          const spotId = Number(record.spotId)
          const longitude = Number(record.longitude)
          const latitude = Number(record.latitude)
          if (
            !Number.isFinite(spotId) ||
            !Number.isFinite(longitude) ||
            !Number.isFinite(latitude)
          ) {
            return []
          }
          return [{
            spotId,
            longitude,
            latitude,
            checkedAt: Number(record.checkedAt) || Date.now(),
          }]
        })
      : []
    return { checkedSpotIds, pending }
  } catch {
    return emptyState()
  }
}

function saveState(routeId: number, state: GuideCheckinRouteState): void {
  try {
    localStorage.setItem(keyOf(routeId), JSON.stringify(state))
  } catch {
    // 隐私模式 / 存储已满时忽略，页面内仍保留当前会话状态
  }
}

/** 写入一次打卡；pending=true 表示离线保存，等联网后再同步 */
export function saveGuideCheckin(
  routeId: number,
  spotId: number,
  location: { longitude: number; latitude: number },
  pending: boolean,
): GuideCheckinRouteState {
  const state = getGuideCheckins(routeId)
  if (!state.checkedSpotIds.includes(spotId)) state.checkedSpotIds.push(spotId)
  state.pending = state.pending.filter((item) => item.spotId !== spotId)
  if (pending) {
    state.pending.push({
      spotId,
      longitude: location.longitude,
      latitude: location.latitude,
      checkedAt: Date.now(),
    })
  }
  saveState(routeId, state)
  return state
}

/** 后端已确认打卡：移除对应待同步记录 */
export function markGuideCheckinSynced(
  routeId: number,
  spotId: number,
): GuideCheckinRouteState {
  const state = getGuideCheckins(routeId)
  state.pending = state.pending.filter((item) => item.spotId !== spotId)
  saveState(routeId, state)
  return state
}
