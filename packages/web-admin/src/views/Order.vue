<script setup lang="ts">
// PC管理后台 特产意向订单管理页（PRD 5.6，表 product_order）
// 订单由游客侧产生，管理端无新增/编辑/删除，仅支持：筛选查看、详情、状态流转
// 状态链路：pending 待处理 → contacted 已联系 → closed 已关闭
// 超管可见全部景区订单，普管仅见管辖景区订单；两者均可流转状态（跟进职责）
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RedButton } from '@red-tour-project/common'
import {
  getAdminProductOrders,
  getAdminProductOrder,
  updateAdminProductOrderStatus,
  type AdminProductOrderItem,
  type ProductOrderStatus,
} from '@/api/admin'
import { getScenicAreas } from '@/api'
import { useUserStore } from '@/store'
import { formatDate } from '@red-tour-project/common'
import type { ScenicAreaInfo } from '@red-tour-project/common'

const userStore = useUserStore()
const isSuperAdmin = computed(() => userStore.isSuperAdmin)

// 普管数据范围锁定管辖景区
const boundScenicAreaId = computed(() =>
  isSuperAdmin.value ? undefined : (userStore.userInfo?.scenicAreaId ?? undefined),
)

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

// ===== 列表 =====
const loading = ref(false)
const tableData = ref<AdminProductOrderItem[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  pageSize: 10,
  phone: '',
  status: '' as '' | ProductOrderStatus,
  scenicAreaId: '' as number | '',
})

