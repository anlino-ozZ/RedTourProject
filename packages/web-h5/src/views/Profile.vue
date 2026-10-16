<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  mockUser,
  mockMedals,
  mockFootprints,
  mockOrders,
  type MedalItem,
  type OrderItem,
} from '@/mock/profile'
import UserAvatar from '@/components/UserAvatar.vue'
import MedalBadge from '@/components/MedalBadge.vue'
import {
  AVATAR_PRESETS,
  DEFAULT_PROFILE,
  fileToAvatarDataUrl,
  getProfile,
  getSettings,
  saveProfile,
  saveSettings,
  type AppSettings,
  type FontScale,
  type UserProfile,
} from '@/utils/profileStorage'
import { applyFontScale } from '@/utils/fontScale'
import { clearSessions, listSessions } from '@/utils/qaStorage'
import { LEVEL_RULES, addPoints, getLevelInfo, type LevelInfo } from '@/utils/levelStorage'
import {
  checkInToday,
  getCheckIn,
  isCheckedToday,
  quoteOfTheDay,
  type CheckInState,
} from '@/utils/checkInStorage'
import { playCorrectSound, playTapSound, playWrongSound } from '@/utils/sound'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

// ===== 本地资料 & 设置（账号体系接入前仅存本机）=====
const profile = ref<UserProfile>(getProfile())
const settings = ref<AppSettings>(getSettings())

// ===== 等级体系：积分驱动，进入页面时刷新 =====
const levelInfo = ref<LevelInfo>(getLevelInfo())
onMounted(async () => {
  levelInfo.value = getLevelInfo()
  // 登录后把账号昵称回填到本地资料：仅当本地昵称仍是默认值时，避免覆盖用户自定义昵称
  if (!userStore.isLoggedIn) return
  const me = userStore.user ?? (await userStore.refreshMe())
  if (me?.nickname && profile.value.nickname === DEFAULT_PROFILE.nickname) {
    profile.value = saveProfile({ nickname: me.nickname })
  }
})

// ===== 每日签到：每日首次 +10 积分，签到后展示当日革命先辈名言 =====
const SIGN_IN_POINTS = 10
const checkInState = ref<CheckInState>(getCheckIn())
const checkedToday = ref(isCheckedToday())
const quote = quoteOfTheDay()

// 诗句逐字淡入：含「」一起拆字
const quoteChars = computed(() => ('「' + quote.text + '」').split(''))

// 签到成功的淡金色星火粒子：从按钮位置缓慢向上飘散（确定性参数，避免重渲染跳动）
interface Spark {
  left: number
  delay: number
  dur: number
  size: number
  color: string
  dx: number
  rise: number
}
const SPARK_COLORS = ['#d4af37', '#e6c96b', '#f0dc9e', '#c9a227']
const sparkles: Spark[] = Array.from({ length: 18 }, () => ({
  left: 18 + Math.random() * 64,
  delay: Math.random() * 0.7,
  dur: 1.9 + Math.random() * 1.3,
  size: 2.5 + Math.random() * 3,
  color: SPARK_COLORS[Math.floor(Math.random() * SPARK_COLORS.length)],
  dx: (Math.random() - 0.5) * 46,
  rise: 110 + Math.random() * 90,
}))
const sparkleOn = ref(false)
let sparkleStartTimer: number | null = null
let sparkleEndTimer: number | null = null

/** 星火粒子触发：先归零再下一帧启动，保证重复触发时动画重启 */
function fireSparkles() {
  if (sparkleStartTimer) clearTimeout(sparkleStartTimer)
  if (sparkleEndTimer) clearTimeout(sparkleEndTimer)
  sparkleOn.value = false
  sparkleStartTimer = window.setTimeout(() => {
    sparkleOn.value = true
    sparkleEndTimer = window.setTimeout(() => {
      sparkleOn.value = false
    }, 3600)
  }, 50)
}

onUnmounted(() => {
  if (sparkleStartTimer) clearTimeout(sparkleStartTimer)
  if (sparkleEndTimer) clearTimeout(sparkleEndTimer)
})

function onCheckIn() {
  if (checkedToday.value) return
  const result = checkInToday()
  if (!result) {
    checkedToday.value = true
    return
  }
  checkInState.value = result
  checkedToday.value = true
  addPoints(SIGN_IN_POINTS)
  levelInfo.value = getLevelInfo()
  playCorrectSound()
  fireSparkles()
}

// ===== 每日签到弹窗 =====
const checkInModalVisible = ref(false)

function openCheckIn() {
  // 打开时刷新签到状态，处理页面跨天未刷新的情况
  checkInState.value = getCheckIn()
  checkedToday.value = isCheckedToday()
  checkInModalVisible.value = true
  playTapSound()
}

function closeCheckIn() {
  checkInModalVisible.value = false
}

/** 头部展示用户：等级由本地积分实时解析，昵称/签名/头像用本地资料覆盖 */
const user = computed(() => ({
  ...mockUser,
  nickname: profile.value.nickname,
  slogan: profile.value.slogan,
  level: levelInfo.value.level,
  levelName: levelInfo.value.levelName,
}))

const medals = mockMedals
const footprintGroups = mockFootprints
const orders = ref(mockOrders)

const orderStatusColor: Record<OrderItem['status'], string> = {
  pending: '#faad14',
  paid: '#2f7cf6',
  shipped: '#fa8c16',
  done: '#52c41a',
  refund: '#8c8c8c',
}

// 订单 Tab 筛选
type OrderTab = 'all' | OrderItem['status']
const orderTabs: { key: OrderTab; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待付款' },
  { key: 'paid', label: '待发货' },
  { key: 'shipped', label: '待收货' },
  { key: 'done', label: '已完成' },
]
const activeTab = ref<OrderTab>('all')
const filteredOrders = computed(() =>
  activeTab.value === 'all'
    ? orders.value
    : orders.value.filter((o) => o.status === activeTab.value),
)

const allStats = computed(() => user.value.stats)

// ===== 游览足迹：按日期查看 =====
/** 日期筛选的"全部"哨兵值 */
const FOOTPRINT_ALL = 'all'
const footprintDate = ref<string>(FOOTPRINT_ALL)
/** 当前筛选出的足迹分组（选"全部"则按日期分组全展示） */
const visibleFootprintGroups = computed(() =>
  footprintDate.value === FOOTPRINT_ALL
    ? footprintGroups
    : footprintGroups.filter((g) => g.date === footprintDate.value),
)
const footprintTotal = computed(() =>
  footprintGroups.reduce((sum, g) => sum + g.items.length, 0),
)

function selectFootprintDate(date: string) {
  if (footprintDate.value === date) return
  playTapSound()
  footprintDate.value = date
}

function onFootprintAction(to?: string) {
  if (to) router.push(to)
  else showToast('更多足迹详情开发中，敬请期待')
}

/** 足迹卡片整卡点击：有跳转目标才响应 */
function onFootprintCard(to?: string) {
  if (to) {
    playTapSound()
    router.push(to)
  }
}

function medalColor(m: MedalItem) {
  if (!m.earned) return '#bfbfbf'
  return m.color ?? '#c41e3a'
}

// ===== 勋章详情弹窗 =====
const selectedMedal = ref<MedalItem | null>(null)

function openMedal(m: MedalItem) {
  selectedMedal.value = m
  if (m.earned) playCorrectSound()
  else playTapSound()
}

function closeMedal() {
  selectedMedal.value = null
}

