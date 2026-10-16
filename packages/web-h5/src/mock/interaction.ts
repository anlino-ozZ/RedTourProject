/**
 * 互动中心 Mock 数据（后续接入互动任务/成就接口）
 */

/** 成就徽章 */
export interface AchievementBadge {
  id: string
  name: string
  earned: boolean
  /** 未解锁时灰色展示 */
  color?: string
  icon?: 'flag' | 'star' | 'medal'
  /** 徽章说明（弹窗展示） */
  desc?: string
  /** 获取时间，格式 YYYY-MM-DD HH:mm；未解锁为空 */
  earnedAt?: string
}

/** 待解锁任务 */
export interface InteractionTask {
  id: string
  title: string
  desc: string
  /** 按钮文案 */
  actionText: string
  /** 任务状态 */
  status: 'doing' | 'todo' | 'done'
  /** 点击跳转路由（无则 toast 提示敬请期待） */
  actionTo?: string
}

/** 剧本角色 */
export interface ScriptRole {
  id: string
  name: string
  title: string
  desc: string
  /** 角色立绘主题色 */
  color: string
}

/** 剧情节点 */
export interface ScriptNode {
  speaker: string
  text: string
}

/** 任务类型：动作识别 / 问答 / 打卡 */
export type ScriptTaskType = 'action' | 'quiz' | 'checkin'

export interface ScriptTask {
  type: ScriptTaskType
  title: string
  hint: string
  /** 动作任务：需完成的动作名 */
  action?: string
  /** 问答任务：题目与选项 */
  quiz?: {
    question: string
    options: string[]
    answer: number
    /** 首次答错后的引导提示 */
    guide: string
  }
  /** 打卡任务：打卡点名称 */
  spot?: string
  /** 完成奖励积分 */
  points: number
}

export const mockAchievements: AchievementBadge[] = [
  {
    id: 'a1',
    name: '初心使命',
    earned: false,
    desc: '通关沉浸式剧本《暗哨行动》，完成情报传递任务即可点亮。',
  },
  {
    id: 'a2',
    name: '旗弈先锋',
    earned: true,
    color: '#d4af37',
    desc: '在红色剧本任务中行动果敢，率先完成动作识别挑战。',
    earnedAt: '2026-09-28 14:20',
  },
  {
    id: 'a3',
    name: '知识达人',
    earned: true,
    color: '#c41e3a',
    desc: '连续答对 5 道红色历史知识题，彰显扎实的知识储备。',
    earnedAt: '2026-09-29 10:05',
  },
  {
    id: 'a4',
    name: '鱼水情深',
    earned: false,
    desc: '通关沉浸式剧本《半条被子》，感悟军民鱼水情。',
  },
]

export const mockTasks: InteractionTask[] = [
  {
    id: 't0',
    title: '沉浸式红色剧本',
    desc: '选择剧本，扮演角色完成剧情任务',
    actionText: '选择剧本',
    status: 'doing',
    actionTo: '/interactive/scripts',
  },
  {
    id: 't1',
    title: '文物扫描：寻找"磨盘"',
    desc: '使用拍照功能识别馆内特定文物',
    actionText: '去完成',
    status: 'todo',
  },
  {
    id: 't2',
    title: '红色答题：峥嵘岁月',
    desc: '连续答对 5 道历史知识题',
    actionText: '未开始',
    status: 'todo',
  },
]

/** 剧本 */
export interface Script {
  id: string
  title: string
  subtitle: string
  /** 主题色（列表封面/氛围） */
  color: string
  intro: string
  roles: ScriptRole[]
  tasks: ScriptTask[]
  ending: string
  reward: {
    points: number
    /** 通关解锁的徽章 id（对应 mockAchievements） */
    badgeId: string
  }
  /** 未上线剧本：列表置灰锁定 */
  locked?: boolean
}

