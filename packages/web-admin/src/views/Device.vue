<script setup lang="ts">
// PC管理后台 设备监控页（接口文档 第十二章，PRD 5.8）
// 树莓派 5 + Hailo8L 边缘节点运行状态：CPU 温度/内存/磁盘/Hailo/摄像头/音箱 + 心跳
// 设备每 30 秒上报心跳，超过 2 分钟未上报判定离线；超管可远程重启
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RedButton } from '@red-tour-project/common'
import { getDeviceStatus, rebootDevice, type DeviceStatus } from '@/api/device'
import { getScenicAreas } from '@/api'
import { useUserStore } from '@/store'
import type { ScenicAreaInfo } from '@red-tour-project/common'

const userStore = useUserStore()
const isSuperAdmin = computed(() => userStore.isSuperAdmin)
const boundScenicAreaId = computed(() =>
  isSuperAdmin.value ? undefined : userStore.userInfo?.scenicAreaId,
)

/** 心跳自动刷新间隔（与设备上报周期一致） */
const POLL_INTERVAL = 30 * 1000
/** 离线判定阈值（PRD：2 分钟） */
const OFFLINE_THRESHOLD = 2 * 60 * 1000

// ===== 景区下拉 =====
const scenicOptions = ref<ScenicAreaInfo[]>([])
const scenicNameMap = computed(() => {
  const map = new Map<number, string>()
  for (const s of scenicOptions.value) {
    map.set(s.id, s.name)
  }
  return map
})

async function loadScenicOptions() {
  try {
    scenicOptions.value = await getScenicAreas()
  } catch {
    // 错误提示已由拦截器统一弹出
  }
}

// ===== 数据 =====
const loading = ref(false)
const devices = ref<DeviceStatus[]>([])
const query = reactive({
  scenicAreaId: (boundScenicAreaId.value ?? '') as number | '',
})
const lastRefreshAt = ref<Date | null>(null)

async function loadList() {
  loading.value = true
  try {
    devices.value = await getDeviceStatus(
      boundScenicAreaId.value ?? (query.scenicAreaId || undefined),
    )
    lastRefreshAt.value = new Date()
  } catch {
    // 错误提示已由拦截器统一弹出
  } finally {
    loading.value = false
  }
}

// ===== 汇总 =====
const onlineCount = computed(() => devices.value.filter((d) => d.online).length)
const offlineCount = computed(() => devices.value.length - onlineCount.value)

// ===== 自动刷新 =====
const autoRefresh = ref(true)
let pollTimer: number | null = null

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(loadList, POLL_INTERVAL)
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function toggleAutoRefresh(val: boolean | string | number) {
  if (val) {
    startPolling()
    loadList()
  } else {
    stopPolling()
  }
}

// ===== 指标展示辅助 =====
// 注意：el-progress 一旦绑定 status 会用状态图标替换数值文本，故改用 :color 按阈值着色，
// 以保留 format 出的温度/百分比数字
const BAR_COLOR_NORMAL = '#67c23a'
const BAR_COLOR_WARNING = '#e6a23c'
const BAR_COLOR_DANGER = '#f56c6c'

/** CPU 温度进度条颜色：<60 正常 / 60-75 警告 / >75 危险（入参为温度值） */
function tempBarColor(t: number): string {
  if (t >= 75) return BAR_COLOR_DANGER
  if (t >= 60) return BAR_COLOR_WARNING
  return BAR_COLOR_NORMAL
}

/** 占用率进度条颜色：>90 危险 / >75 警告 */
function usageBarColor(v: number): string {
  if (v >= 90) return BAR_COLOR_DANGER
  if (v >= 75) return BAR_COLOR_WARNING
  return BAR_COLOR_NORMAL
}

/** Hailo8L 状态元信息：active=正常，其余按异常展示原值 */
function hailoMeta(status: string): { text: string; type: 'success' | 'danger' | 'info' } {
  if (status === 'active') return { text: '正常', type: 'success' }
  if (['inactive', 'error', 'failed'].includes(status)) {
    return { text: '异常', type: 'danger' }
  }
  return { text: status || '未知', type: 'info' }
}

/** 当前时间 tick，驱动心跳相对时间每 30 秒重算 */
const nowTick = ref(Date.now())
let nowTimer: number | null = null

