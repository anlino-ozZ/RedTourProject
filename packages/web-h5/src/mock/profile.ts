/**
 * 我的页 Mock 数据（后续接入用户中心接口）
 */

export interface ProfileUser {
  nickname: string
  avatar?: string
  slogan: string
  level: number
  levelName: string
  medals: { owned: number; total: number }
  stats: { label: string; value: number }[]
}

export interface MedalItem {
  name: string
  icon: 'star' | 'check' | 'trophy' | 'lock' | 'map' | 'fire' | 'book' | 'flag'
  earned: boolean
  color?: string
  /** 勋章说明（点击弹窗展示） */
  desc?: string
}

export interface FootprintItem {
  id: string
  title: string
  meta: string
  tags?: string[]
  progress?: number
  actionText?: string
  actionTo?: string
}

export interface OrderItem {
  id: string
  title: string
  spec: string
  status: 'pending' | 'paid' | 'shipped' | 'done' | 'refund'
  statusText: string
  date: string
  price: number
  qty: number
  cover?: string
}

export const mockUser: ProfileUser = {
  nickname: '寻火者_2026',
  slogan: '红色之旅第 12 天 · 资深团友',
  level: 15,
  levelName: '筑梦先锋',
  medals: { owned: 12, total: 24 },
  stats: [
    { label: '游览次数', value: 36 },
    { label: '成就数', value: 12 },
    { label: '打卡数', value: 28 },
  ],
}

export const mockMedals: MedalItem[] = [
  { name: '初心始发', icon: 'star', earned: true, color: '#c41e3a', desc: '完成注册并开启红色之旅，迈出初心第一步。' },
  { name: '剧本专家', icon: 'check', earned: true, color: '#2f7cf6', desc: '通关任意一部沉浸式红色剧本。' },
  { name: '博古通今', icon: 'book', earned: true, color: '#d4af37', desc: '在红色问答中累计答对 20 道历史知识题。' },
  { name: '足迹达人', icon: 'map', earned: true, color: '#52c41a', desc: '累计完成 5 个景点打卡。' },
  { name: '烽火连城', icon: 'fire', earned: true, color: '#fa8c16', desc: '连续 7 天访问红色文旅，学习热情如火。' },
  { name: '红旗飘扬', icon: 'flag', earned: true, color: '#c41e3a', desc: '游览全部核心景点，让红旗插遍足迹地图。' },
  { name: '百战不殆', icon: 'trophy', earned: false, desc: '在剧本问答任务中零失误通关，证明你的博闻强识。' },
  { name: '万古流芳', icon: 'star', earned: false, desc: '收集全部隐藏文物线索，解锁终极荣誉。' },
]

/** 游览足迹：按日期分组，个人页可用日期筛选查看某一天的记录 */
export const mockFootprints: { date: string; items: FootprintItem[] }[] = [
  {
    date: '2026.05.10',
    items: [
      {
        id: 'f1',
        title: '革命历史陈列馆',
        meta: '停留时间：1小时12分钟',
        tags: ['完成讲解', '收集：草鞋'],
      },
      {
        id: 'f2',
        title: '红军烈士陵园',
        meta: '停留时间：36分钟',
        tags: ['敬献花篮'],
      },
    ],
  },
  {
    date: '2026.05.09',
    items: [
      {
        id: 'f3',
        title: '四渡赤水纪念馆',
        meta: '停留时间：52分钟',
        tags: ['完成讲解', '答题 3/3'],
      },
      {
        id: 'f4',
        title: '红色剧本：暗哨行动',
        meta: '进行中 · 进度 85%',
        progress: 85,
        actionText: '继续探索',
        actionTo: '/interactive',
      },
    ],
  },
  {
    date: '2026.05.08',
    items: [
      {
        id: 'f5',
        title: '遵义会议会址',
        meta: '停留时间：1小时05分钟',
        tags: ['完成讲解', '打卡成功'],
      },
    ],
  },
  {
    date: '2026.05.06',
    items: [
      {
        id: 'f6',
        title: '娄山关战斗遗址',
        meta: '停留时间：26分钟',
        tags: ['打卡成功'],
      },
    ],
  },
]

export const mockOrders: OrderItem[] = [
  {
    id: 'o1',
    title: '井冈山红军草鞋（复刻版）',
    spec: '38码 · 原色',
    status: 'shipped',
    statusText: '待收货',
    date: '2026-05-09',
    price: 89,
    qty: 1,
  },
  {
    id: 'o2',
    title: '初心之旅 · 经典打卡线 电子导览',
    spec: '终身有效 · 含语音讲解',
    status: 'done',
    statusText: '已完成',
    date: '2026-05-06',
    price: 29,
    qty: 1,
  },
  {
    id: 'o3',
    title: '红色文创 · 苏区搪瓷杯',
    spec: '350ml · 为人民服务',
    status: 'paid',
    statusText: '待发货',
    date: '2026-05-10',
    price: 59,
    qty: 2,
  },
]