export const mockScripts: Script[] = [
  {
    id: 's1',
    title: '暗哨行动',
    subtitle: '1934 · 井冈山 | 沉浸式红色剧本',
    color: '#c41e3a',
    intro:
      '1934 年秋，井冈山根据地被敌人严密封锁。你是一名年仅十六岁的小交通员，代号"磨盘"。今夜，一份标注着敌军布防的情报必须在天亮前送到山南的中心支部。哨卡林立、盘查森严，出发之前，交通员老张将教你用暗号确认彼此……',
    roles: [
      {
        id: 'r1',
        name: '阿明',
        title: '小交通员 · 机敏',
        desc: '从小在山中长大，熟悉每一条小路，擅长乔装与暗号。',
        color: '#c41e3a',
      },
      {
        id: 'r2',
        name: '秀英',
        title: '联络员 · 沉稳',
        desc: '识字断文，负责情报誊抄与传递，临危不乱。',
        color: '#a3541e',
      },
    ],
    tasks: [
      {
        type: 'action',
        title: '动作任务：致敬先烈',
        hint: '面向屏幕，行一个庄严的军礼即可通过哨卡',
        action: '军礼',
        points: 10,
      },
      {
        type: 'quiz',
        title: '问答任务：哨卡对暗号',
        hint: '答对哨兵的提问，证明自己的身份',
        quiz: {
          question: '井冈山革命根据地创建于哪一年？',
          options: ['1927 年', '1928 年', '1930 年', '1934 年'],
          answer: 0,
          guide: '提示：秋收起义部队于 1927 年 10 月到达井冈山，开始创建根据地。',
        },
        points: 10,
      },
      {
        type: 'checkin',
        title: '打卡任务：黄洋界哨口',
        hint: '抵达打卡点，留下你的红色足迹',
        spot: '黄洋界哨口',
        points: 10,
      },
    ],
    ending:
      '晨光初现，你穿过最后一道山林，将情报稳稳交到中心支部同志手中。凭借这份情报，红军及时转移了乡亲，粉碎了敌人的合围。老张拍着你的肩膀笑了："磨盘，好样的，你已经是真正的红色交通员了。"',
    reward: { points: 30, badgeId: 'a1' },
  },
  {
    id: 's2',
    title: '半条被子',
    subtitle: '1934 · 湖南汝城沙洲村 | 军民鱼水情',
    color: '#a3541e',
    intro:
      '1934 年 11 月，红军长征路过湖南汝城县沙洲村。连日阴雨，寒气刺骨。你和两位女红军战友借宿在村民徐解秀老人家中——她家一贫如洗，床上只有一堆烂棉絮。夜深了，老人执意要把仅有的"铺盖"让给你们，而你们行囊里，只有一床行军被……',
    roles: [
      {
        id: 'r1',
        name: '小芹',
        title: '卫生员 · 细心',
        desc: '年纪最小，背着药箱一路救护伤员，最见不得老乡受苦。',
        color: '#a3541e',
      },
      {
        id: 'r2',
        name: '秀兰',
        title: '班长 · 坚毅',
        desc: '带队的老兵，纪律严明，常说"不拿群众一针一线"。',
        color: '#7d3c98',
      },
    ],
    tasks: [
      {
        type: 'action',
        title: '动作任务：致谢老乡',
        hint: '面向屏幕，向收留你的老人挥手致意',
        action: '挥手',
        points: 10,
      },
      {
        type: 'quiz',
        title: '问答任务：老人的疑问',
        hint: '回答老人的问题，讲讲红军的纪律',
        quiz: {
          question: '“半条被子”的故事发生在哪个村？',
          options: ['沙洲村', '瑶岗村', '古田村', '杨家岭'],
          answer: 0,
          guide: '提示：1934 年 11 月，3 名女红军借宿湖南汝城县沙洲村徐解秀老人家中。',
        },
        points: 10,
      },
      {
        type: 'checkin',
        title: '打卡任务：“半条被子”雕塑',
        hint: '抵达纪念雕塑前，留下你的敬意',
        spot: '“半条被子”主题雕塑',
        points: 10,
      },
    ],
    ending:
      '临别前，你们把那床仅有的行军被剪下一半，塞到老人手里："大娘，等革命胜利了，我们一定送你一床新被子！"多年后老人逢人便讲：什么是共产党？共产党就是自己只有一条被子，也要剪下半条给老百姓的人。',
    reward: { points: 30, badgeId: 'a4' },
  },
  {
    id: 's3',
    title: '飞夺泸定桥',
    subtitle: '1935 · 大渡河 | 即将上线',
    color: '#35567d',
    intro: '',
    roles: [],
    tasks: [],
    ending: '',
    reward: { points: 40, badgeId: '' },
    locked: true,
  },
]

export function getScriptById(id?: string): Script | undefined {
  return mockScripts.find((s) => s.id === id && !s.locked)
}

export function getBadgeById(id: string): AchievementBadge | undefined {
  return mockAchievements.find((b) => b.id === id)
}