/** 心跳相对时间文本 */
function heartbeatText(d: DeviceStatus): string {
  if (!d.lastHeartbeat) return '—'
  const diff = nowTick.value - new Date(d.lastHeartbeat).getTime()
  if (Number.isNaN(diff)) return '—'
  if (diff < 0) return '刚刚'
  if (diff < 60 * 1000) return `${Math.floor(diff / 1000)} 秒前`
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)} 分钟前`
  return `${Math.floor(diff / 3600000)} 小时前`
}

/** 心跳是否超过离线阈值（前端兜底标记，用于文字变红） */
function heartbeatStale(d: DeviceStatus): boolean {
  if (!d.lastHeartbeat) return false
  const diff = nowTick.value - new Date(d.lastHeartbeat).getTime()
  return !Number.isNaN(diff) && diff > OFFLINE_THRESHOLD
}

function refreshTimeText() {
  if (!lastRefreshAt.value) return '—'
  return `${String(lastRefreshAt.value.getHours()).padStart(2, '0')}:${String(
    lastRefreshAt.value.getMinutes(),
  ).padStart(2, '0')}:${String(lastRefreshAt.value.getSeconds()).padStart(2, '0')}`
}

// ===== 远程重启 =====
const rebootingId = ref<string | null>(null)

async function handleReboot(d: DeviceStatus) {
  try {
    await ElMessageBox.confirm(
      `确定向设备 ${d.deviceId} 下发远程重启指令吗？重启期间该设备导览服务将短暂中断。`,
      '远程重启确认',
      { confirmButtonText: '下发重启指令', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  rebootingId.value = d.deviceId
  try {
    const res = await rebootDevice(d.deviceId)
    if (res.sent) {
      ElMessage.success('重启指令已下发')
    }
  } catch {
    // 503 设备离线等错误提示已由拦截器统一弹出
  } finally {
    rebootingId.value = null
  }
}

/**
 * 设备心跳字段可能为 null（尚未上报）。渲染期直接 Math.round/toFixed 会抛错，
 * 统一转成数字兜底。
 */
function numOf(value: unknown): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

onMounted(() => {
  loadScenicOptions()
  loadList()
  if (autoRefresh.value) startPolling()
  nowTimer = window.setInterval(() => {
    nowTick.value = Date.now()
  }, POLL_INTERVAL)
})

onBeforeUnmount(() => {
  stopPolling()
  if (nowTimer !== null) window.clearInterval(nowTimer)
})
</script>

<template>
  <div class="device-page" v-loading="loading">
    <!-- 汇总卡片 -->
    <el-row :gutter="16" class="device-page__summary">
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="device-page__stat-card">
          <div class="device-page__stat">
            <span class="device-page__stat-num">{{ devices.length }}</span>
            <span class="device-page__stat-label">设备总数</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="device-page__stat-card">
          <div class="device-page__stat">
            <span class="device-page__stat-num device-page__stat-num--online">
              {{ onlineCount }}
            </span>
            <span class="device-page__stat-label">在线</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="device-page__stat-card">
          <div class="device-page__stat">
            <span class="device-page__stat-num device-page__stat-num--offline">
              {{ offlineCount }}
            </span>
            <span class="device-page__stat-label">离线</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 工具栏 -->
    <div class="device-page__toolbar">
      <div class="device-page__filters">
        <el-select
          v-if="isSuperAdmin"
          v-model="query.scenicAreaId"
          placeholder="全部景区"
          clearable
          filterable
          style="width: 180px"
          @change="loadList"
        >
          <el-option v-for="s in scenicOptions" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-switch
          v-model="autoRefresh"
          active-text="30 秒自动刷新"
          inline-prompt
          @change="toggleAutoRefresh"
        />
        <span class="device-page__refresh-time">最近刷新 {{ refreshTimeText() }}</span>
      </div>
      <RedButton type="primary" size="small" @click="loadList">刷新</RedButton>
    </div>

    <!-- 设备卡片网格 -->
    <el-empty v-if="!loading && devices.length === 0" description="暂无设备" />
    <el-row v-else :gutter="16">
      <el-col v-for="d in devices" :key="d.deviceId" :xs="24" :sm="12" :lg="8">
        <el-card
          shadow="never"
          class="device-page__device"
          :class="{ 'device-page__device--offline': !d.online }"
        >
          <!-- 卡片头：设备 ID + 在线状态 -->
          <div class="device-page__head">
            <div class="device-page__id">
              <span class="device-page__dot" :class="d.online ? 'is-online' : 'is-offline'"></span>
              <span class="device-page__device-id">{{ d.deviceId }}</span>
            </div>
            <el-tag :type="d.online ? 'success' : 'info'" effect="plain" size="small">
              {{ d.online ? '在线' : '离线' }}
            </el-tag>
          </div>
          <div class="device-page__scenic">
            {{ scenicNameMap.get(d.scenicAreaId) ?? `景区 #${d.scenicAreaId}` }}
          </div>

          <!-- 指标 -->
          <div class="device-page__metrics">
            <div class="device-page__metric">
              <span class="device-page__metric-label">CPU 温度</span>
              <el-progress
                :percentage="Math.min(Math.round(numOf(d.cpuTemp)), 100)"
                :color="tempBarColor(Math.min(Math.round(numOf(d.cpuTemp)), 100))"
                :stroke-width="10"
                :format="() => `${numOf(d.cpuTemp).toFixed(1)}℃`"
              />
            </div>
            <div class="device-page__metric">
              <span class="device-page__metric-label">内存占用</span>
              <el-progress
                :percentage="Math.round(numOf(d.memoryUsage))"
                :color="usageBarColor(Math.round(numOf(d.memoryUsage)))"
                :stroke-width="10"
                :format="(p: number) => `${p}%`"
              />
            </div>
            <div class="device-page__metric">
              <span class="device-page__metric-label">磁盘占用</span>
              <el-progress
                :percentage="Math.round(numOf(d.diskUsage))"
                :color="usageBarColor(Math.round(numOf(d.diskUsage)))"
                :stroke-width="10"
                :format="(p: number) => `${p}%`"
              />
            </div>
          </div>

          <!-- 外设状态 -->
          <div class="device-page__peripherals">
            <el-tag :type="hailoMeta(d.hailoStatus).type" effect="plain" size="small">
              Hailo8L：{{ hailoMeta(d.hailoStatus).text }}
            </el-tag>
            <el-tag :type="d.cameraConnected ? 'success' : 'danger'" effect="plain" size="small">
              摄像头：{{ d.cameraConnected ? '已连接' : '未连接' }}
            </el-tag>
            <el-tag :type="d.speakerConnected ? 'success' : 'danger'" effect="plain" size="small">
              音箱：{{ d.speakerConnected ? '已连接' : '未连接' }}
            </el-tag>
          </div>

          <!-- 心跳 + 操作 -->
          <div class="device-page__foot">
            <span class="device-page__heartbeat" :class="{ 'is-stale': heartbeatStale(d) }">
              心跳：{{ heartbeatText(d) }}
            </span>
            <el-button
              v-if="isSuperAdmin"
              link
              type="warning"
              size="small"
              :disabled="!d.online"
              :loading="rebootingId === d.deviceId"
              @click="handleReboot(d)"
            >
              远程重启
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.device-page {
  &__summary {
    margin-bottom: @spacing-md;
  }

  &__stat-card {
    margin-bottom: @spacing-sm;
  }

  &__stat {
    display: flex;
    align-items: baseline;
    gap: @spacing-sm;
    padding: @spacing-xs @spacing-sm;
  }

  &__stat-num {
    font-size: 28px;
    font-weight: 700;
    color: @color-text-primary;

    &--online {
      color: #67c23a;
    }

    &--offline {
      color: #909399;
    }
  }

  &__stat-label {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: @spacing-md;
  }

  &__filters {
    display: flex;
    align-items: center;
    gap: @spacing-md;
  }

  &__refresh-time {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__device {
    margin-bottom: @spacing-md;
  }

  &__device--offline {
    :deep(.el-card__body) {
      opacity: 0.75;
    }
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__id {
    display: flex;
    align-items: center;
    gap: @spacing-xs;
  }

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;

    &.is-online {
      background: #67c23a;
      box-shadow: 0 0 0 3px rgba(103, 194, 58, 0.2);
    }

    &.is-offline {
      background: #909399;
    }
  }

  &__device-id {
    font-family: Consolas, Monaco, 'Courier New', monospace;
    font-weight: 600;
    color: @color-text-primary;
  }

  &__scenic {
    margin: @spacing-xs 0 @spacing-sm;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__metrics {
    display: flex;
    flex-direction: column;
    gap: @spacing-sm;
    margin-bottom: @spacing-sm;
  }

  &__metric-label {
    display: block;
    margin-bottom: 2px;
    font-size: @font-size-sm;
    color: @color-text-regular;
  }

  &__peripherals {
    display: flex;
    flex-wrap: wrap;
    gap: @spacing-xs;
    margin-bottom: @spacing-sm;
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: @spacing-sm;
    border-top: 1px solid @color-border;
  }

  &__heartbeat {
    font-size: @font-size-sm;
    color: @color-text-secondary;

    &.is-stale {
      color: #f56c6c;
    }
  }
}
</style>
