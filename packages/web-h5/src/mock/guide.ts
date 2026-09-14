/**
 * 智能导览 mock 数据（H-04 静态阶段，后续 H-07 替换为接口）
 * - spots：景区内热门景点，x/y 为模拟地图上的百分比坐标
 * - routes：导览路线，spotIds 关联该路线途经的景点；visitCount 为累计访问人数
 */

/** 景点 */
export interface GuideSpot {
  id: number
  name: string
  tag: string
  desc: string
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
  /** 难度 1~3（对应星级） */
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
  },
  {
    id: 2,
    name: '八角楼',
    tag: '革命旧址',
    desc: '毛泽东同志旧居，一盏清油灯下写下《中国的红色政权为什么能够存在？》。',
    x: 58,
    y: 38,
  },
  {
    id: 3,
    name: '革命博物馆',
    tag: '博物馆',
    desc: '全面展示井冈山斗争史的国家级博物馆，馆藏文物三千余件。',
    x: 46,
    y: 62,
  },
  {
    id: 4,
    name: '烈士陵园',
    tag: '纪念地',
    desc: '瞻仰革命先烈的纪念圣地，碑林、纪念碑与雕塑群依山而建。',
    x: 74,
    y: 70,
  },
  {
    id: 5,
    name: '会师广场',
    tag: '纪念碑',
    desc: '朱毛两军胜利会师之地，高耸的会师纪念碑见证了红军的诞生。',
    x: 30,
    y: 76,
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
    spotCount: 4,
    distance: 3.2,
    tag: '轻松',
    visitCount: 28600,
    spotIds: [1, 3, 5],
  },
  {
    id: 2,
    name: '烽火岁月·深度体验线',
    desc: '八角楼、小井红军医院旧址、烈士陵园，感受艰苦卓绝的斗争史',
    difficulty: 2,
    duration: 90,
    spotCount: 6,
    distance: 1.5,
    tag: '推荐',
    visitCount: 45200,
    spotIds: [2, 3, 4],
  },
  {
    id: 3,
    name: '重上井冈·全程徒步线',
    desc: '五大哨口全线徒步，适合体力充沛、时间充裕的游客',
    difficulty: 3,
    duration: 120,
    spotCount: 9,
    distance: 6.8,
    tag: '挑战',
    visitCount: 8900,
    spotIds: [1, 2, 3, 4, 5],
  },
]
