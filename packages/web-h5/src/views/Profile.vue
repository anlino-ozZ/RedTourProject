<script setup lang="ts">
import { computed, ref } from 'vue'
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
import {
  AVATAR_PRESETS,
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
import { playCorrectSound, playTapSound, playWrongSound } from '@/utils/sound'

const router = useRouter()

// ===== 本地资料 & 设置（账号体系接入前仅存本机）=====
const profile = ref<UserProfile>(getProfile())
const settings = ref<AppSettings>(getSettings())

/** 头部展示用户：mock 的等级/统计不变，昵称/签名/头像用本地资料覆盖 */
const user = computed(() => ({
  ...mockUser,
  nickname: profile.value.nickname,
  slogan: profile.value.slogan,
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

function onFootprintAction(to?: string) {
  if (to) router.push(to)
}

function medalColor(m: MedalItem) {
  if (!m.earned) return '#bfbfbf'
  return m.color ?? '#c41e3a'
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

function onLogout() {
  // TODO: 接入登出接口 / 清 token
  panelVisible.value = false
  router.push('/auth')
}
</script>

<template>
  <div class="profile">
    <!-- 顶部红色用户区（全宽，顶到状态栏下） -->
    <div class="profile__header">
      <div class="profile__actions">
        <button class="profile__icon-btn" title="设置" @click="onSettings">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3" />
            <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.6 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.6a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09A1.65 1.65 0 0015 4.6a1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9c.24.56.81.96 1.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z" />
          </svg>
        </button>
        <button class="profile__icon-btn" title="退出登录" @click="onLogout">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4" />
            <path d="M16 17l5-5-5-5" />
            <path d="M21 12H9" />
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
            <span class="profile__badge profile__badge--level">
              Lv {{ user.level }} {{ user.levelName }}
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
        <span class="profile__arrow">›</span>
      </div>
      <div class="profile__medal-list">
        <div v-for="m in medals" :key="m.name" class="profile__medal">
          <div class="profile__medal-icon" :class="{ 'is-locked': !m.earned }" :style="{ color: medalColor(m) }">
            <svg v-if="m.icon === 'star'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2l2.9 6.3 6.9.8-5.1 4.7 1.4 6.8L12 17.8 5.9 20.6l1.4-6.8L2.2 9.1l6.9-.8L12 2z" />
            </svg>
            <svg v-else-if="m.icon === 'check'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10" fill="currentColor" stroke="none" opacity="0.12" />
              <path d="M8 12.5l2.8 2.8L16 10" />
            </svg>
            <svg v-else-if="m.icon === 'trophy'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M6 4h12v2a6 6 0 01-12 0V4zm0 2v0a4 4 0 008 0H6zm2 8h8l-1 6H9l-1-6zm-3-6h2v2a4 4 0 01-2-2zm14 0h-2v2a4 4 0 002-2z" />
            </svg>
            <svg v-else-if="m.icon === 'map'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2a7 7 0 00-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 00-7-7zm0 9.5A2.5 2.5 0 1112 6.5a2.5 2.5 0 010 5z" />
            </svg>
            <svg v-else-if="m.icon === 'fire'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M13.5 2s4.5 4 4.5 8.5a6 6 0 01-12 0c0-2 1-3.5 1-3.5s2 2.5 3 2.5c0-3 3.5-7.5 3.5-7.5zM12 22a5 5 0 01-5-5c0-2 1-3.5 2.5-4.5.5 1.5 1.5 2.5 2.5 3 .5-2 1.5-3 2.5-3.5C16 12.5 17 14 17 16a5 5 0 01-5 6z" />
            </svg>
            <svg v-else-if="m.icon === 'book'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M4 4h6a3 3 0 013 3v13a2 2 0 00-2-2H4V4zm16 0h-6a3 3 0 00-3 3v13a2 2 0 012-2h7V4z" />
            </svg>
            <svg v-else-if="m.icon === 'flag'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M5 3h2v18H5V3zm2 3h12l-2 4 2 4H7V6z" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="currentColor">
              <path d="M17 9V7a5 5 0 00-10 0v2H5v13h14V9h-2zm-8-2a3 3 0 016 0v2H9V7z" />
            </svg>
          </div>
          <div class="profile__medal-name">{{ m.name }}</div>
        </div>
      </div>
    </section>

    <!-- 我的订单 -->
    <section class="profile__card profile__orders">
      <div class="profile__section-title">
        <span>我的订单</span>
        <span class="profile__arrow">›</span>
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

    <!-- 游览足迹 -->
    <section v-for="g in footprintGroups" :key="g.date" class="profile__footprint">
      <div class="profile__section-title">
        <span>游览足迹（{{ g.date }}）</span>
      </div>
      <div
        v-for="item in g.items"
        :key="item.id"
        class="profile__footprint-card"
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
    </section>

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

            <div class="pf-version">红色文旅 v0.1.0</div>
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
  }

  &__badge--medal {
    background: rgba(255, 255, 255, 0.18);
    color: #fff;
    font-weight: 600;
  }

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

  // ===== 勋章 =====
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
  }

  &__medal-icon {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    background: @color-bg-page;
    display: flex;
    align-items: center;
    justify-content: center;

    svg {
      width: 26px;
      height: 26px;
    }

    &.is-locked {
      background: #f0f0f2;
    }
  }

  &__medal-name {
    font-size: @font-size-sm;
    color: @color-text-secondary;
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

.pf-version {
  text-align: center;
  font-size: @font-size-sm;
  color: @color-text-secondary;
  padding: @spacing-sm 0;
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
</style>
