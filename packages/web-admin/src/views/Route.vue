<script setup lang="ts">
// PC管理后台 导览路线管理页（PRD 5.3）
// 超管可增删改全部景区路线，普管仅查看管辖景区路线（只读）
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { RedButton } from '@red-tour-project/common'
import {
  getAdminRoutes,
  createAdminRoute,
  updateAdminRoute,
  deleteAdminRoute,
  getAdminSpots,
  type AdminRouteItem,
  type AdminRoutePayload,
  type AdminSpotItem,
} from '@/api/admin'
import { getScenicAreas } from '@/api'
import { useUserStore } from '@/store'
import type { ScenicAreaInfo } from '@red-tour-project/common'

const userStore = useUserStore()
const isReadOnly = computed(() => !userStore.isSuperAdmin)

// ===== 景区下拉数据 =====
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
const tableData = ref<AdminRouteItem[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  pageSize: 10,
  keyword: '',
  scenicAreaId: '' as number | '',
})

async function loadList() {
  loading.value = true
  try {
    // 普管自动绑定管辖景区过滤
    const autoScenicId = isReadOnly.value
      ? userStore.userInfo?.scenicAreaId
      : query.scenicAreaId || undefined
    const res = await getAdminRoutes({
      page: query.page,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      scenicAreaId: autoScenicId,
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

// ===== 景点下拉数据（按景区加载，供路线选择景点用）=====
const spotOptions = ref<AdminSpotItem[]>([])
const spotNameMap = computed(() => {
  const map = new Map<number, string>()
  for (const s of spotOptions.value) {
    map.set(s.id, s.name)
  }
  return map
})

async function loadSpots(scenicAreaId: number) {
  try {
    spotOptions.value = await getAdminSpots(scenicAreaId)
  } catch {
    spotOptions.value = []
  }
}

// ===== 新增/编辑弹窗 =====
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增路线' : '编辑路线'))
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<AdminRoutePayload & { id?: number }>({
  name: '',
  scenicAreaId: 0,
  description: '',
  spotIds: [],
  duration: 60,
  difficulty: 'easy',
  status: 1,
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入路线名称', trigger: 'blur' },
    { min: 1, max: 50, message: '名称长度为 1-50 字符', trigger: 'blur' },
  ],
  scenicAreaId: [{ required: true, message: '请选择所属景区', trigger: 'change' }],
  spotIds: [
    {
      validator: (_rule, value: number[], callback) => {
        if (!value || value.length === 0) {
          return callback(new Error('请至少选择一个景点'))
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  duration: [{ required: true, message: '请输入预计时长', trigger: 'blur' }],
}

function resetForm() {
  form.id = undefined
  form.name = ''
  form.scenicAreaId = 0
  form.description = ''
  form.spotIds = []
  form.duration = 60
  form.difficulty = 'easy'
  form.status = 1
}

function openCreate() {
  resetForm()
  // 普管默认绑定管辖景区
  if (isReadOnly.value && userStore.userInfo?.scenicAreaId) {
    form.scenicAreaId = userStore.userInfo.scenicAreaId
    loadSpots(form.scenicAreaId)
  }
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEdit(row: AdminRouteItem) {
  resetForm()
  dialogMode.value = 'edit'
  form.id = row.id
  form.name = row.name
  form.scenicAreaId = row.scenicAreaId
  form.description = row.description || ''
  form.spotIds = toSpotIdList(row.spotIds)
  form.duration = row.duration
  form.difficulty = row.difficulty || 'easy'
  form.status = row.status ?? 1
  // 加载该景区的景点列表
  loadSpots(row.scenicAreaId)
  dialogVisible.value = true
}

// 弹窗中选择景区时重新加载景点
// flush: 'sync' 保证 openEdit 程序化赋值时 watch 立即执行（此时弹窗尚未打开，不清空回显值）
watch(
  () => form.scenicAreaId,
  (newId) => {
    if (newId && dialogVisible.value) {
      loadSpots(newId)
      form.spotIds = [] // 切换景区时清空已选景点
    }
  },
  { flush: 'sync' },
)

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload: AdminRoutePayload = {
        name: form.name,
        scenicAreaId: form.scenicAreaId,
        description: form.description || undefined,
        spotIds: form.spotIds,
        duration: form.duration,
        difficulty: form.difficulty,
        status: form.status,
      }
      if (dialogMode.value === 'create') {
        await createAdminRoute(payload)
        ElMessage.success('路线创建成功')
      } else if (form.id) {
        await updateAdminRoute(form.id, payload)
        ElMessage.success('路线更新成功')
      }
      dialogVisible.value = false
      loadList()
    } catch {
      // 错误提示已由拦截器统一弹出
    } finally {
      submitting.value = false
    }
  })
}

// ===== 删除 =====
async function handleDelete(row: AdminRouteItem) {
  try {
    await ElMessageBox.confirm(`确定要删除路线 "${row.name}" 吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteAdminRoute(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 错误提示已由拦截器统一弹出
  }
}

// ===== 工具函数 =====
const difficultyMap: Record<string, { text: string; type: '' | 'warning' | 'danger' }> = {
  easy: { text: '轻松', type: '' },
  medium: { text: '中等', type: 'warning' },
  hard: { text: '困难', type: 'danger' },
}

function statusText(status?: number) {
  return status === 1 ? '上线' : '下线'
}

function statusTagType(status?: number) {
  return status === 1 ? 'success' : 'info'
}

/** 后端 spotIds 可能为空或 JSON 字符串，统一解析成 number[] */
function toSpotIdList(spotIds: unknown): number[] {
  if (Array.isArray(spotIds)) return spotIds as number[]
  if (typeof spotIds === 'string' && spotIds.trim()) {
    try {
      const parsed = JSON.parse(spotIds)
      if (Array.isArray(parsed)) return parsed as number[]
    } catch {
      return []
    }
  }
  return []
}

function spotNames(spotIds: unknown) {
  const ids = toSpotIdList(spotIds)
  if (ids.length === 0) return '—'
  return ids.map((id) => spotNameMap.value.get(id) ?? `#${id}`).join(' → ')
}

// ===== 初始化 =====
onMounted(() => {
  loadScenicOptions()
  loadList()
})
</script>

<template>
  <div class="route-page">
    <!-- 搜索栏 -->
    <div class="route-page__toolbar">
      <div class="route-page__search">
        <el-input
          v-model="query.keyword"
          placeholder="路线名称搜索"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-if="!isReadOnly"
          v-model="query.scenicAreaId"
          placeholder="景区筛选"
          clearable
          filterable
          style="width: 180px"
        >
          <el-option v-for="s in scenicOptions" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <RedButton type="primary" size="small" @click="handleSearch">搜索</RedButton>
      </div>
      <RedButton v-if="!isReadOnly" type="primary" size="small" @click="openCreate">
        + 新增路线
      </RedButton>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="name" label="路线名称" min-width="140" />
      <el-table-column label="所属景区" width="140">
        <template #default="{ row }">
          {{ scenicNameMap.get(row.scenicAreaId) ?? `#${row.scenicAreaId}` }}
        </template>
      </el-table-column>
      <el-table-column label="描述" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.description || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="景点路线" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          {{ spotNames(row.spotIds) }}
        </template>
      </el-table-column>
      <el-table-column label="时长" width="80">
        <template #default="{ row }">{{ row.duration }} 分钟</template>
      </el-table-column>
      <el-table-column label="难度" width="80">
        <template #default="{ row }">
          <el-tag :type="difficultyMap[row.difficulty]?.type || ''" effect="plain">
            {{ difficultyMap[row.difficulty]?.text || row.difficulty }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" effect="plain">
            {{ statusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="!isReadOnly" label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="route-page__pagination">
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" size="default">
        <el-form-item label="路线名称" prop="name">
          <el-input v-model="form.name" placeholder="1-50 字符" />
        </el-form-item>
        <el-form-item label="所属景区" prop="scenicAreaId">
          <el-select
            v-model="form.scenicAreaId"
            placeholder="请选择景区"
            filterable
            :disabled="isReadOnly"
            style="width: 100%"
          >
            <el-option v-for="s in scenicOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="路线描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="选填，最多 200 字符"
          />
        </el-form-item>
        <el-form-item label="景点路线" prop="spotIds">
          <el-select
            v-model="form.spotIds"
            multiple
            filterable
            placeholder="请选择景点（可多选，顺序即导览顺序）"
            style="width: 100%"
          >
            <el-option v-for="s in spotOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计时长" prop="duration">
          <el-input-number v-model="form.duration" :min="1" :max="600" />
          <span style="margin-left: 8px; color: #909399">分钟</span>
        </el-form-item>
        <el-form-item label="难度">
          <el-radio-group v-model="form.difficulty">
            <el-radio-button value="easy">轻松</el-radio-button>
            <el-radio-button value="medium">中等</el-radio-button>
            <el-radio-button value="hard">困难</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="上线"
            inactive-text="下线"
            inline-prompt
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <RedButton type="ghost" size="small" @click="dialogVisible = false">取消</RedButton>
        <RedButton type="primary" size="small" :loading="submitting" @click="handleSubmit">
          确定
        </RedButton>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.route-page {
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

  &__pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: @spacing-md;
  }
}
</style>