// Esc 关闭勋章弹窗（键盘可达性）
function onMedalKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && selectedMedal.value) closeMedal()
  if (e.key === 'Escape' && levelModalVisible.value) closeLevel()
  if (e.key === 'Escape' && checkInModalVisible.value) closeCheckIn()
}
window.addEventListener('keydown', onMedalKeydown)
onUnmounted(() => window.removeEventListener('keydown', onMedalKeydown))

// ===== 等级说明弹窗 =====
const levelModalVisible = ref(false)

function openLevel() {
  // 打开前刷新积分，反映其他页面的加分
  levelInfo.value = getLevelInfo()
  levelModalVisible.value = true
  playTapSound()
}

function closeLevel() {
  levelModalVisible.value = false
}

function goAchievements() {
  closeMedal()
  router.push('/interactive')
}

// ===== 设置面板（底部弹层，内含资料编辑子视图）=====
type PanelView = 'main' | 'avatar' | 'nickname' | 'slogan' | 'font'

const FONT_OPTIONS: { key: FontScale; label: string; desc: string }[] = [
  { key: 'normal', label: '标准', desc: '默认字号' },
  { key: 'large', label: '大号', desc: '放大 12%' },
  { key: 'xlarge', label: '特大', desc: '放大 25%' },
]

function fontLabelOf(key: FontScale): string {
  return FONT_OPTIONS.find((o) => o.key === key)?.label ?? '标准'
}
const panelVisible = ref(false)
const panelView = ref<PanelView>('main')
const toastText = ref('')
let toastTimer: ReturnType<typeof setTimeout> | null = null

function showToast(text: string) {
  toastText.value = text
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toastText.value = ''), 1800)
}

function onSettings() {
  panelView.value = 'main'
  panelVisible.value = true
  playTapSound()
}

function closePanel() {
  panelVisible.value = false
}

function openView(v: PanelView) {
  if (v === 'nickname') {
    editNick.value = profile.value.nickname
    nickError.value = ''
  } else if (v === 'slogan') {
    editSlogan.value = profile.value.slogan
    sloganError.value = ''
  }
  panelView.value = v
  playTapSound()
}

// ===== 昵称 / 签名编辑 =====
const NICK_MAX = 12
const SLOGAN_MAX = 20
const editNick = ref(profile.value.nickname)
const nickError = ref('')
const editSlogan = ref(profile.value.slogan)
const sloganError = ref('')

function saveNickname() {
  const v = editNick.value.trim()
  if (!v) {
    nickError.value = '昵称不能为空'
    playWrongSound()
    return
  }
  if (v.length > NICK_MAX) {
    nickError.value = `昵称最多 ${NICK_MAX} 个字`
    playWrongSound()
    return
  }
  profile.value = saveProfile({ nickname: v })
  playCorrectSound()
  panelView.value = 'main'
}

function saveSlogan() {
  const v = editSlogan.value.trim()
  if (v.length > SLOGAN_MAX) {
    sloganError.value = `个性签名最多 ${SLOGAN_MAX} 个字`
    playWrongSound()
    return
  }
  profile.value = saveProfile({ slogan: v || '这个人很神秘，什么都没留下' })
  playCorrectSound()
  panelView.value = 'main'
}

// ===== 头像选择：预设 + 本地相册 =====
const avatarPresets = AVATAR_PRESETS
const fileInput = ref<HTMLInputElement | null>(null)
const uploadingAvatar = ref(false)

function pickPreset(i: number) {
  if (profile.value.presetIndex === i && !profile.value.avatar) return
  profile.value = saveProfile({ presetIndex: i, avatar: '' })
  playCorrectSound()
}

function triggerUpload() {
  playTapSound()
  fileInput.value?.click()
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadingAvatar.value = true
  try {
    const dataUrl = await fileToAvatarDataUrl(file)
    profile.value = saveProfile({ avatar: dataUrl })
    playCorrectSound()
    showToast('头像已更新')
  } catch (err) {
    showToast(err instanceof Error ? err.message : '头像设置失败')
    playWrongSound()
  } finally {
    uploadingAvatar.value = false
    // 清空 value 才能重复选择同一文件
    input.value = ''
  }
}

// ===== 通用设置 =====
function toggleSound() {
  const next = !settings.value.soundEnabled
  settings.value = saveSettings({ soundEnabled: next })
  // 打开时给一次即时反馈；关闭时自然静音
  if (next) playCorrectSound()
}

/** 切换字体档位：立即对整站生效并持久化，刷新后保持 */
function pickFont(key: FontScale) {
  if (settings.value.fontScale === key) return
  settings.value = saveSettings({ fontScale: key })
  applyFontScale(key)
  playCorrectSound()
}

const qaCount = computed(() => listSessions().length)

function clearQaHistory() {
  if (qaCount.value === 0) {
    showToast('暂无问答历史')
    return
  }
  if (window.confirm(`确定清空全部 ${qaCount.value} 条问答历史吗？此操作不可恢复。`)) {
    clearSessions()
    showToast('问答历史已清空')
    playTapSound()
  }
}

async function onLogout() {
  // 二次确认，防止误触直接退出
  if (!window.confirm('确定要退出登录吗？')) return
  panelVisible.value = false
  // 通知后端把 token 写入 Redis 黑名单，并清空本地会话
  await userStore.logout()
  router.replace('/auth')
}
</script>

