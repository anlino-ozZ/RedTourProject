<script setup lang="ts">
// PC管理后台 打卡点管理页（PRD 5.4）
// 打卡点归属于景点，筛选/新增均需"景区 → 景点"两级联动
// 超管可增删改全部景区打卡点，普管仅查看管辖景区（只读）
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { RedButton } from '@red-tour-project/common'
import {
  getAdminCheckpoints,
  createAdminCheckpoint,
  updateAdminCheckpoint,
  deleteAdminCheckpoint,
  getAdminSpots,
  type AdminCheckpointItem,
  type AdminCheckpointPayload,
  type AdminSpotItem,
} from '@/api/admin'
import { getScenicAreas } from '@/api'
import { useUserStore } from '@/store'
import type { ScenicAreaInfo } from '@red-tour-project/common'

const userStore = useUserStore()
const isReadOnly = computed(() => !userStore.isSuperAdmin)

// 普管管辖景区（自动绑定）
const boundScenicAreaId = computed(() =>
  isReadOnly.value ? (userStore.userInfo?.scenicAreaId ?? undefined) : undefined,
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

// ===== 景点下拉（按景区联动）=====
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

// ===== 列表 =====
const loading = ref(false)
const tableData = ref<AdminCheckpointItem[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  pageSize: 10,
  scenicAreaId: '' as number | '',
  spotId: '' as number | '',
})

// 筛选栏选择景区后加载景点并清空已选景点
watch(
  () => query.scenicAreaId,
  (newId) => {
    query.spotId = ''
    if (newId) {
      loadSpots(newId)
    } else {
      spotOptions.value = []
    }
  },
)

