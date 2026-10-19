/** 地理坐标与模拟地图投影工具 */

export interface GeoPoint {
  latitude: number
  longitude: number
  /** 定位精度（米），浏览器 geolocation 返回 */
  accuracy?: number
}

export interface MapProjection {
  minLat: number
  maxLat: number
  minLon: number
  maxLon: number
}

/**
 * 浏览器定位只在安全上下文可用：
 * - https:// 页面
 * - http://localhost / http://127.0.0.1
 * 手机通过局域网 IP（http://192.168.x.x）访问时会被浏览器直接禁用。
 */
export function isSecureGeoContext(): boolean {
  if (typeof window === 'undefined') return false
  return (
    window.isSecureContext ||
    window.location.hostname === 'localhost' ||
    window.location.hostname === '127.0.0.1'
  )
}

/** 把 GeolocationPositionError 翻译成用户能看懂、能处理的原因 */
export function geoFailureMessage(error: GeolocationPositionError): string {
  if (!isSecureGeoContext()) {
    return '当前页面不是 HTTPS，手机用局域网 IP 访问时浏览器会禁用定位；请改用 https 或 localhost 访问'
  }
  switch (error.code) {
    case error.PERMISSION_DENIED:
      return '定位权限被拒绝，请点击浏览器地址栏的权限图标，允许本站获取位置后重试'
    case error.POSITION_UNAVAILABLE:
      return '系统定位服务不可用：请打开电脑/手机的定位开关；部分网络环境（如无法访问 Google 定位服务）也会导致该错误'
    case error.TIMEOUT:
      return '定位超时，请到信号较好的位置后重试'
    default:
      return error.message || '定位失败，请重试'
  }
}

const EARTH_RADIUS_M = 6371000

function toRadians(deg: number): number {
  return (deg * Math.PI) / 180
}

/** 两点间球面距离（米） */
export function haversineMeters(a: GeoPoint, b: GeoPoint): number {
  const dLat = toRadians(b.latitude - a.latitude)
  const dLon = toRadians(b.longitude - a.longitude)
  const lat1 = toRadians(a.latitude)
  const lat2 = toRadians(b.latitude)
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)))
}

/** 根据一组景点坐标构建与 api/spots.ts 一致的地图投影范围 */
export function buildMapProjection(points: GeoPoint[]): MapProjection | null {
  const valid = points.filter(
    (point) => Number.isFinite(point.latitude) && Number.isFinite(point.longitude),
  )
  if (!valid.length) return null
  const lats = valid.map((point) => point.latitude)
  const lons = valid.map((point) => point.longitude)
  return {
    minLat: Math.min(...lats),
    maxLat: Math.max(...lats),
    minLon: Math.min(...lons),
    maxLon: Math.max(...lons),
  }
}

/**
 * 经纬度 → 地图百分比坐标。
 * 投影公式与 api/spots.ts 中 projectCoords 保持一致，保证用户蓝点和景点 pin 在同一套坐标系。
 */
export function projectGeoPoint(
  point: GeoPoint,
  projection: MapProjection,
): { x: number; y: number } {
  const spanLon = projection.maxLon - projection.minLon
  const spanLat = projection.maxLat - projection.minLat
  const rawX = spanLon === 0 ? 50 : ((point.longitude - projection.minLon) / spanLon) * 76 + 12
  const rawY = spanLat === 0 ? 50 : ((projection.maxLat - point.latitude) / spanLat) * 76 + 12
  const clamp = (value: number) => Math.min(96, Math.max(4, value))
  return {
    x: Math.round(clamp(rawX) * 10) / 10,
    y: Math.round(clamp(rawY) * 10) / 10,
  }
}