<template>
  <div class="profile">
    <!-- 顶部红色用户区（全宽，顶到状态栏下） -->
    <div class="profile__header">
      <div class="profile__actions">
        <button class="profile__icon-btn" title="每日签到" @click="openCheckIn">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="4" width="18" height="18" rx="2" />
            <path d="M16 2v4M8 2v4M3 10h18" />
          </svg>
        </button>
        <button class="profile__icon-btn" title="设置" @click="onSettings">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3" />
            <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.6 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.6a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09A1.65 1.65 0 0015 4.6a1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9c.24.56.81.96 1.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z" />
          </svg>
        </button>
      </div>
      <div class="profile__user">
        <div class="profile__avatar">
          <UserAvatar :profile="profile" :size="52" />
        </div>
        <div class="profile__user-info">
          <div class="profile__nickname">{{ user.nickname }}</div>
          <div class="profile__slogan">{{ user.slogan }}</div>
          <div class="profile__badges">
            <span
              class="profile__badge profile__badge--level"
              role="button"
              tabindex="0"
              aria-label="查看等级说明"
              @click="openLevel"
              @keyup.enter="openLevel"
            >
              Lv {{ user.level }} {{ user.levelName }} ›
            </span>
            <span class="profile__badge profile__badge--medal">
              勋章 {{ user.medals.owned }}/{{ user.medals.total }}
            </span>
          </div>
        </div>
      </div>

      <div class="profile__stats">
        <div v-for="s in allStats" :key="s.label" class="profile__stat">
          <div class="profile__stat-value">{{ s.value }}</div>
          <div class="profile__stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <!-- 荣誉勋章墙 -->
    <section class="profile__card profile__medals">
      <div class="profile__section-title">
        <span>荣誉勋章墙</span>
        <button
          type="button"
          class="profile__section-more-btn"
          @click="router.push('/interactive')"
        >
          个人成就馆 <span class="profile__arrow">›</span>
        </button>
      </div>
      <div class="profile__medal-list">
        <div
          v-for="m in medals"
          :key="m.name"
          class="profile__medal"
          :class="{ 'is-earned': m.earned }"
          role="button"
          tabindex="0"
          @click="openMedal(m)"
          @keyup.enter="openMedal(m)"
        >
          <MedalBadge :icon="m.icon" :earned="m.earned" :color="medalColor(m)" />
          <div class="profile__medal-name">{{ m.name }}</div>
        </div>
      </div>
    </section>

    <!-- 我的订单 -->
    <section class="profile__card profile__orders">
      <div class="profile__section-title">
        <span>我的订单</span>
        <button
          type="button"
          class="profile__section-more-btn"
          @click="showToast('订单中心开发中，敬请期待')"
        >
          查看更多 <span class="profile__arrow">›</span>
        </button>
      </div>
      <div class="profile__order-tabs">
        <span
          v-for="t in orderTabs"
          :key="t.key"
          class="profile__order-tab"
          :class="{ 'is-active': activeTab === t.key }"
          @click="activeTab = t.key"
        >{{ t.label }}</span>
      </div>
      <div class="profile__order-list">
        <div v-if="filteredOrders.length" v-for="o in filteredOrders" :key="o.id" class="profile__order-item">
          <div class="profile__order-cover">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M6 2l1.5 4h9L18 2M4 6h16l-1.5 13a2 2 0 01-2 1.7h-9A2 2 0 015.5 19L4 6z" />
              <path d="M9 10v6M15 10v6" />
            </svg>
          </div>
          <div class="profile__order-body">
            <div class="profile__order-top">
              <span class="profile__order-title">{{ o.title }}</span>
              <span class="profile__order-status" :style="{ color: orderStatusColor[o.status] }">{{ o.statusText }}</span>
            </div>
            <div class="profile__order-spec">{{ o.spec }}</div>
            <div class="profile__order-bottom">
              <span class="profile__order-date">{{ o.date }}</span>
              <span class="profile__order-price">¥{{ o.price }} <small>×{{ o.qty }}</small></span>
            </div>
          </div>
        </div>
        <div v-else class="profile__order-empty">暂无相关订单</div>
      </div>
    </section>

    <!-- 游览足迹：可选择日期查看 -->
    <section class="profile__footprint">
      <div class="profile__section-title">
        <span>游览足迹</span>
        <span class="profile__footprint-total">共 {{ footprintTotal }} 条记录</span>
      </div>

      <div class="profile__footprint-dates">
        <button
          type="button"
          class="profile__footprint-date"
          :class="{ 'is-active': footprintDate === FOOTPRINT_ALL }"
          @click="selectFootprintDate(FOOTPRINT_ALL)"
        >
          全部
        </button>
        <button
          v-for="g in footprintGroups"
          :key="g.date"
          type="button"
          class="profile__footprint-date"
          :class="{ 'is-active': footprintDate === g.date }"
          @click="selectFootprintDate(g.date)"
        >
          {{ g.date }}
        </button>
      </div>

      <div v-for="g in visibleFootprintGroups" :key="g.date" class="profile__footprint-day">
        <div class="profile__footprint-day-label">
          {{ g.date }} · {{ g.items.length }} 条记录
        </div>
        <div
          v-for="item in g.items"
          :key="item.id"
          class="profile__footprint-card"
          :class="{ 'is-clickable': !!item.actionTo }"
          role="button"
          :tabindex="item.actionTo ? 0 : -1"
          @click="onFootprintCard(item.actionTo)"
          @keyup.enter="onFootprintCard(item.actionTo)"
        >
          <div class="profile__footprint-main">
            <div class="profile__footprint-title">{{ item.title }}</div>
            <div class="profile__footprint-meta">{{ item.meta }}</div>
            <div v-if="item.tags?.length" class="profile__footprint-tags">
              <span v-for="t in item.tags" :key="t" class="profile__tag">{{ t }}</span>
            </div>
            <div v-if="item.progress != null" class="profile__progress">
              <div class="profile__progress-bar" :style="{ width: item.progress + '%' }" />
            </div>
          </div>
          <div v-if="item.actionText" class="profile__footprint-action">
            <button class="profile__btn-action" @click="onFootprintAction(item.actionTo)">
              {{ item.actionText }} <span class="profile__arrow-sm">›</span>
            </button>
          </div>
        </div>
      </div>

      <div v-if="!visibleFootprintGroups.length" class="profile__footprint-empty">
        这一天还没有足迹记录
      </div>
    </section>

    <!-- ===== 勋章详情弹窗 ===== -->
    <Transition name="pf-fade">
      <div v-if="selectedMedal" class="pf-medal-modal" @click.self="closeMedal">
        <div
          :key="selectedMedal.name"
          class="pf-medal-card"
          :class="{ 'is-locked': !selectedMedal.earned }"
          role="dialog"
          aria-modal="true"
          :aria-label="`勋章：${selectedMedal.name}`"
        >
          <button class="pf-sheet__close" aria-label="关闭" @click="closeMedal">×</button>

          <MedalBadge
            class="pf-medal-card__badge"
            :icon="selectedMedal.icon"
            :earned="selectedMedal.earned"
            :color="medalColor(selectedMedal)"
            :size="92"
          />

          <h3 class="pf-medal-card__name">{{ selectedMedal.name }}</h3>
          <span
            class="pf-medal-card__status"
            :class="selectedMedal.earned ? 'is-earned' : 'is-locked'"
          >
            {{ selectedMedal.earned ? '已点亮' : '未点亮' }}
          </span>
          <p class="pf-medal-card__desc">
            {{ selectedMedal.desc ?? '继续探索红色之旅，解锁这枚荣誉勋章。' }}
          </p>

          <button type="button" class="pf-primary" @click="goAchievements">
            {{ selectedMedal.earned ? '去个人成就馆' : '去点亮徽章' }}
          </button>
        </div>
      </div>
    </Transition>

    <!-- ===== 每日签到弹窗 ===== -->
    <Transition name="pf-fade">
      <div v-if="checkInModalVisible" class="pf-medal-modal" @click.self="closeCheckIn">
        <div class="pf-checkin-card" role="dialog" aria-modal="true" aria-label="每日签到">
          <!-- 星火暗纹（极低透明度，不抢文字） -->
          <div class="pf-checkin-card__pattern" aria-hidden="true" />

          <button class="pf-sheet__close" aria-label="关闭" @click="closeCheckIn">×</button>

          <!-- 标题区：金星 + 标题；右侧两行签到天数 -->
          <header class="pf-checkin-card__head">
            <div class="pf-checkin-card__brand">
              <svg class="pf-checkin-card__star" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  fill="currentColor"
                  d="M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"
                />
              </svg>
              <h3 class="pf-checkin-card__title">每日签到</h3>
            </div>
            <div class="pf-checkin-card__counts">
              <span>连续 <b>{{ checkInState.streak }}</b> 天</span>
              <span>累计 <b>{{ checkInState.totalDays }}</b> 天</span>
            </div>
          </header>

          <!-- 签到按钮 + 星火粒子层 -->
          <div class="pf-checkin-card__btn-wrap">
            <button
              type="button"
              class="pf-checkin-card__btn"
              :class="{ 'is-done': checkedToday }"
              :disabled="checkedToday"
              @click="onCheckIn"
            >
              <svg
                v-if="checkedToday"
                class="pf-checkin-card__check"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M20 6L9 17l-5-5" />
              </svg>
              {{ checkedToday ? '今日已签到' : `立即签到 · +${SIGN_IN_POINTS} 积分` }}
            </button>
            <div v-if="sparkleOn" class="pf-checkin-card__sparkles" aria-hidden="true">
              <i
                v-for="(s, i) in sparkles"
                :key="i"
                :style="{
                  left: s.left + '%',
                  width: s.size + 'px',
                  height: s.size + 'px',
                  background: s.color,
                  animationDelay: s.delay + 's',
                  animationDuration: s.dur + 's',
                  '--dx': s.dx + 'px',
                  '--dy': -s.rise + 'px',
                }"
              />
            </div>
          </div>

          <!-- 名言引用卡片：逐字淡入 -->
          <Transition name="pf-fade">
            <blockquote v-if="checkedToday" class="pf-checkin-card__quote">
              <p class="pf-checkin-card__quote-text">
                <span
                  v-for="(ch, i) in quoteChars"
                  :key="i"
                  :style="{ animationDelay: 0.15 + i * 0.05 + 's' }"
                >{{ ch }}</span>
              </p>
              <footer
                class="pf-checkin-card__quote-author"
                :style="{ animationDelay: 0.15 + quoteChars.length * 0.05 + 0.1 + 's' }"
              >—— {{ quote.author }}</footer>
            </blockquote>
          </Transition>
        </div>
      </div>
    </Transition>

    <!-- ===== 等级说明弹窗 ===== -->
    <Transition name="pf-fade">
      <div v-if="levelModalVisible" class="pf-medal-modal" @click.self="closeLevel">
        <div class="pf-level-card" role="dialog" aria-modal="true" aria-label="等级说明">
          <button class="pf-sheet__close" aria-label="关闭" @click="closeLevel">×</button>

          <!-- 等级头图 -->
          <div class="pf-level-card__hero">
            <div class="pf-level-card__lv">Lv {{ levelInfo.level }}</div>
            <div class="pf-level-card__name">{{ levelInfo.levelName }}</div>
            <div class="pf-level-card__points">累计积分 {{ levelInfo.points }}</div>
          </div>

          <!-- 升级进度 -->
          <div class="pf-level-progress">
            <div class="pf-level-progress__bar">
              <i :style="{ width: (levelInfo.progress * 100).toFixed(1) + '%' }" />
            </div>
            <p class="pf-level-progress__text">
              <template v-if="levelInfo.nextNeed != null">
                再获得 <strong>{{ levelInfo.remaining }}</strong> 积分升级到「{{ levelInfo.nextName }}」
              </template>
              <template v-else>已达最高等级，感谢你点亮红色之旅</template>
            </p>
          </div>

          <!-- 获取途径 -->
          <div class="pf-level-ways">
            <span class="pf-level-ways__title">如何获得积分</span>
            <div class="pf-level-ways__row">
              <span>每日签到</span><b>+10</b>
            </div>
            <div class="pf-level-ways__row">
              <span>通关沉浸式红色剧本</span><b>+30</b>
            </div>
            <div class="pf-level-ways__row">
              <span>导览到达景点打卡</span><b>+5</b>
            </div>
            <div class="pf-level-ways__row">
              <span>红色答题（即将上线）</span><b>+10</b>
            </div>
          </div>

          <!-- 等级阶梯 -->
          <div class="pf-level-rules">
            <span class="pf-level-rules__title">等级阶梯</span>
            <div
              v-for="r in LEVEL_RULES"
              :key="r.level"
              class="pf-level-rule"
              :class="{ 'is-current': r.level === levelInfo.level, 'is-reached': r.level < levelInfo.level }"
              :title="r.perk"
            >
              <span class="pf-level-rule__lv">Lv{{ r.level }}</span>
              <span class="pf-level-rule__name">{{ r.name }}</span>
              <span class="pf-level-rule__perk">{{ r.perk }}</span>
              <span class="pf-level-rule__need">{{ r.need }} 积分</span>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- ===== 设置底部弹层 ===== -->
    <Transition name="pf-fade">
      <div v-if="panelVisible" class="pf-overlay" @click.self="closePanel">
        <div class="pf-sheet" role="dialog" aria-modal="true" aria-label="设置">
          <!-- 主设置 -->
          <template v-if="panelView === 'main'">
            <div class="pf-sheet__head">
              <span class="pf-sheet__title">设置</span>
              <button class="pf-sheet__close" aria-label="关闭" @click="closePanel">×</button>
            </div>

            <div class="pf-group">
              <div class="pf-group__label">个人资料</div>
              <div class="pf-row" role="button" tabindex="0" @click="openView('avatar')" @keyup.enter="openView('avatar')">
                <span class="pf-row__label">头像</span>
                <span class="pf-row__right">
                  <UserAvatar :profile="profile" :size="38" />
                  <span class="pf-row__chevron">›</span>
                </span>
              </div>
              <div class="pf-row" role="button" tabindex="0" @click="openView('nickname')" @keyup.enter="openView('nickname')">
                <span class="pf-row__label">昵称</span>
                <span class="pf-row__right">
                  <span class="pf-row__value">{{ profile.nickname }}</span>
                  <span class="pf-row__chevron">›</span>
                </span>
              </div>
              <div class="pf-row" role="button" tabindex="0" @click="openView('slogan')" @keyup.enter="openView('slogan')">
                <span class="pf-row__label">个性签名</span>
                <span class="pf-row__right">
                  <span class="pf-row__value pf-row__value--ellipsis">{{ profile.slogan }}</span>
                  <span class="pf-row__chevron">›</span>
                </span>
              </div>
            </div>

            <div class="pf-group">
              <div class="pf-group__label">通用</div>
              <div class="pf-row">
                <span class="pf-row__label">音效</span>
                <button
                  class="pf-switch"
                  :class="{ 'is-on': settings.soundEnabled }"
                  role="switch"
                  :aria-checked="settings.soundEnabled"
                  :aria-label="settings.soundEnabled ? '关闭音效' : '开启音效'"
                  @click="toggleSound"
                >
                  <i />
                </button>
              </div>
              <div class="pf-row" role="button" tabindex="0" @click="openView('font')" @keyup.enter="openView('font')">
                <span class="pf-row__label">字体大小</span>
                <span class="pf-row__right">
                  <span class="pf-row__hint">{{ fontLabelOf(settings.fontScale) }}</span>
                  <span class="pf-row__chevron">›</span>
                </span>
              </div>
              <div class="pf-row" role="button" tabindex="0" @click="clearQaHistory" @keyup.enter="clearQaHistory">
                <span class="pf-row__label">清除问答历史</span>
                <span class="pf-row__right">
                  <span class="pf-row__hint">{{ qaCount }} 条</span>
                  <span class="pf-row__chevron">›</span>
                </span>
              </div>
              <div class="pf-row pf-row--danger" role="button" tabindex="0" @click="onLogout" @keyup.enter="onLogout">
                <span class="pf-row__label">退出登录</span>
                <span class="pf-row__chevron">›</span>
              </div>
            </div>
          </template>

          <!-- 头像选择 -->
          <template v-else-if="panelView === 'avatar'">
            <div class="pf-sheet__head">
              <button class="pf-sheet__back" aria-label="返回" @click="openView('main')">‹</button>
              <span class="pf-sheet__title">更换头像</span>
              <span class="pf-sheet__spacer" />
            </div>
            <div class="pf-avatar-grid">
              <button
                v-for="(p, i) in avatarPresets"
                :key="i"
                type="button"
                class="pf-avatar-cell"
                :class="{ 'is-active': !profile.avatar && profile.presetIndex === i }"
                @click="pickPreset(i)"
              >
                <UserAvatar :profile="{ avatar: '', presetIndex: i }" :size="56" />
              </button>
              <button
                type="button"
                class="pf-avatar-cell pf-avatar-upload"
                :disabled="uploadingAvatar"
                @click="triggerUpload"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                  stroke-linecap="round" stroke-linejoin="round">
                  <path d="M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2z" />
                  <circle cx="12" cy="13" r="4" />
                </svg>
                <span>{{ uploadingAvatar ? '处理中…' : '相册选择' }}</span>
              </button>
            </div>
            <p class="pf-tip">选择预设头像，或从相册上传图片（自动居中裁剪）</p>
            <input
              ref="fileInput"
              type="file"
              accept="image/*"
              class="pf-hidden-file"
              @change="onFileChange"
            />
          </template>

          <!-- 修改昵称 -->
          <template v-else-if="panelView === 'nickname'">
            <div class="pf-sheet__head">
              <button class="pf-sheet__back" aria-label="返回" @click="openView('main')">‹</button>
              <span class="pf-sheet__title">修改昵称</span>
              <span class="pf-sheet__spacer" />
            </div>
            <div class="pf-form">
              <div class="pf-input-wrap">
                <input
                  v-model="editNick"
                  class="pf-input"
                  type="text"
                  maxlength="12"
                  placeholder="请输入昵称"
                  @keyup.enter="saveNickname"
                />
                <span class="pf-input-count">{{ editNick.length }}/{{ NICK_MAX }}</span>
              </div>
              <p v-if="nickError" class="pf-error">{{ nickError }}</p>
              <button type="button" class="pf-primary" @click="saveNickname">保存</button>
            </div>
          </template>

          <!-- 修改个性签名 -->
          <template v-else-if="panelView === 'slogan'">
            <div class="pf-sheet__head">
              <button class="pf-sheet__back" aria-label="返回" @click="openView('main')">‹</button>
              <span class="pf-sheet__title">个性签名</span>
              <span class="pf-sheet__spacer" />
            </div>
            <div class="pf-form">
              <div class="pf-input-wrap pf-input-wrap--col">
                <textarea
                  v-model="editSlogan"
                  class="pf-textarea"
                  maxlength="20"
                  rows="3"
                  placeholder="写一句介绍自己的话吧"
                />
                <span class="pf-input-count">{{ editSlogan.length }}/{{ SLOGAN_MAX }}</span>
              </div>
              <p v-if="sloganError" class="pf-error">{{ sloganError }}</p>
              <button type="button" class="pf-primary" @click="saveSlogan">保存</button>
            </div>
          </template>

          <!-- 字体大小（适老模式）-->
          <template v-else>
            <div class="pf-sheet__head">
              <button class="pf-sheet__back" aria-label="返回" @click="openView('main')">‹</button>
              <span class="pf-sheet__title">字体大小</span>
              <span class="pf-sheet__spacer" />
            </div>

            <!-- 预览：随当前档位实时放大，方便老年用户直观对比 -->
            <div class="pf-font-preview">
              <div class="pf-font-preview__title">预览效果</div>
              <div class="pf-font-preview__body">
                欢迎来到红色文旅，开启您的研学之旅。
              </div>
              <div class="pf-font-preview__small">智能导览 · AI 问答 · 红色剧本</div>
            </div>

            <div class="pf-font-options">
              <button
                v-for="o in FONT_OPTIONS"
                :key="o.key"
                type="button"
                class="pf-font-option"
                :class="{ 'is-active': settings.fontScale === o.key }"
                @click="pickFont(o.key)"
              >
                <span class="pf-font-option__label" :class="`pf-font-option__label--${o.key}`">
                  A
                </span>
                <span class="pf-font-option__name">{{ o.label }}</span>
                <span class="pf-font-option__desc">{{ o.desc }}</span>
              </button>
            </div>
            <p class="pf-tip">大号/特大字将整体放大页面内容，设置后对所有页面生效</p>
          </template>
        </div>

        <!-- 轻提示 -->
        <Transition name="pf-toast">
          <div v-if="toastText" class="pf-toast">{{ toastText }}</div>
        </Transition>
      </div>
    </Transition>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.profile {
  // 抵消 Layout main 的 16px 内边距，让红色头部铺满到顶
  margin: -@spacing-md;
  padding-bottom: @spacing-md;

  &__header {
    position: relative;
    background: linear-gradient(160deg, @color-primary 0%, @color-primary-active 100%);
    padding: calc(env(safe-area-inset-top) + 18px) @spacing-md @spacing-lg;
    border-bottom-left-radius: 24px;
    border-bottom-right-radius: 24px;
    color: #fff;
  }

  &__actions {
    position: absolute;
    top: calc(env(safe-area-inset-top) + 12px);
    right: @spacing-md;
    display: flex;
    gap: @spacing-sm;
  }

  &__icon-btn {
    width: 34px;
    height: 34px;
    border-radius: 50%;
    border: none;
    background: rgba(255, 255, 255, 0.18);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: background 0.15s;

    svg {
      width: 18px;
      height: 18px;
    }

    &:active {
      background: rgba(255, 255, 255, 0.3);
    }
  }

  &__user {
    display: flex;
    align-items: flex-start;
    gap: @spacing-md;
  }

  &__avatar {
    flex-shrink: 0;
    width: 56px;
    height: 56px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.22);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 2px solid rgba(255, 255, 255, 0.5);

    svg {
      width: 32px;
      height: 32px;
    }
  }

  &__user-info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__nickname {
    font-size: 20px;
    font-weight: 700;
    line-height: 1.2;
  }

  &__slogan {
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.85);
  }

  &__badges {
    display: flex;
    flex-wrap: wrap;
    gap: @spacing-sm;
    margin-top: @spacing-xs;
  }

  &__badge {
    font-size: @font-size-sm;
    line-height: 1;
    padding: 4px 10px;
    border-radius: 10px;
    white-space: nowrap;
  }

  &__badge--level {
    background: linear-gradient(90deg, #f5cd6b, @color-accent);
    color: #7a5a10;
    font-weight: 700;
    cursor: pointer;
    transition: transform 0.15s ease;

    &:active {
      transform: scale(0.94);
    }
  }

  &__badge--medal {
    background: rgba(255, 255, 255, 0.18);
    color: #fff;
    font-weight: 600;
  }

  // ===== 每日签到弹窗（卡片样式见 .pf-checkin-card） =====

  &__stats {
    display: flex;
    gap: @spacing-sm;
    margin-top: @spacing-lg;
  }

  &__stat {
    flex: 1;
    background: rgba(255, 255, 255, 0.16);
    border-radius: @radius-lg;
    padding: @spacing-sm 0;
    text-align: center;
    backdrop-filter: blur(6px);
  }

  &__stat-value {
    font-size: 22px;
    font-weight: 800;
    line-height: 1.1;
    color: #fff;
  }

  &__stat-label {
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.82);
    margin-top: 2px;
  }

  // ===== 通用卡片 =====
  &__card {
    background: @color-bg-card;
    margin: @spacing-md;
    border-radius: @radius-lg;
    box-shadow: @shadow-card;
  }

  &__section-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: @spacing-md;
    font-size: @font-size-lg;
    font-weight: 700;
    color: @color-text-primary;
  }

  &__arrow {
    color: @color-text-secondary;
    font-size: 20px;
    line-height: 1;
  }

  // 区块标题右侧"更多"按钮（勋章墙 → 互动中心 / 订单 → toast）
  &__section-more-btn {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    padding: 4px 0 4px 8px;
    border: none;
    background: transparent;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;

    &:active {
      color: @color-primary;
    }
  }

  // ===== 勋章 =====
  // 与订单/足迹一致：直接在卡片底色上排布，不再叠加一层"展柜底"
  &__medal-list {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: @spacing-md @spacing-sm;
    padding: 0 @spacing-md @spacing-md;
  }

  &__medal {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: @spacing-xs;
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;

    &:active :deep(.medal-badge__disc) {
      transform: scale(0.94);
    }
  }

  // 已点亮勋章的名字加重，未点亮保持灰色，形成主次
  &__medal.is-earned &__medal-name {
    color: @color-text-primary;
    font-weight: 600;
  }

  &__medal-name {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    text-align: center;
  }

  // ===== 订单 =====
  &__order-tabs {
    display: flex;
    gap: @spacing-sm;
    padding: 0 @spacing-md @spacing-md;
    border-bottom: 1px solid #f2f2f4;
    margin: 0 @spacing-md;
  }

  &__order-tab {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    padding: 4px 0;
    position: relative;
    cursor: pointer;

    &.is-active {
      color: @color-primary;
      font-weight: 600;

      &::after {
        content: '';
        position: absolute;
        left: 50%;
        bottom: -9px;
        transform: translateX(-50%);
        width: 16px;
        height: 2px;
        border-radius: 2px;
        background: @color-primary;
      }
    }
  }

  &__order-list {
    padding: @spacing-xs @spacing-md @spacing-md;
  }

  &__order-item {
    display: flex;
    gap: @spacing-md;
    padding: @spacing-md 0;
    border-bottom: 1px solid #f5f5f7;

    &:last-child {
      border-bottom: none;
    }
  }

  &__order-cover {
    flex-shrink: 0;
    width: 56px;
    height: 56px;
    border-radius: @radius-base;
    background: @color-bg-page;
    color: @color-primary;
    display: flex;
    align-items: center;
    justify-content: center;

    svg {
      width: 30px;
      height: 30px;
    }
  }

  &__order-body {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__order-top {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: @spacing-sm;
  }

  &__order-title {
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__order-status {
    font-size: @font-size-sm;
    font-weight: 600;
    flex-shrink: 0;
  }

  &__order-spec {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__order-bottom {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: auto;
    padding-top: @spacing-xs;
  }

  &__order-date {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__order-price {
    font-size: @font-size-base;
    font-weight: 700;
    color: @color-primary;

    small {
      font-size: @font-size-sm;
      font-weight: 400;
      color: @color-text-secondary;
    }
  }

  &__order-empty {
    text-align: center;
    color: @color-text-secondary;
    font-size: @font-size-sm;
    padding: @spacing-lg 0;
  }

  // ===== 足迹 =====
  &__footprint {
    margin-top: @spacing-sm;
  }

  &__footprint-total {
    font-size: @font-size-sm;
    font-weight: 400;
    color: @color-text-secondary;
  }

  // 日期选择：横向可滑动，选中主色实底
  &__footprint-dates {
    display: flex;
    gap: @spacing-sm;
    margin: 0 @spacing-md @spacing-md;
    padding-bottom: 2px;
    overflow-x: auto;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  &__footprint-date {
    flex: 0 0 auto;
    padding: 6px 14px;
    border: 1px solid @color-border;
    border-radius: 16px;
    background: @color-bg-card;
    font-size: @font-size-sm;
    color: @color-text-regular;
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;

    &:active {
      border-color: fade(@color-primary, 40%);
      color: @color-primary;
    }

    &.is-active {
      border-color: @color-primary;
      background: @color-primary;
      color: #fff;
      font-weight: 600;
    }
  }

  &__footprint-day-label {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0 @spacing-md @spacing-sm;
    font-size: @font-size-sm;
    font-weight: 600;
    color: @color-text-secondary;

    &::before {
      content: '';
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: @color-primary;
      opacity: 0.55;
    }
  }

  &__footprint-empty {
    margin: 0 @spacing-md @spacing-md;
    padding: @spacing-lg 0;
    border-radius: @radius-lg;
    background: @color-bg-card;
    box-shadow: @shadow-card;
    text-align: center;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__footprint-card {
    margin: 0 @spacing-md @spacing-md;
    background: @color-bg-card;
    border-radius: @radius-lg;
    padding: @spacing-md;
    box-shadow: @shadow-card;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: @spacing-md;
    -webkit-tap-highlight-color: transparent;

    &.is-clickable {
      cursor: pointer;

      &:active {
        transform: scale(0.99);
      }
    }
  }

  &__footprint-main {
    flex: 1;
    min-width: 0;
  }

  &__footprint-title {
    font-size: @font-size-lg;
    font-weight: 700;
    color: @color-text-primary;
    margin-bottom: @spacing-xs;
  }

  &__footprint-meta {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    margin-bottom: @spacing-sm;
  }

  &__footprint-tags {
    display: flex;
    flex-wrap: wrap;
    gap: @spacing-xs;
  }

  &__tag {
    font-size: @font-size-sm;
    line-height: 1;
    padding: 4px 10px;
    border-radius: 10px;
    background: @color-primary-light;
    color: @color-primary;
    font-weight: 500;
  }

  &__progress {
    height: 6px;
    background: #f0f0f2;
    border-radius: 3px;
    overflow: hidden;
    margin-top: 2px;
  }

  &__progress-bar {
    height: 100%;
    background: linear-gradient(90deg, @color-primary, @color-warning);
    border-radius: 3px;
    transition: width 0.4s;
  }

  &__footprint-action {
    flex-shrink: 0;
  }

  &__btn-action {
    border: none;
    background: @color-primary-light;
    color: @color-primary;
    font-size: @font-size-sm;
    font-weight: 600;
    padding: 6px 12px;
    border-radius: 14px;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 2px;

    &:active {
      background: fade(@color-primary, 18%);
    }
  }

  &__arrow-sm {
    font-size: 16px;
    line-height: 1;
  }
}

// ===== 设置底部弹层 =====
.pf-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(2px);
}

.pf-sheet {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  max-height: 82%;
  overflow-y: auto;
  background: @color-bg-page;
  border-radius: 20px 20px 0 0;
  padding: 0 @spacing-md calc(@spacing-md + env(safe-area-inset-bottom));
  animation: pf-sheet-in 0.28s cubic-bezier(0.22, 0.8, 0.36, 1);
}

@keyframes pf-sheet-in {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

.pf-fade-enter-active,
.pf-fade-leave-active {
  transition: opacity 0.25s;
}
.pf-fade-enter-from,
.pf-fade-leave-to {
  opacity: 0;
}

.pf-sheet__head {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: @spacing-md 0;
  background: @color-bg-page;
}

.pf-sheet__title {
  font-size: 17px;
  font-weight: 700;
  color: @color-text-primary;
}

.pf-sheet__close,
.pf-sheet__back {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: #f0f0f2;
  color: @color-text-regular;
  font-size: 20px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;

  &:active {
    background: #e4e4e8;
  }
}

.pf-sheet__spacer {
  width: 32px;
}

// 分组与行
.pf-group {
  background: @color-bg-card;
  border-radius: @radius-lg;
  margin-bottom: @spacing-md;
  overflow: hidden;
}

.pf-group__label {
  font-size: @font-size-sm;
  color: @color-text-secondary;
  padding: @spacing-sm @spacing-md 0;
}

.pf-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: @spacing-md;
  padding: @spacing-md;
  cursor: pointer;
  min-height: 56px;
  box-sizing: border-box;

  & + & {
    border-top: 1px solid #f5f5f7;
  }
  &:active {
    background: #fafafc;
  }

  &__label {
    font-size: @font-size-lg;
    color: @color-text-primary;
  }

  &__right {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
  }

  &__value {
    font-size: @font-size-base;
    color: @color-text-secondary;
    max-width: 180px;
  }

  &__value--ellipsis {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__hint {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__chevron {
    color: #c0c0c6;
    font-size: 20px;
    line-height: 1;
  }

  &--danger &__label {
    color: @color-primary;
  }
}

// 开关
.pf-switch {
  width: 46px;
  height: 26px;
  border-radius: 13px;
  border: none;
  padding: 2px;
  background: #d8d8dd;
  display: flex;
  align-items: center;
  cursor: pointer;
  transition: background 0.2s;

  i {
    display: block;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: #fff;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
    transition: transform 0.2s;
  }

  &.is-on {
    background: @color-primary;
    i {
      transform: translateX(20px);
    }
  }
}

// 头像选择
.pf-avatar-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: @spacing-md @spacing-sm;
  padding: @spacing-sm 0 @spacing-md;
}

.pf-avatar-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: @spacing-sm 0;
  border: 2px solid transparent;
  border-radius: @radius-lg;
  background: @color-bg-card;
  cursor: pointer;
  font-size: @font-size-sm;
  color: @color-text-secondary;
  transition: border-color 0.15s, transform 0.15s;

  &:active {
    transform: scale(0.95);
  }

  &.is-active {
    border-color: @color-primary;
    color: @color-primary;
    font-weight: 600;
  }

  svg {
    width: 26px;
    height: 26px;
  }
}

.pf-avatar-upload {
  color: @color-primary;
  border-style: dashed;
  border-color: fade(@color-primary, 35%);

  &:disabled {
    opacity: 0.6;
  }
}

.pf-tip {
  margin: 0 0 @spacing-sm;
  font-size: @font-size-sm;
  color: @color-text-secondary;
  text-align: center;
}

// 字体大小预览与选项
.pf-font-preview {
  background: @color-bg-card;
  border-radius: @radius-lg;
  padding: @spacing-md;
  margin-bottom: @spacing-md;
  box-shadow: @shadow-card;

  &__title {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    margin-bottom: @spacing-sm;
  }

  &__body {
    font-size: @font-size-lg;
    line-height: 1.6;
    color: @color-text-primary;
  }

  &__small {
    margin-top: @spacing-xs;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }
}

.pf-font-options {
  display: flex;
  gap: @spacing-sm;
  margin-bottom: @spacing-sm;
}

.pf-font-option {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: @spacing-md @spacing-sm;
  border: 2px solid #f0f0f0;
  border-radius: @radius-lg;
  background: @color-bg-card;
  cursor: pointer;
  transition: border-color 0.15s, transform 0.15s;

  &:active {
    transform: scale(0.96);
  }

  &.is-active {
    border-color: @color-primary;
    background: @color-primary-light;
  }

  &__label {
    line-height: 1;
    color: @color-primary;
    font-weight: 700;

    &--normal {
      font-size: 16px;
    }
    &--large {
      font-size: 20px;
    }
    &--xlarge {
      font-size: 24px;
    }
  }

  &__name {
    font-size: @font-size-lg;
    font-weight: 600;
    color: @color-text-primary;
  }

  &__desc {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    white-space: nowrap;
  }
}

.pf-hidden-file {
  display: none;
}

// 表单
.pf-form {
  padding: @spacing-sm 0 @spacing-md;
}

.pf-input-wrap {
  display: flex;
  align-items: center;
  gap: @spacing-sm;
  background: @color-bg-card;
  border: 1px solid #f0f0f0;
  border-radius: @radius-base;
  padding: 0 @spacing-md;

  &--col {
    flex-direction: column;
    align-items: stretch;
    gap: 4px;
    padding: @spacing-sm @spacing-md;
  }

  &:focus-within {
    border-color: fade(@color-primary, 50%);
  }
}

.pf-input {
  flex: 1;
  height: 46px;
  border: none;
  outline: none;
  background: transparent;
  font-size: @font-size-lg;
  color: @color-text-primary;
}

.pf-textarea {
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  font-size: @font-size-lg;
  line-height: 1.5;
  color: @color-text-primary;
  font-family: inherit;
  width: 100%;
}

.pf-input-count {
  flex-shrink: 0;
  font-size: @font-size-sm;
  color: @color-text-secondary;
}

.pf-error {
  margin: @spacing-sm 2px 0;
  font-size: @font-size-sm;
  color: @color-primary;
}

.pf-primary {
  width: 100%;
  margin-top: @spacing-lg;
  height: 46px;
  border: none;
  border-radius: 23px;
  background: linear-gradient(135deg, @color-primary, @color-primary-active);
  color: #fff;
  font-size: @font-size-lg;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(196, 30, 58, 0.25);

  &:active {
    transform: scale(0.98);
  }
}

// 轻提示
.pf-toast {
  position: absolute;
  left: 50%;
  bottom: 12%;
  transform: translateX(-50%);
  padding: 9px 18px;
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  font-size: @font-size-base;
  border-radius: 20px;
  white-space: nowrap;
  pointer-events: none;
}
.pf-toast-enter-active,
.pf-toast-leave-active {
  transition: opacity 0.25s;
}
.pf-toast-enter-from,
.pf-toast-leave-to {
  opacity: 0;
}

// ===== 勋章详情弹窗 =====
.pf-medal-modal {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: @spacing-lg;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(2px);
}

// ===== 等级说明弹窗 =====
.pf-level-card {
  position: relative;
  width: 100%;
  max-width: 320px;
  max-height: 78vh;
  overflow-y: auto;
  padding: 24px @spacing-md 18px;
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
  text-align: center;
  animation: pf-medal-pop 0.35s cubic-bezier(0.22, 1.2, 0.36, 1) both;

  &__hero {
    padding: 16px @spacing-md 14px;
    border-radius: 14px;
    background: linear-gradient(150deg, @color-primary 0%, @color-primary-active 100%);
    color: #fff;
  }
  &__lv {
    font-size: 26px;
    font-weight: 800;
    letter-spacing: 1px;
  }
  &__name {
    margin-top: 2px;
    font-size: @font-size-lg;
    font-weight: 600;
  }
  &__points {
    margin-top: 6px;
    font-size: @font-size-sm;
    opacity: 0.88;
  }
}

.pf-level-progress {
  margin: 14px 0 4px;
  text-align: left;

  &__bar {
    height: 10px;
    border-radius: 5px;
    background: @color-primary-light;
    overflow: hidden;

    i {
      display: block;
      height: 100%;
      border-radius: 5px;
      background: linear-gradient(90deg, @color-primary 0%, #e8555f 100%);
      transition: width 0.6s ease;
    }
  }
  &__text {
    margin-top: 6px;
    font-size: @font-size-sm;
    color: @color-text-secondary;

    strong {
      color: @color-primary;
    }
  }
}

// 积分获取途径
.pf-level-ways {
  margin-top: 12px;
  text-align: left;

  &__title {
    display: block;
    margin-bottom: 4px;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
  }
  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 7px 0;
    border-bottom: 1px dashed @color-border;
    font-size: @font-size-sm;
    color: @color-text-regular;

    b {
      color: @color-primary;
      font-weight: 700;
    }
  }
}

// 等级阶梯
.pf-level-rules {
  margin-top: 14px;
  text-align: left;

  &__title {
    display: block;
    margin-bottom: 6px;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
  }
}
.pf-level-rule {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 10px;
  font-size: @font-size-sm;
  color: @color-text-secondary;

  &__lv {
    width: 38px;
    flex-shrink: 0;
    font-weight: 700;
  }
  &__name {
    flex-shrink: 0;
    font-weight: 600;
    color: @color-text-regular;
  }
  &__perk {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  &__need {
    flex-shrink: 0;
  }

  // 已达成
  &.is-reached {
    .pf-level-rule__lv {
      color: @color-success;
    }
  }
  // 当前等级
  &.is-current {
    background: @color-primary-light;
    color: @color-primary;
    font-weight: 600;

    .pf-level-rule__lv,
    .pf-level-rule__name,
    .pf-level-rule__need {
      color: @color-primary;
    }
  }
}

// ===== 每日签到弹窗 =====
// 庄重典雅风：暗红主色、金色点缀、正文深灰、引用棕褐；动效克制（淡入上移 / 星火飘散 / 逐字浮现）
@pf-checkin-dark-red: #8a1e2b;
@pf-checkin-tan: #9a7b4f;

@keyframes pf-rise-in {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pf-char-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

// 星火粒子：缓慢向上飘散、渐隐，水平轻微漂移
@keyframes pf-spark-float {
  0% {
    opacity: 0;
    transform: translate(0, 0) scale(1) rotate(0deg);
  }
  12% {
    opacity: 0.9;
  }
  100% {
    opacity: 0;
    transform: translate(var(--dx, 0px), var(--dy, -150px)) scale(0.35) rotate(160deg);
  }
}

.pf-checkin-card {
  position: relative;
  width: 100%;
  max-width: 320px;
  padding: 24px @spacing-lg 22px;
  border-radius: 24px;
  background: #ffffff;
  box-shadow:
    0 24px 64px rgba(60, 12, 16, 0.22),
    0 2px 8px rgba(60, 12, 16, 0.08);
  overflow: hidden;
  animation: pf-rise-in 0.28s ease-out both;

  // 星火暗纹：极低透明度的金/红微光点阵，铺满卡片底层
  &__pattern {
    position: absolute;
    inset: 0;
    pointer-events: none;
    background:
      radial-gradient(circle at 22% 26%, rgba(212, 175, 55, 0.1) 0 1.6px, transparent 2.6px),
      radial-gradient(circle at 72% 64%, rgba(138, 30, 43, 0.08) 0 1.4px, transparent 2.4px);
    background-size:
      46px 46px,
      38px 38px;
    background-position:
      0 0,
      19px 23px;
    opacity: 0.5;
  }

  // 标题区：金星 + 暗红标题；右侧两行小字
  &__head {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: @spacing-md;
  }
  &__brand {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  &__star {
    width: 22px;
    height: 22px;
    color: @color-accent;
    flex-shrink: 0;
    filter: drop-shadow(0 1px 2px rgba(212, 175, 55, 0.35));
  }
  &__title {
    margin: 0;
    font-size: 20px;
    font-weight: 800;
    letter-spacing: 1px;
    color: @pf-checkin-dark-red;
  }
  &__counts {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 2px;
    padding-top: 2px;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    white-space: nowrap;

    b {
      color: @pf-checkin-dark-red;
      font-weight: 700;
    }
  }

  // 签到按钮（含粒子层容器）
  &__btn-wrap {
    position: relative;
    z-index: 1;
    margin-top: 18px;
  }
  &__btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    width: 100%;
    border: none;
    border-radius: 24px;
    padding: 13px 0;
    font-size: @font-size-base;
    font-weight: 700;
    letter-spacing: 2px;
    cursor: pointer;
    color: #fff;
    background: linear-gradient(135deg, @color-primary 0%, @pf-checkin-dark-red 100%);
    box-shadow: 0 6px 16px rgba(138, 30, 43, 0.28);
    transition:
      transform 0.2s ease,
      box-shadow 0.2s ease,
      background 0.2s ease;

    // 未签到：hover 淡红放大
    &:not(:disabled):hover {
      transform: scale(1.03);
      box-shadow: 0 8px 22px rgba(138, 30, 43, 0.34);
    }
    &:not(:disabled):active {
      transform: scale(0.97);
    }

    // 已签到：白底 + 灰对勾，不可点击
    &.is-done {
      background: #ffffff;
      border: 1px solid @color-border;
      color: @color-text-secondary;
      box-shadow: none;
      cursor: default;
      letter-spacing: 4px;
    }
  }
  &__check {
    width: 16px;
    height: 16px;
  }

  // 星火粒子层：覆盖按钮上方区域
  &__sparkles {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 18px;
    height: 0;
    pointer-events: none;

    i {
      position: absolute;
      bottom: 0;
      border-radius: 50%;
      opacity: 0;
      animation: pf-spark-float linear both;
    }
  }

  // 名言引用卡片：浅米黄圆角，棕褐文字，作者右下
  &__quote {
    position: relative;
    z-index: 1;
    margin: 16px 0 0;
    padding: 14px 16px 12px;
    border-radius: 14px;
    background: #faf4e8;
    border-left: 3px solid @color-accent;
  }
  &__quote-text {
    margin: 0;
    font-family: 'Kaiti SC', 'STKaiti', 'KaiTi', 'SimSun', serif;
    font-size: @font-size-base;
    line-height: 1.9;
    color: @pf-checkin-tan;

    span {
      display: inline-block;
      opacity: 0;
      animation: pf-char-in 0.45s ease both;
    }
  }
  &__quote-author {
    display: block;
    margin-top: 4px;
    text-align: right;
    font-size: @font-size-sm;
    color: fade(@pf-checkin-tan, 80%);
    animation: pf-char-in 0.5s ease both;
  }
}

.pf-medal-card {
  position: relative;
  width: 100%;
  max-width: 300px;
  padding: 26px @spacing-lg 22px;
  border-radius: 20px;
  background: linear-gradient(180deg, #fffdf7 0%, #ffffff 60%, #fff8ec 100%);
  box-shadow:
    0 20px 60px rgba(0, 0, 0, 0.4),
    0 0 0 1px rgba(212, 175, 55, 0.25);
  text-align: center;
  animation: pf-medal-pop 0.35s cubic-bezier(0.22, 1.2, 0.36, 1) both;

  &.is-locked {
    background: linear-gradient(180deg, #f8f8f8 0%, #ffffff 60%, #f3f3f3 100%);
    box-shadow:
      0 20px 60px rgba(0, 0, 0, 0.32),
      0 0 0 1px rgba(0, 0, 0, 0.06);
  }

  &__badge {
    margin-bottom: @spacing-sm;
  }

  &__name {
    margin: 0 0 8px;
    font-size: 19px;
    font-weight: 700;
    color: #5a4310;
    letter-spacing: 1px;
  }

  &__status {
    display: inline-block;
    padding: 3px 14px;
    border-radius: 12px;
    font-size: @font-size-sm;
    font-weight: 600;

    &.is-earned {
      background: rgba(212, 175, 55, 0.16);
      color: #a07f1c;
    }

    &.is-locked {
      background: rgba(0, 0, 0, 0.06);
      color: @color-text-secondary;
    }
  }

  &__desc {
    margin: 12px 0 0;
    font-size: @font-size-base;
    line-height: 1.7;
    color: @color-text-regular;
  }

  .pf-primary {
    margin-top: @spacing-md;
  }
}

@keyframes pf-medal-pop {
  from {
    opacity: 0;
    transform: scale(0.7);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
