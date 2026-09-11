<script setup lang="ts">
// PC管理后台 用户管理页（仅超管，PRD 5.1）
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { RedButton } from '@red-tour-project/common'
import {
  getAdminUsers,
  createAdminUser,
  updateAdminUser,
  deleteAdminUser,
  type AdminUserItem,
  type AdminUserPayload,
} from '@/api/admin'
import { getScenicAreas } from '@/api'
import type { UserRole, ScenicAreaInfo } from '@red-tour-project/common'

// ===== 列表 =====
const loading = ref(false)
const tableData = ref<AdminUserItem[]>([])
const total = ref(0)

// ===== 景区下拉数据（用于绑定管辖景区）=====
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

const query = reactive({
  page: 1,
  pageSize: 10,
  username: '',
  role: '' as '' | UserRole,
})

async function loadList() {
  loading.value = true
  try {
    const res = await getAdminUsers({
      page: query.page,
      pageSize: query.pageSize,
      username: query.username || undefined,
      role: query.role || undefined,
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
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增用户' : '编辑用户'))
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<AdminUserPayload & { id?: number }>({
  username: '',
  password: '',
  nickname: '',
  role: 'admin' as UserRole,
  scenicAreaId: null,
  status: 1,
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为 3-20 字符', trigger: 'blur' },
  ],
  password: [
    {
      // 新增必填；编辑时可为空（表示不修改）
      validator: (_rule, value: string, callback) => {
        if (dialogMode.value === 'create' && !value) {
          return callback(new Error('请输入密码'))
        }
        if (value && (value.length < 6 || value.length > 32)) {
          return callback(new Error('密码长度为 6-32 字符'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  nickname: [{ max: 20, message: '昵称最多 20 字符', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  scenicAreaId: [
    {
      validator: (_rule, value: number | null, callback) => {
        if (form.role === 'admin' && !value) {
          return callback(new Error('普管必须绑定管辖景区'))
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

function resetForm() {
  form.id = undefined
  form.username = ''
  form.password = ''
  form.nickname = ''
  form.role = 'admin'
  form.scenicAreaId = null
  form.status = 1
}

function openCreate() {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEdit(row: AdminUserItem) {
  resetForm()
  dialogMode.value = 'edit'
  form.id = row.id
  form.username = row.username
  form.password = '' // 编辑时留空表示不修改
  form.nickname = row.nickname || ''
  form.role = row.role
  form.scenicAreaId = row.scenicAreaId ?? null
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload: AdminUserPayload = {
        username: form.username,
        password: form.password || undefined,
        nickname: form.nickname || undefined,
        role: form.role,
        scenicAreaId: form.role === 'admin' ? form.scenicAreaId : null,
        status: form.status,
      }
      if (dialogMode.value === 'create') {
        await createAdminUser(payload)
        ElMessage.success('用户创建成功')
      } else if (form.id) {
        await updateAdminUser(form.id, payload)
        ElMessage.success('用户更新成功')
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
async function handleDelete(row: AdminUserItem) {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户 "${row.username}" 吗？此操作为软删除。`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteAdminUser(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 错误提示已由拦截器统一弹出
  }
}

// ===== 工具函数 =====
const roleTextMap: Record<string, string> = {
  super_admin: '超级管理员',
  admin: '普通管理员',
  tourist: '游客',
}

function roleText(role: string) {
  return roleTextMap[role] || role
}

function statusText(status: number) {
  return status === 1 ? '启用' : '禁用'
}

function statusTagType(status: number) {
  return status === 1 ? 'success' : 'info'
}

// ===== 初始化 =====
onMounted(() => {
  loadList()
  loadScenicOptions()
})
</script>

<template>
  <div class="user-page">
    <!-- 搜索栏 -->
    <div class="user-page__toolbar">
      <div class="user-page__search">
        <el-input
          v-model="query.username"
          placeholder="用户名模糊搜索"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.role" placeholder="角色筛选" clearable style="width: 150px">
          <el-option label="超级管理员" value="super_admin" />
          <el-option label="普通管理员" value="admin" />
          <el-option label="游客" value="tourist" />
        </el-select>
        <RedButton type="primary" size="small" @click="handleSearch">搜索</RedButton>
      </div>
      <RedButton type="primary" size="small" @click="openCreate">+ 新增用户</RedButton>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="100" />
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag :type="row.role === 'super_admin' ? 'danger' : 'warning'" effect="plain">
            {{ roleText(row.role) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="管辖景区" width="140">
        <template #default="{ row }">
          {{
            row.scenicAreaId ? (scenicNameMap.get(row.scenicAreaId) ?? `#${row.scenicAreaId}`) : '—'
          }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" effect="plain">
            {{ statusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="user-page__pagination">
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
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" size="default">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="3-20 字符，字母数字下划线"
            :disabled="dialogMode === 'edit'"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="dialogMode === 'edit' ? '留空表示不修改' : '6-32 字符'"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="选填，最多 20 字符" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="超级管理员" value="super_admin" />
            <el-option label="普通管理员" value="admin" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === 'admin'" label="管辖景区" prop="scenicAreaId">
          <el-select
            v-model="form.scenicAreaId"
            placeholder="请选择管辖景区"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option v-for="s in scenicOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
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

.user-page {
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