async function loadList() {
  loading.value = true
  try {
    const res = await getAdminProductOrders({
      page: query.page,
      pageSize: query.pageSize,
      phone: query.phone || undefined,
      status: query.status || undefined,
      scenicAreaId: boundScenicAreaId.value ?? (query.scenicAreaId || undefined),
    })
    tableData.value = res.list
    total.value = res.total
  } catch {
    // 错误提示已由拦截器统一弹出
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handlePageChange(page: number) {
  query.page = page
  loadList()
}

function handleSizeChange(size: number) {
  query.pageSize = size
  query.page = 1
  loadList()
}

// ===== 详情弹窗（只读） =====
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminProductOrderItem | null>(null)

async function openDetail(row: AdminProductOrderItem) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = row
  try {
    // 拉详情保证最新状态（列表数据可能已过期）
    detail.value = await getAdminProductOrder(row.id)
  } catch {
    // 拉取失败时保留列表行数据
  } finally {
    detailLoading.value = false
  }
}

// ===== 状态流转 =====
const statusMeta: Record<
  ProductOrderStatus,
  { text: string; type: 'warning' | 'primary' | 'info' }
> = {
  pending: { text: '待处理', type: 'warning' },
  contacted: { text: '已联系', type: 'primary' },
  closed: { text: '已关闭', type: 'info' },
}

/** 各状态可执行的下一步动作 */
const nextAction: Partial<
  Record<ProductOrderStatus, { status: ProductOrderStatus; text: string; confirm: string }>
> = {
  pending: {
    status: 'contacted',
    text: '标记已联系',
    confirm: '确认已与该游客电话沟通，将订单标记为"已联系"？',
  },
  contacted: {
    status: 'closed',
    text: '关闭订单',
    confirm: '确认关闭该意向订单？关闭后不可再流转。',
  },
}

const changingId = ref<number | null>(null)

async function handleChangeStatus(row: AdminProductOrderItem) {
  const action = nextAction[row.status]
  if (!action) return
  try {
    await ElMessageBox.confirm(action.confirm, '状态流转确认', {
      confirmButtonText: action.text,
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  changingId.value = row.id
  try {
    await updateAdminProductOrderStatus(row.id, action.status)
    ElMessage.success('状态已更新')
    // 同步详情弹窗中的状态
    if (detail.value?.id === row.id) {
      detail.value.status = action.status
    }
    loadList()
  } catch {
    // 错误提示已由拦截器统一弹出
  } finally {
    changingId.value = null
  }
}

// ===== 工具函数 =====
function formatTime(t?: string) {
  return t ? formatDate(t, 'YYYY-MM-DD HH:mm') : '—'
}

// ===== 初始化 =====
onMounted(() => {
  loadScenicOptions()
  if (boundScenicAreaId.value) {
    query.scenicAreaId = boundScenicAreaId.value
  }
  loadList()
})
</script>

<template>
  <div class="order-page">
    <!-- 筛选栏（订单由游客侧产生，无新增入口） -->
    <div class="order-page__toolbar">
      <div class="order-page__search">
        <el-input
          v-model="query.phone"
          placeholder="联系电话搜索"
          clearable
          style="width: 170px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.status" placeholder="状态筛选" clearable style="width: 130px">
          <el-option label="待处理" value="pending" />
          <el-option label="已联系" value="contacted" />
          <el-option label="已关闭" value="closed" />
        </el-select>
        <el-select
          v-if="isSuperAdmin"
          v-model="query.scenicAreaId"
          placeholder="景区筛选"
          clearable
          filterable
          style="width: 170px"
        >
          <el-option v-for="s in scenicOptions" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <RedButton type="primary" size="small" @click="handleSearch">搜索</RedButton>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
      <el-table-column label="订单号" width="80">
        <template #default="{ row }">#{{ row.id }}</template>
      </el-table-column>
      <el-table-column label="意向产品" min-width="180">
        <template #default="{ row }">
          <div class="order-page__product">
            <el-image
              v-if="row.productImage"
              :src="row.productImage"
              fit="cover"
              class="order-page__thumb"
            />
            <span v-else class="order-page__no-img">无图</span>
            <span class="order-page__product-name">
              {{ row.productName ?? `产品#${row.productId}` }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="所属景区" width="130">
        <template #default="{ row }">
          {{
            row.scenicAreaName ||
            (row.scenicAreaId
              ? (scenicNameMap.get(row.scenicAreaId) ?? `#${row.scenicAreaId}`)
              : '—')
          }}
        </template>
      </el-table-column>
      <el-table-column label="数量" width="70" align="center">
        <template #default="{ row }">×{{ row.quantity }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="联系电话" width="130" />
      <el-table-column label="备注" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.remark || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }: { row: AdminProductOrderItem }">
          <el-tag :type="statusMeta[row.status]?.type" effect="plain">
            {{ statusMeta[row.status]?.text || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" width="150">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }: { row: AdminProductOrderItem }">
          <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="nextAction[row.status]"
            link
            :type="row.status === 'pending' ? 'warning' : 'success'"
            size="small"
            :loading="changingId === row.id"
            @click="handleChangeStatus(row)"
          >
            {{ nextAction[row.status]!.text }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="order-page__pagination">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 详情弹窗（只读，底部可流转状态） -->
    <el-dialog v-model="detailVisible" title="意向订单详情" width="560px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="2" border>
          <el-descriptions-item label="订单号">#{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusMeta[detail.status]?.type" effect="plain">
              {{ statusMeta[detail.status]?.text || detail.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="意向产品" :span="2">
            {{ detail.productName ?? `产品#${detail.productId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="所属景区" :span="2">
            {{
              detail.scenicAreaName ||
              (detail.scenicAreaId
                ? (scenicNameMap.get(detail.scenicAreaId) ?? `#${detail.scenicAreaId}`)
                : '—')
            }}
          </el-descriptions-item>
          <el-descriptions-item label="数量">×{{ detail.quantity }}</el-descriptions-item>
          <el-descriptions-item label="游客ID">{{ detail.userId ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">
            <span class="order-page__phone">{{ detail.phone }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="下单时间">
            {{ formatTime(detail.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">
            {{ detail.remark || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间" :span="2">
            {{ formatTime(detail.updatedAt) }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <RedButton type="ghost" size="small" @click="detailVisible = false">关闭</RedButton>
        <RedButton
          v-if="detail && nextAction[detail.status]"
          type="primary"
          size="small"
          :loading="changingId === detail.id"
          @click="handleChangeStatus(detail)"
        >
          {{ nextAction[detail.status]!.text }}
        </RedButton>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.order-page {
  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: @spacing-md;
  }

  &__search {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
  }

  &__product {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
  }

  &__thumb {
    width: 40px;
    height: 40px;
    border-radius: 4px;
    border: 1px solid @color-border;
    flex-shrink: 0;
  }

  &__no-img {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 4px;
    background: @color-bg-page;
    color: @color-text-secondary;
    font-size: @font-size-sm;
    flex-shrink: 0;
  }

  &__product-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__phone {
    color: @color-primary;
    font-weight: 600;
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: @spacing-md;
  }
}
</style>
