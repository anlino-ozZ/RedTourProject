<script setup lang="ts">
// PC管理后台 景区管理页（PRD 5.2）
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { RedButton } from '@red-tour-project/common'
import {
  getAdminScenicAreas,
  createAdminScenicArea,
  updateAdminScenicArea,
  deleteAdminScenicArea,
  type AdminScenicPayload,
} from '@/api/admin'
import { useUserStore } from '@/store'
import UploadFile from '@/components/UploadFile.vue'
import type { ScenicAreaInfo } from '@red-tour-project/common'

const userStore = useUserStore()

// 普管只读：仅查看管辖景区，不可增删改
const isReadOnly = computed(() => !userStore.isSuperAdmin)

// ===== 列表 =====
const loading = ref(false)
const tableData = ref<ScenicAreaInfo[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  pageSize: 10,
  keyword: '',
})

async function loadList() {
  loading.value = true
  try {
    // 普管自动绑定管辖景区过滤
    const scenicAreaId = isReadOnly.value ? userStore.userInfo?.scenicAreaId : undefined
    const res = await getAdminScenicAreas({
      page: query.page,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      scenicAreaId,
    })
    tableData.value = res.list
    total.value = res.total
  } catch {
    // 错误提示已由 axios 拦截器统一弹出
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
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增景区' : '编辑景区'))
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<AdminScenicPayload & { id?: number }>({
  name: '',
  intro: '',
  longitude: undefined,
  latitude: undefined,
  address: '',
  openHours: '',
  coverImage: '',
  status: 1,
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入景区名称', trigger: 'blur' },
    { min: 1, max: 50, message: '名称长度为 1-50 字符', trigger: 'blur' },
  ],
  intro: [{ max: 500, message: '简介最多 500 字符', trigger: 'blur' }],
  address: [{ max: 200, message: '地址最多 200 字符', trigger: 'blur' }],
  openHours: [{ max: 50, message: '如 08:30-17:00', trigger: 'blur' }],
}

function resetForm() {
  form.id = undefined
  form.name = ''
  form.intro = ''
  form.longitude = undefined
  form.latitude = undefined
  form.address = ''
  form.openHours = ''
  form.coverImage = ''
  form.status = 1
}

function openCreate() {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEdit(row: ScenicAreaInfo) {
  resetForm()
  dialogMode.value = 'edit'
  form.id = row.id
  form.name = row.name
  form.intro = row.intro || ''
  form.longitude = row.longitude
  form.latitude = row.latitude
  form.address = row.address || ''
  form.openHours = row.openHours || ''
  form.coverImage = row.coverImage || ''
  form.status = row.status ?? 1
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload: AdminScenicPayload = {
        name: form.name,
        intro: form.intro || undefined,
        longitude: form.longitude,
        latitude: form.latitude,
        address: form.address || undefined,
        openHours: form.openHours || undefined,
        coverImage: form.coverImage || undefined,
        status: form.status,
      }
      if (dialogMode.value === 'create') {
        await createAdminScenicArea(payload)
        ElMessage.success('景区创建成功')
      } else if (form.id) {
        await updateAdminScenicArea(form.id, payload)
        ElMessage.success('景区更新成功')
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
async function handleDelete(row: ScenicAreaInfo) {
  try {
    await ElMessageBox.confirm(`确定要删除景区 "${row.name}" 吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteAdminScenicArea(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 错误提示已由拦截器统一弹出
  }
}

// ===== 工具函数 =====
function statusText(status?: number) {
  return status === 1 ? '上线' : '下线'
}

function statusTagType(status?: number) {
  return status === 1 ? 'success' : 'info'
}

// ===== 初始化 =====
onMounted(loadList)
</script>

<template>
  <div class="scenic-page">
    <!-- 搜索栏 -->
    <div class="scenic-page__toolbar">
      <div class="scenic-page__search">
        <el-input
          v-model="query.keyword"
          placeholder="景区名称搜索"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <RedButton type="primary" size="small" @click="handleSearch">搜索</RedButton>
      </div>
      <RedButton v-if="!isReadOnly" type="primary" size="small" @click="openCreate">
        + 新增景区
      </RedButton>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="name" label="景区名称" min-width="140" />
      <el-table-column label="简介" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.intro || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="地址" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.address || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="开放时间" width="130">
        <template #default="{ row }">
          {{ row.openHours || '—' }}
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
    <div class="scenic-page__pagination">
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
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" size="default">
        <el-form-item label="景区名称" prop="name">
          <el-input v-model="form.name" placeholder="1-50 字符" />
        </el-form-item>
        <el-form-item label="简介" prop="intro">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="3"
            placeholder="选填，最多 500 字符"
          />
        </el-form-item>
        <el-form-item label="经纬度">
          <div class="scenic-page__coord">
            <el-input-number
              v-model="form.longitude"
              :precision="7"
              :step="0.0001"
              :min="-180"
              :max="180"
              placeholder="经度"
              style="width: 50%"
            />
            <el-input-number
              v-model="form.latitude"
              :precision="7"
              :step="0.0001"
              :min="-90"
              :max="90"
              placeholder="纬度"
              style="width: 50%"
            />
          </div>
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model="form.address" placeholder="选填，详细地址" />
        </el-form-item>
        <el-form-item label="开放时间" prop="openHours">
          <el-input v-model="form.openHours" placeholder="如 08:30-17:00" />
        </el-form-item>
        <el-form-item label="封面图">
          <!-- 编辑时景区已存在，携带景区 ID 作为存储路径；新增时暂无 ID 不传 -->
          <UploadFile
            v-model="form.coverImage"
            type="image"
            :scenic-area-id="dialogMode === 'edit' ? form.id : undefined"
          />
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

.scenic-page {
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

  &__coord {
    display: flex;
    gap: @spacing-sm;
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: @spacing-md;
  }
}
</style>
