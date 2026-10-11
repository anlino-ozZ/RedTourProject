/**
 * 智能导览 mock 数据（H-04/H-06 静态阶段，后续 H-07 替换为接口）
 * - spots：景区内热门景点，x/y 为模拟地图上的百分比坐标
 * - routes：导览路线，spotIds 关联该路线途经的景点；visitCount 为累计访问人数
 * - tags：兴趣标签，用于偏好采集与路线加权评分
 *
 * 加权评分算法（mock 版本）：
 *   score = 兴趣匹配分 + 体力匹配分 + 时长匹配分 + 热度分 + 距离分
 */

/** 兴趣标签（与景点/路线关联） */
export type InterestTag = '历史' | '人物' | '战役' | '文物'

/** 体力等级 */
export type FitnessLevel = 'easy' | 'standard' | 'deep'

/** 景点 */
export interface GuideSpot {
  id: number
  name: string
  tag: string
  desc: string
  /** 讲解音频时长（秒，mock） */
  audioLen?: number
  /** 景点兴趣标签（用于偏好匹配） */
  interests?: InterestTag[]
  /** 模拟地图横坐标（百分比 0~100） */
  x: number
  /** 模拟地图纵坐标（百分比 0~100） */
  y: number
}

/** 导览路线 */
export interface GuideRoute {
  id: number
  name: string
  desc: string
  /** 难度 1~3（对应星级；1=轻松 2=标准 3=深度） */
  difficulty: number
  /** 预计时长（分钟） */
  duration: number
  /** 景点数 */
  spotCount: number
  /** 距当前位置（公里，mock 定位） */
  distance: number
  tag: string
  /** 累计访问人数（热度） */
  visitCount: number
  /** 途经景点 ID */
  spotIds: number[]
  /** 路线兴趣标签（用于偏好匹配加权） */
  interests: InterestTag[]
  /** 路线特色（AI 推荐后展示，如"★ 党史爱好者首选"） */
  highlight?: string
}

/** 热门景点（井冈山革命根据地区域 mock） */
export const spots: GuideSpot[] = [
  {
    id: 1,
    name: '黄洋界',
    tag: '战役遗址',
    desc: '黄洋界保卫战发生地，扼守井冈山五大哨口之一，地势险要、云海壮阔。',
    x: 22,
    y: 26,
    audioLen: 145,
    interests: ['战役', '历史'],
  },
  {
    id: 2,
    name: '八角楼',
    tag: '革命旧址',
    desc: '毛泽东同志旧居，一盏清油灯下写下《中国的红色政权为什么能够存在？》。',
    x: 58,
    y: 38,
    audioLen: 180,
    interests: ['人物', '历史', '文物'],
  },
  {
    id: 3,
    name: '革命博物馆',
    tag: '博物馆',
    desc: '全面展示井冈山斗争史的国家级博物馆，馆藏文物三千余件。',
    x: 46,
    y: 62,
    audioLen: 210,
    interests: ['历史', '文物', '战役'],
  },
  {
    id: 4,
    name: '烈士陵园',
    tag: '纪念地',
    desc: '瞻仰革命先烈的纪念圣地，碑林、纪念碑与雕塑群依山而建。',
    x: 74,
    y: 70,
    audioLen: 120,
    interests: ['人物', '历史'],
  },
  {
    id: 5,
    name: '会师广场',
    tag: '纪念碑',
    desc: '朱毛两军胜利会师之地，高耸的会师纪念碑见证了红军的诞生。',
    x: 30,
    y: 76,
    audioLen: 155,
    interests: ['人物', '战役', '历史'],
  },
]

/** 导览路线 */
export const routes: GuideRoute[] = [
  {
    id: 1,
    name: '初心之旅·经典打卡线',
    desc: '黄洋界哨口、革命博物馆、会师广场，首次到访必走',
    difficulty: 1,
    duration: 60,
    spotCount: 3,
    distance: 3.2,
    tag: '轻松',
    visitCount: 28600,
    spotIds: [1, 3, 5],
    interests: ['历史', '战役'],
  },
  {
    id: 2,
    name: '烽火岁月·深度体验线',
    desc: '八角楼、革命博物馆、烈士陵园，感受艰苦卓绝的斗争史',
    difficulty: 2,
    duration: 90,
    spotCount: 3,
    distance: 1.5,
    tag: '推荐',
    visitCount: 45200,
    spotIds: [2, 3, 4],
    interests: ['人物', '文物', '历史'],
  },
  {
    id: 3,
    name: '重上井冈·全程徒步线',
    desc: '五大哨口全线徒步，适合体力充沛、时间充裕的游客',
    difficulty: 3,
    duration: 120,
    spotCount: 5,
    distance: 6.8,
    tag: '挑战',
    visitCount: 8900,
    spotIds: [1, 2, 3, 4, 5],
    interests: ['战役', '历史', '人物', '文物'],
  },
]

/** AI 加权评分算法（mock）：根据偏好从 routes 中推荐 2-3 条 */
export function recommendRoutes(params: {
  interests: InterestTag[]
  fitness: FitnessLevel
  duration: number // 可用时长（分钟）
}): (GuideRoute & { score: number; highlight: string })[] {
  const diffFromFitness: Record<FitnessLevel, number> = { easy: 1, standard: 2, deep: 3 }
  const targetDiff = diffFromFitness[params.fitness]

  const scored = routes.map((r) => {
    // 1) 兴趣匹配：选对一个 +8 分（满分 32）
    const interestScore = r.interests.filter((t) => params.interests.includes(t)).length * 8
    // 2) 体力匹配：难度差越小分越高（满分 20）
    const diffDelta = Math.abs(r.difficulty - targetDiff)
    const fitnessScore = Math.max(0, 20 - diffDelta * 8)
    // 3) 时长匹配：差距越小分越高（满分 20）；超时长直接大扣分
    const durDelta = Math.abs(r.duration - params.duration)
    const timeScore = r.duration > params.duration + 15 ? -20 : Math.max(0, 20 - durDelta * 0.5)
    // 4) 热度（满分 16）
    const hotScore = Math.min(16, Math.round(r.visitCount / 1000))
    // 5) 距离（满分 12，越近越高）
    const distScore = Math.max(0, 12 - Math.round(r.distance))

    const score = interestScore + fitnessScore + timeScore + hotScore + distScore

    // AI 生成特色文案
    const highlights: string[] = []
    if (interestScore >= 16) highlights.push('★ 高度匹配您的兴趣偏好')
    if (diffDelta === 0) highlights.push('★ 难度匹配您的体力等级')
    if (durDelta <= 15) highlights.push('★ 时长契合您的游览计划')
    if (r.visitCount >= 20000) highlights.push(`🔥 热门路线（${r.visitCount.toLocaleString()} 人游览）`)
    const highlight = highlights.length ? highlights.join(' · ') : '为您精选'

    return { ...r, score, highlight }
  })

  // 降序取前 2~3 条
  scored.sort((a, b) => b.score - a.score)
  return scored.slice(0, params.duration >= 120 ? 3 : 2)
}
