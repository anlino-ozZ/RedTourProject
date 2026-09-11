<script setup lang="ts">
// PC管理后台 文物管理页（PRD 5.7，表 artifact）
// 超管可增删改全部景区文物，普管仅查看管辖景区文物（只读）
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { RedButton } from '@red-tour-project/common'
import {
  getAdminArtifacts,
  createAdminArtifact,
  updateAdminArtifact,
  deleteAdminArtifact,
  type AdminArtifactItem,
  type AdminArtifactPayload,
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

// ===== 列表 =====
const loading = ref(false)
const tableData = ref<AdminArtifactItem[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  pageSize: 10,
  keyword: '',
  category: '',
  scenicAreaId: '' as number | '',
})

async function loadList() {
  loading.value = true
  try {
    const res = await getAdminArtifacts({
      page: query.page,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      category: query.category || undefined,
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

// ===== 新增/编辑弹窗 =====
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增文物' : '编辑文物'))
const formRef = ref<FormInstance>()
const submitting = ref(false)

// 关联字段初始必须为 undefined，数字 0 会绕过 required 校验
const form = reactive<{
  id?: number
  scenicAreaId?: number
  name: string
  era: string
  category: string
  location: string
  intro: string
  imageList: string[] // 图片 URL 动态列表，提交时过滤空值映射到 images
  wikiRef: string
  status: number
}>({
  id: undefined,
  scenicAreaId: boundScenicAreaId.value,
  name: '',
  era: '',
  category: '',
  location: '',
  intro: '',
  imageList: [''],
  wikiRef: '',
  status: 1,
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入文物名称', trigger: 'blur' },
    { min: 1, max: 100, message: '名称长度为 1-100 字符', trigger: 'blur' },
  ],
  scenicAreaId: [{ required: true, message: '请选择所属景区', trigger: 'change' }],
  intro: [{ required: true, message: '请输入文物简介', trigger: 'blur' }],
  era: [{ max: 50, message: '年代最多 50 字符', trigger: 'blur' }],
  category: [{ max: 50, message: '品类最多 50 字符', trigger: 'blur' }],
  location: [{ max: 100, message: '地点最多 100 字符', trigger: 'blur' }],
}

// ===== 图片 URL 动态行 =====
function addImageRow() {
  if (form.imageList.length >= 9) {
    ElMessage.warning('最多上传 9 张图片')
    return
  }
  form.imageList.push('')
}

function removeImageRow(index: number) {
  form.imageList.splice(index, 1)
  if (form.imageList.length === 0) {
    form.imageList.push('')
  }
}

/** 已填写的有效图片 URL（用于预览） */
const validImages = computed(() => form.imageList.map((u) => u.trim()).filter(Boolean))

function resetForm() {
  form.id = undefined
  form.scenicAreaId = boundScenicAreaId.value
  form.name = ''
  form.era = ''
  form.category = ''
  form.location = ''
  form.intro = ''
  form.imageList = ['']
  form.wikiRef = ''
  form.status = 1
}

function openCreate() {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEdit(row: AdminArtifactItem) {
  resetForm()
  dialogMode.value = 'edit'
  form.id = row.id
  form.scenicAreaId = row.scenicAreaId
  form.name = row.name
  form.era = row.era || ''
  form.category = row.category || ''
  form.location = row.location || ''
  form.intro = row.intro || ''
  form.imageList = row.images && row.images.length > 0 ? [...row.images] : ['']
  form.wikiRef = row.wikiRef || ''
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload: AdminArtifactPayload = {
        name: form.name,
        scenicAreaId: form.scenicAreaId!,
        era: form.era || undefined,
        category: form.category || undefined,
        location: form.location || undefined,
        intro: form.intro,
        images: validImages.value.length > 0 ? validImages.value : undefined,
        wikiRef: form.wikiRef || undefined,
        status: form.status,
      }
      if (dialogMode.value === 'create') {
        await createAdminArtifact(payload)
        ElMessage.success('文物创建成功')
      } else if (form.id) {
        await updateAdminArtifact(form.id, payload)
        ElMessage.success('文物更新成功')
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
async function handleDelete(row: AdminArtifactItem) {
  try {
    await ElMessageBox.confirm(`确定要删除文物 "${row.name}" 吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteAdminArtifact(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 错误提示已由拦截器统一弹出
  }
}

// ===== 工具函数 =====
function statusText(status: number) {
  return status === 1 ? '上架' : '下架'
}

function statusTagType(status: number) {
  return status === 1 ? 'success' : 'info'
}

function firstImage(row: AdminArtifactItem) {
  return row.images && row.images.length > 0 ? row.images[0] : ''
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
  <div class="artifact-page">
    <!-- 筛选栏 -->
    <div class="artifact-page__toolbar">
      <div class="artifact-page__search">
        <el-input
          v-model="query.keyword"
          placeholder="文物名称搜索"
          clearable
          style="width: 170px"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="query.category"
          placeholder="品类，如 武器 / 文献"
          clearable
          style="width: 170px"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-if="!isReadOnly"
          v-model="query.scenicAreaId"
          placeholder="景区筛选"
          clearable
          filterable
          style="width: 170px"
        >
          <el-option
            v-for="s in scenicOptions"
            :key="s.id"
            :label="s.name"
            :value="s.id"
          />
        </el-select>
        <RedButton type="primary" size="small" @click="handleSearch">搜索</RedButton>
      </div>
      <RedButton v-if="!isReadOnly" type="primary" size="small" @click="openCreate">
        + 新增文物
      </RedButton>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
      <el-table-column label="图片" width="90">
        <template #default="{ row }">
          <el-image
            v-if="firstImage(row)"
            :src="firstImage(row)"
            fit="cover"
            class="artifact-page__thumb"
            :preview-src-list="row.images"
            preview-teleported
          />
          <span v-else class="artifact-page__no-img">无图</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="文物名称" min-width="140" />
      <el-table-column label="年代" width="100">
        <template #default="{ row }">
          {{ row.era || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="品类" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.category" effect="plain">{{ row.category }}</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="出土地点" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.location || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="所属景区" width="130">
        <template #default="{ row }">
          {{ scenicNameMap.get(row.scenicAreaId) ?? `#${row.scenicAreaId}` }}
        </template>
      </el-table-column>
      <el-table-column label="简介" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.intro || '—' }}
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
    <div class="artifact-page__pagination">
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
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" size="default">
        <el-form-item label="文物名称" prop="name">
          <el-input v-model="form.name" placeholder="1-100 字符" />
        </el-form-item>
        <el-form-item label="所属景区" prop="scenicAreaId">
          <el-select
            v-model="form.scenicAreaId"
            placeholder="请选择景区"
            filterable
            :disabled="isReadOnly"
            style="width: 100%"
          >
            <el-option
              v-for="s in scenicOptions"
              :key="s.id"
              :label="s.name"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="年代" prop="era">
          <el-input v-model="form.era" placeholder="选填，如 1935年" />
        </el-form-item>
        <el-form-item label="品类" prop="category">
          <el-input v-model="form.category" placeholder="选填，如 武器 / 文献 / 生活用品" />
        </el-form-item>
        <el-form-item label="出土地点" prop="location">
          <el-input v-model="form.location" placeholder="选填，出土/收藏地点" />
        </el-form-item>
        <el-form-item label="文物简介" prop="intro">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="3"
            placeholder="必填"
          />
        </el-form-item>
        <el-form-item label="图片">
          <div class="artifact-page__image-editor">
            <div
              v-for="(url, index) in form.imageList"
              :key="index"
              class="artifact-page__image-row"
            >
              <el-input v-model="form.imageList[index]" placeholder="图片 URL" />
              <el-button
                link
                type="danger"
                :disabled="form.imageList.length === 1 && !url"
                @click="removeImageRow(index)"
              >
                删除
              </el-button>
            </div>
            <el-button link type="primary" @click="addImageRow">+ 添加图片（最多 9 张）</el-button>
            <div v-if="validImages.length > 0" class="artifact-page__image-previews">
              <el-image
                v-for="(img, i) in validImages"
                :key="i"
                :src="img"
                fit="cover"
                class="artifact-page__preview"
                :preview-src-list="validImages"
                :initial-index="i"
                preview-teleported
              />
            </div>
          </div>
        </el-form-item>
        <el-form-item label="Wiki条目">
          <el-input v-model="form.wikiRef" placeholder="选填，关联 Wiki 条目路径" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="上架"
            inactive-text="下架"
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

.artifact-page {
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

  &__thumb {
    width: 56px;
    height: 56px;
    border-radius: 4px;
    border: 1px solid @color-border;
    cursor: pointer;
  }

  &__no-img {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 56px;
    border-radius: 4px;
    background: @color-bg-page;
    color: @color-text-secondary;
    font-size: @font-size-sm;
  }

  &__image-editor {
    width: 100%;
  }

  &__image-row {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    margin-bottom: @spacing-xs;
  }

  &__image-previews {
    display: flex;
    flex-wrap: wrap;
    gap: @spacing-xs;
    margin-top: @spacing-sm;
  }

  &__preview {
    width: 64px;
    height: 64px;
    border-radius: 4px;
    border: 1px solid @color-border;
    cursor: pointer;
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: @spacing-md;
  }
}
</style>