async function loadList() {
  loading.value = true
  try {
    const res = await getAdminCheckpoints({
      page: query.page,
      pageSize: query.pageSize,
      scenicAreaId: boundScenicAreaId.value ?? (query.scenicAreaId || undefined),
      spotId: query.spotId || undefined,
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

// ===== 新增/编辑弹窗 =====
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增打卡点' : '编辑打卡点'))
const formRef = ref<FormInstance>()
const submitting = ref(false)

// 注意：scenicAreaId/spotId 初始必须为 undefined 而非 0，
// 否则 async-validator 的 required 会把数字 0 判定为有值，校验被绕过
const form = reactive<{
  id?: number
  scenicAreaId?: number
  spotId?: number
  longitude?: number
  latitude?: number
  radius: number
}>({
  id: undefined,
  scenicAreaId: boundScenicAreaId.value,
  spotId: undefined,
  longitude: undefined,
  latitude: undefined,
  radius: 50,
})

// 弹窗中选择景区后加载景点并清空已选景点
// flush: 'sync' 保证 openEdit 程序化赋值时 watch 先执行清空，后续回显赋值不被覆盖
watch(
  () => form.scenicAreaId,
  (newId) => {
    form.spotId = undefined
    if (newId && dialogVisible.value) {
      loadSpots(newId)
    }
  },
  { flush: 'sync' },
)

const rules: FormRules = {
  scenicAreaId: [{ required: true, message: '请选择所属景区', trigger: 'change' }],
  spotId: [{ required: true, message: '请选择关联景点', trigger: 'change' }],
  longitude: [
    {
      validator: (_rule, value: number, callback) => {
        if (value === undefined || value === null || Number.isNaN(value)) {
          return callback(new Error('请输入经度'))
        }
        if (value < -180 || value > 180) {
          return callback(new Error('经度范围 -180 ~ 180'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  latitude: [
    {
      validator: (_rule, value: number, callback) => {
        if (value === undefined || value === null || Number.isNaN(value)) {
          return callback(new Error('请输入纬度'))
        }
        if (value < -90 || value > 90) {
          return callback(new Error('纬度范围 -90 ~ 90'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  radius: [
    {
      validator: (_rule, value: number, callback) => {
        if (value === undefined || value === null || Number.isNaN(value)) {
          return callback(new Error('请输入有效半径'))
        }
        if (value < 1 || value > 1000) {
          return callback(new Error('半径范围 1 ~ 1000 米'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

function resetForm() {
  form.id = undefined
  form.scenicAreaId = boundScenicAreaId.value
  form.spotId = undefined
  form.longitude = undefined
  form.latitude = undefined
  form.radius = 50
}

function openCreate() {
  resetForm()
  if (form.scenicAreaId) {
    loadSpots(form.scenicAreaId)
  }
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEdit(row: AdminCheckpointItem) {
  resetForm()
  dialogMode.value = 'edit'
  form.id = row.id
  form.scenicAreaId = row.scenicAreaId
  form.spotId = row.spotId
  form.longitude = row.longitude
  form.latitude = row.latitude
  form.radius = row.radius
  if (form.scenicAreaId) {
    loadSpots(form.scenicAreaId)
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload: AdminCheckpointPayload = {
        spotId: form.spotId!,
        longitude: form.longitude!,
        latitude: form.latitude!,
        radius: form.radius,
      }
      if (dialogMode.value === 'create') {
        await createAdminCheckpoint(payload)
        ElMessage.success('打卡点创建成功')
      } else if (form.id) {
        await updateAdminCheckpoint(form.id, payload)
        ElMessage.success('打卡点更新成功')
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
async function handleDelete(row: AdminCheckpointItem) {
  const spotName = row.spotName ?? `#${row.spotId}`
  try {
    await ElMessageBox.confirm(`确定要删除景点 "${spotName}" 的打卡点吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteAdminCheckpoint(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 错误提示已由拦截器统一弹出
  }
}

// ===== 工具函数 =====
function displaySpotName(row: AdminCheckpointItem) {
  return row.spotName || spotNameMap.value.get(row.spotId) || `#${row.spotId}`
}

// ===== 初始化 =====
onMounted(async () => {
  await loadScenicOptions()
  // 普管进入页面即加载管辖景区景点并拉列表
  if (boundScenicAreaId.value) {
    query.scenicAreaId = boundScenicAreaId.value
    await loadSpots(boundScenicAreaId.value)
  }
  loadList()
})
</script>

<template>
  <div class="checkpoint-page">
    <!-- 筛选栏 -->
    <div class="checkpoint-page__toolbar">
      <div class="checkpoint-page__search">
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
        <el-select
          v-model="query.spotId"
          placeholder="景点筛选"
          clearable
          filterable
          :disabled="!query.scenicAreaId"
          style="width: 180px"
        >
          <el-option v-for="s in spotOptions" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <RedButton type="primary" size="small" @click="handleSearch">搜索</RedButton>
      </div>
      <RedButton v-if="!isReadOnly" type="primary" size="small" @click="openCreate">
        + 新增打卡点
      </RedButton>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
      <el-table-column label="所属景区" min-width="140">
        <template #default="{ row }">
          {{
            row.scenicAreaId ? (scenicNameMap.get(row.scenicAreaId) ?? `#${row.scenicAreaId}`) : '—'
          }}
        </template>
      </el-table-column>
      <el-table-column label="关联景点" min-width="140">
        <template #default="{ row }">
          {{ displaySpotName(row) }}
        </template>
      </el-table-column>
      <el-table-column prop="longitude" label="经度" width="140" />
      <el-table-column prop="latitude" label="纬度" width="140" />
      <el-table-column label="有效半径" width="110">
        <template #default="{ row }">{{ row.radius }} 米</template>
      </el-table-column>
      <el-table-column v-if="!isReadOnly" label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="checkpoint-page__pagination">
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
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" size="default">
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
        <el-form-item label="关联景点" prop="spotId">
          <el-select
            v-model="form.spotId"
            placeholder="请先选择景区，再选择景点"
            filterable
            :disabled="!form.scenicAreaId"
            style="width: 100%"
          >
            <el-option v-for="s in spotOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="经度" prop="longitude">
          <el-input-number
            v-model="form.longitude"
            :precision="7"
            :step="0.0001"
            :min="-180"
            :max="180"
            controls-position="right"
            placeholder="经度，如 106.92723"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="纬度" prop="latitude">
          <el-input-number
            v-model="form.latitude"
            :precision="7"
            :step="0.0001"
            :min="-90"
            :max="90"
            controls-position="right"
            placeholder="纬度，如 27.68657"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="有效半径" prop="radius">
          <el-input-number v-model="form.radius" :min="1" :max="1000" controls-position="right" />
          <span style="margin-left: 8px; color: #909399">米（游客进入该半径内可打卡）</span>
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

.checkpoint-page {
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
