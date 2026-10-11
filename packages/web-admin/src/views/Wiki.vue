<script setup lang="ts">
// PC管理后台 知识库（Wiki 条目）管理页（接口文档 14.5，表 wiki）
// 上传史料素材 → 后端编译为 Markdown 百科；支持编辑正文、重新编译、查看引用关系
// 普管可管理管辖景区的 Wiki（景区自动绑定），超管可管理全部景区
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { RedButton, formatDate } from '@red-tour-project/common'
import {
  getAdminWikiList,
  uploadAdminWiki,
  getAdminWikiDetail,
  updateAdminWiki,
  compileAdminWiki,
  deleteAdminWiki,
  type AdminWikiItem,
  type AdminWikiDetail,
  type WikiCompileStatus,
} from '@/api/admin'
import { getScenicAreas } from '@/api'
import { useUserStore } from '@/store'
import type { ScenicAreaInfo } from '@red-tour-project/common'

const userStore = useUserStore()
const isSuperAdmin = computed(() => userStore.isSuperAdmin)

// 普管管辖景区（自动绑定，不可切换）
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

// ===== 编译状态元信息 =====
const statusMeta: Record<
  WikiCompileStatus,
  { text: string; type: 'info' | 'primary' | 'success' | 'danger' }
> = {
  pending: { text: '待编译', type: 'info' },
  compiling: { text: '编译中', type: 'primary' },
  done: { text: '已完成', type: 'success' },
  failed: { text: '失败', type: 'danger' },
}

// ===== 列表 =====
const loading = ref(false)
const tableData = ref<AdminWikiItem[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  pageSize: 10,
  keyword: '',
  compileStatus: '' as WikiCompileStatus | '',
  scenicAreaId: (boundScenicAreaId.value ?? '') as number | '',
})

