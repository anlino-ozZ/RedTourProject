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
}

/** 待解锁任务 */
export interface InteractionTask {
  id: string
  title: string
  desc: string
  /** 按钮文案 */
  actionText: string
  /** 任务状态 */
  status: 'doing' | 'todo'
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
  { id: 'a1', name: '初心使命', earned: false },
  { id: 'a2', name: '旗弈先锋', earned: true, color: '#d4af37' },
  { id: 'a3', name: '知识达人', earned: true, color: '#c41e3a' },
]

/** 剧本通关后解锁的徽章（对应初心使命） */
export const SCRIPT_REWARD_BADGE = mockAchievements[0]

export const mockTasks: InteractionTask[] = [
  {
    id: 't0',
    title: '红色剧本：暗哨行动',
    desc: '扮演小交通员，完成情报传递',
    actionText: '进入剧本',
    status: 'doing',
    actionTo: '/interactive/script',
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

/** 剧本：暗哨行动 */
export const mockScript = {
  id: 's1',
  title: '红色剧本：暗哨行动',
  subtitle: '1934 · 井冈山 | 沉浸式红色剧本',
  /** 开场介绍 */
  intro:
    '1934 年秋，井冈山根据地被敌人严密封锁。你是一名年仅十六岁的小交通员，代号"磨盘"。今夜，一份标注着敌军布防的情报必须在天亮前送到山南的中心支部。哨卡林立、盘查森严，出发之前，交通员老张将教你用暗号确认彼此……',
  /** 可选角色 */
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
  ] as ScriptRole[],
  /** 任务循环（按顺序执行） */
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
  ] as ScriptTask[],
  /** 结局剧情 */
  ending:
    '晨光初现，你穿过最后一道山林，将情报稳稳交到中心支部同志手中。凭借这份情报，红军及时转移了乡亲，粉碎了敌人的合围。老张拍着你的肩膀笑了："磨盘，好样的，你已经是真正的红色交通员了。"',
  /** 通关奖励 */
  reward: {
    points: 30,
    badge: SCRIPT_REWARD_BADGE,
  },
}