async function loadList() {
  loading.value = true
  try {
    const res = await getAdminWikiList({
      page: query.page,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      compileStatus: query.compileStatus || undefined,
      scenicAreaId: (boundScenicAreaId.value ?? query.scenicAreaId) as number,
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
const dialogTitle = computed(() =>
  dialogMode.value === 'create' ? '新增 Wiki 条目' : '编辑 Wiki 条目',
)
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<{
  id?: number
  scenicAreaId?: number
  title: string
  content: string
  tags: string[]
}>({
  id: undefined,
  scenicAreaId: boundScenicAreaId.value,
  title: '',
  content: '',
  tags: [],
})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入素材标题', trigger: 'blur' },
    { min: 1, max: 100, message: '标题长度为 1-100 字符', trigger: 'blur' },
  ],
  content: [{ required: true, message: '请输入素材内容', trigger: 'blur' }],
  scenicAreaId: [{ required: true, message: '请选择所属景区', trigger: 'change' }],
}

function resetForm() {
  form.id = undefined
  form.scenicAreaId = boundScenicAreaId.value
  form.title = ''
  form.content = ''
  form.tags = []
}

function openCreate() {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

async function openEdit(row: AdminWikiItem) {
  resetForm()
  dialogMode.value = 'edit'
  form.id = row.id
  form.scenicAreaId = row.scenicAreaId
  form.title = row.title
  form.tags = row.tags ? [...row.tags] : []
  // 正文需调详情接口拉取
  try {
    const detail = await getAdminWikiDetail(row.id)
    form.content = detail.content || ''
  } catch {
    form.content = ''
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (dialogMode.value === 'create') {
        await uploadAdminWiki({
          scenicAreaId: form.scenicAreaId!,
          title: form.title,
          content: form.content,
          tags: form.tags.length > 0 ? form.tags : undefined,
        })
        ElMessage.success('素材已提交，正在编译')
      } else if (form.id) {
        await updateAdminWiki(form.id, {
          title: form.title,
          content: form.content,
          tags: form.tags,
        })
        ElMessage.success('条目已更新')
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

// ===== 标签输入 =====
const tagInput = ref('')
const tagInputVisible = ref(false)

function showTagInput() {
  tagInputVisible.value = true
}

function confirmTag() {
  const t = tagInput.value.trim()
  if (t && !form.tags.includes(t)) {
    form.tags.push(t)
  }
  tagInput.value = ''
  tagInputVisible.value = false
}

function removeTag(tag: string) {
  form.tags = form.tags.filter((t) => t !== tag)
}

// ===== 从文本文件导入内容（接口只收文本，前端读取 .txt/.md 内容填入） =====
const fileInputRef = ref<HTMLInputElement>()

function triggerImportFile() {
  fileInputRef.value?.click()
}

function handleImportFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    const text = reader.result as string
    form.content = form.content ? `${form.content}\n\n${text}` : text
    ElMessage.success(`已导入文件「${file.name}」内容`)
  }
  reader.onerror = () => ElMessage.error('文件读取失败')
  reader.readAsText(file)
  // 重置 input 以便重复选择同一文件
  ;(e.target as HTMLInputElement).value = ''
}

// ===== 重新编译 =====
const compilingId = ref<number | null>(null)

async function handleCompile(row: AdminWikiItem) {
  compilingId.value = row.id
  try {
    await compileAdminWiki(row.id)
    ElMessage.success('已触发重新编译')
    loadList()
  } catch {
    // 错误提示已由拦截器统一弹出
  } finally {
    compilingId.value = null
  }
}

// ===== 删除 =====
async function handleDelete(row: AdminWikiItem) {
  try {
    await ElMessageBox.confirm(`确定要删除 Wiki 条目 "${row.title}" 吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteAdminWiki(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 错误提示已由拦截器统一弹出
  }
}

// ===== 详情抽屉 =====
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminWikiDetail | null>(null)

async function openDetail(row: AdminWikiItem) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getAdminWikiDetail(row.id)
  } catch {
    // 错误提示已由拦截器统一弹出
  } finally {
    detailLoading.value = false
  }
}

// ===== 工具函数 =====
function formatTime(t: string) {
  return formatDate(t, 'YYYY-MM-DD HH:mm')
}

function tagsText(row: AdminWikiItem) {
  return row.tags && row.tags.length > 0 ? row.tags.join('、') : '—'
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
  <div class="wiki-page">
    <!-- 筛选栏 -->
    <div class="wiki-page__toolbar">
      <div class="wiki-page__search">
        <el-input
          v-model="query.keyword"
          placeholder="标题搜索"
          clearable
          style="width: 170px"
          @keyup.enter="handleSearch"
        />
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
        <el-select
          v-model="query.compileStatus"
          placeholder="编译状态"
          clearable
          style="width: 130px"
        >
          <el-option v-for="(meta, key) in statusMeta" :key="key" :label="meta.text" :value="key" />
        </el-select>
        <RedButton type="primary" size="small" @click="handleSearch">搜索</RedButton>
      </div>
      <RedButton type="primary" size="small" @click="openCreate">+ 新增条目</RedButton>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column label="所属景区" width="130">
        <template #default="{ row }">
          {{ scenicNameMap.get(row.scenicAreaId) ?? `#${row.scenicAreaId}` }}
        </template>
      </el-table-column>
      <el-table-column label="标签" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.tags && row.tags.length > 0">
            <el-tag v-for="t in row.tags" :key="t" effect="plain" class="wiki-page__tag">
              {{ t }}
            </el-tag>
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="编译状态" width="100">
        <template #default="{ row }: { row: AdminWikiItem }">
          <el-tag :type="statusMeta[row.compileStatus]?.type" effect="plain">
            {{ statusMeta[row.compileStatus]?.text || row.compileStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="文件路径" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <code v-if="row.filePath" class="wiki-page__path">{{ row.filePath }}</code>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">
          {{ formatTime(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            type="warning"
            size="small"
            :loading="compilingId === row.id"
            @click="handleCompile(row)"
          >
            重新编译
          </el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="wiki-page__pagination">
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
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" size="default">
        <el-form-item label="所属景区" prop="scenicAreaId">
          <el-select
            v-model="form.scenicAreaId"
            placeholder="请选择景区"
            filterable
            :disabled="!isSuperAdmin"
            style="width: 100%"
          >
            <el-option v-for="s in scenicOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="素材标题" prop="title">
          <el-input v-model="form.title" placeholder="1-100 字符" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="素材内容" prop="content">
          <div class="wiki-page__content-editor">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="10"
              placeholder="输入 Markdown / 纯文本史料内容"
              class="wiki-page__content-input"
            />
            <div class="wiki-page__import-row">
              <el-button size="small" @click="triggerImportFile">
                从文本文件导入（.txt/.md）
              </el-button>
              <input
                ref="fileInputRef"
                type="file"
                accept=".txt,.md,.markdown,text/plain,text/markdown"
                style="display: none"
                @change="handleImportFile"
              />
              <span class="wiki-page__import-tip">文件内容将追加到上方文本框</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="分类标签">
          <div class="wiki-page__tags">
            <el-tag
              v-for="tag in form.tags"
              :key="tag"
              closable
              effect="plain"
              class="wiki-page__tag"
              @close="removeTag(tag)"
            >
              {{ tag }}
            </el-tag>
            <el-input
              v-if="tagInputVisible"
              v-model="tagInput"
              size="small"
              style="width: 120px"
              @keyup.enter="confirmTag"
              @blur="confirmTag"
            />
            <el-button v-else size="small" @click="showTagInput">+ 添加标签</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <RedButton type="ghost" size="small" @click="dialogVisible = false">取消</RedButton>
        <RedButton type="primary" size="small" :loading="submitting" @click="handleSubmit">
          确定
        </RedButton>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="Wiki 条目详情" size="60%" direction="rtl">
      <div v-loading="detailLoading" class="wiki-page__detail">
        <template v-if="detail">
          <h3 class="wiki-page__detail-title">{{ detail.title }}</h3>
          <el-descriptions :column="2" border size="small" class="wiki-page__desc">
            <el-descriptions-item label="所属景区">
              {{ scenicNameMap.get(detail.scenicAreaId) ?? `#${detail.scenicAreaId}` }}
            </el-descriptions-item>
            <el-descriptions-item label="编译状态">
              <el-tag :type="statusMeta[detail.compileStatus]?.type" effect="plain">
                {{ statusMeta[detail.compileStatus]?.text }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="文件路径">
              <code v-if="detail.filePath" class="wiki-page__path">{{ detail.filePath }}</code>
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">
              {{ formatTime(detail.updatedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="标签" :span="2">
              <template v-if="detail.tags && detail.tags.length > 0">
                <el-tag v-for="t in detail.tags" :key="t" effect="plain" class="wiki-page__tag">
                  {{ t }}
                </el-tag>
              </template>
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item label="引用关系" :span="2">
              <template v-if="detail.links && detail.links.length > 0">
                <el-tag
                  v-for="l in detail.links"
                  :key="l"
                  type="success"
                  effect="plain"
                  class="wiki-page__tag"
                >
                  [[{{ l }}]]
                </el-tag>
              </template>
              <span v-else>—</span>
            </el-descriptions-item>
          </el-descriptions>
          <div class="wiki-page__detail-content">
            <div class="wiki-page__detail-label">正文内容</div>
            <pre v-if="detail.content" class="wiki-page__content-pre">{{ detail.content }}</pre>
            <span v-else class="wiki-page__empty">暂无正文</span>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.wiki-page {
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

  &__tag {
    margin-right: @spacing-xs;
    margin-bottom: 2px;
  }

  &__path {
    font-size: @font-size-sm;
    color: @color-text-regular;
    background: @color-bg-page;
    padding: 2px 6px;
    border-radius: 3px;
    word-break: break-all;
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: @spacing-md;
  }

  &__content-editor {
    width: 100%;
  }

  &__content-input {
    :deep(textarea) {
      font-family: Consolas, Monaco, 'Courier New', monospace;
      font-size: @font-size-sm;
      line-height: 1.6;
    }
  }

  &__import-row {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    margin-top: @spacing-xs;
  }

  &__import-tip {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__tags {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: @spacing-xs;
  }

  &__detail {
    padding: 0 @spacing-md;
  }

  &__detail-title {
    margin: 0 0 @spacing-md;
    font-size: 18px;
  }

  &__desc {
    margin-bottom: @spacing-md;
  }

  &__detail-content {
    margin-top: @spacing-md;
  }

  &__detail-label {
    margin-bottom: @spacing-xs;
    font-weight: 600;
    color: @color-text-regular;
  }

  &__content-pre {
    margin: 0;
    padding: @spacing-sm;
    background: @color-bg-page;
    border: 1px solid @color-border;
    border-radius: 4px;
    font-family: Consolas, Monaco, 'Courier New', monospace;
    font-size: @font-size-sm;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 60vh;
    overflow: auto;
  }

  &__empty {
    color: @color-text-secondary;
    font-size: @font-size-sm;
  }
}
</style>
