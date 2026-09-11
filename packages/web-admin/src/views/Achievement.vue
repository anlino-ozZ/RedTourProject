<script setup lang="ts">
// PC管理后台 成就定义管理页（接口文档 14.13，表 achievement）
// 成就为全部景区公用的全局定义：无景区归属、无上下架状态、列表不分页
// 仅超管可访问（路由 meta.roles 与菜单已限制）
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { RedButton, formatDate } from '@red-tour-project/common'
import {
  getAdminAchievements,
  createAdminAchievement,
  updateAdminAchievement,
  deleteAdminAchievement,
  type AdminAchievementItem,
  type AdminAchievementPayload,
  type AchievementTriggerType,
} from '@/api/admin'

// ===== 触发类型元信息 =====
const triggerMeta: Record<
  AchievementTriggerType,
  { text: string; type: 'primary' | 'warning' | 'danger' | 'success' }
> = {
  visit: { text: '游览', type: 'primary' },
  pose: { text: '姿态', type: 'warning' },
  script: { text: '剧本', type: 'danger' },
  checkin: { text: '打卡', type: 'success' },
}

/** 各类型条件模板：visit 取自接口文档真实示例；其余类型文档未定义 schema，放空对象骨架 */
const conditionTemplates: Record<AchievementTriggerType, string> = {
  visit: '{\n  "type": "first_visit"\n}',
  pose: '{}',
  script: '{}',
  checkin: '{}',
}

/** 各类型条件输入提示（示例仅为填写指引，不作字段契约） */
const conditionPlaceholders: Record<AchievementTriggerType, string> = {
  visit: '如 {"type":"first_visit"}（首次游览）',
  pose: '按 AI 引擎姿态识别约定填写，如 {"action":"salute"}（敬礼/推磨/挥手）',
  script: '按剧本完成规则填写，如 {"scriptId":1}',
  checkin: '按打卡规则填写，如 {"spotId":1} 或 {"count":3}',
}

// ===== 列表（全量，前端筛选） =====
const loading = ref(false)
const allData = ref<AdminAchievementItem[]>([])

const keyword = ref('')
const typeFilter = ref<'' | AchievementTriggerType>('')

const filteredData = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return allData.value.filter((item) => {
    const matchKeyword =
      !kw ||
      item.title.toLowerCase().includes(kw) ||
      (item.description || '').toLowerCase().includes(kw)
    const matchType = !typeFilter.value || item.triggerType === typeFilter.value
    return matchKeyword && matchType
  })
})

async function loadList() {
  loading.value = true
  try {
    allData.value = await getAdminAchievements()
  } catch {
    // 错误提示已由拦截器统一弹出
  } finally {
    loading.value = false
  }
}

// ===== 新增/编辑弹窗 =====
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增成就' : '编辑成就'))
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<{
  id?: number
  title: string
  icon: string
  description: string
  triggerType: AchievementTriggerType
  conditionText: string // 触发条件 JSON 文本（提交时解析为对象）
}>({
  id: undefined,
  title: '',
  icon: '',
  description: '',
  triggerType: 'visit',
  conditionText: '',
})

// 条件是否被手动改过：未改过时切换触发类型自动跟随模板，改过则不覆盖用户内容
const conditionTouched = ref(false)

function syncTemplateByType(type: AchievementTriggerType) {
  if (!conditionTouched.value) {
    form.conditionText = conditionTemplates[type]
  }
}

const rules: FormRules = {
  title: [
    { required: true, message: '请输入成就标题', trigger: 'blur' },
    { min: 1, max: 50, message: '标题长度为 1-50 字符', trigger: 'blur' },
  ],
  description: [{ max: 200, message: '描述最多 200 字符', trigger: 'blur' }],
  triggerType: [{ required: true, message: '请选择触发类型', trigger: 'change' }],
  conditionText: [
    {
      validator: (_rule, value: string, callback) => {
        const text = (value || '').trim()
        // 条件可空
        if (!text) return callback()
        try {
          const parsed = JSON.parse(text)
          if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
            return callback(new Error('触发条件必须是 JSON 对象，如 {"type":"first_visit"}'))
          }
          callback()
        } catch {
          callback(new Error('JSON 格式不合法，请检查后重试'))
        }
      },
      trigger: 'blur',
    },
  ],
}

function resetForm() {
  form.id = undefined
  form.title = ''
  form.icon = ''
  form.description = ''
  form.triggerType = 'visit'
  form.conditionText = ''
  conditionTouched.value = false
  syncTemplateByType('visit')
}

function openCreate() {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEdit(row: AdminAchievementItem) {
  resetForm()
  dialogMode.value = 'edit'
  form.id = row.id
  form.title = row.title
  form.icon = row.icon || ''
  form.description = row.description || ''
  form.triggerType = row.triggerType
  if (row.triggerCondition && Object.keys(row.triggerCondition).length > 0) {
    form.conditionText = JSON.stringify(row.triggerCondition, null, 2)
    // 与当前类型默认模板完全一致则视为未自定义，切换类型时仍可自动跟随模板
    conditionTouched.value =
      JSON.stringify(row.triggerCondition) !== JSON.stringify(parseTemplate(row.triggerType))
  } else {
    form.conditionText = conditionTemplates[row.triggerType]
    conditionTouched.value = false
  }
  dialogVisible.value = true
}

function parseTemplate(type: AchievementTriggerType): Record<string, unknown> {
  return JSON.parse(conditionTemplates[type]) as Record<string, unknown>
}

function handleTypeChange(type: AchievementTriggerType) {
  form.triggerType = type
  syncTemplateByType(type)
}

/** 重置按钮：恢复当前类型默认模板 */
function resetConditionTemplate() {
  form.conditionText = conditionTemplates[form.triggerType]
  conditionTouched.value = false
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const conditionText = form.conditionText.trim()
      const payload: AdminAchievementPayload = {
        title: form.title,
        triggerType: form.triggerType,
        icon: form.icon || undefined,
        description: form.description || undefined,
        triggerCondition: conditionText
          ? (JSON.parse(conditionText) as Record<string, unknown>)
          : null,
      }
      if (dialogMode.value === 'create') {
        await createAdminAchievement(payload)
        ElMessage.success('成就创建成功')
      } else if (form.id) {
        await updateAdminAchievement(form.id, payload)
        ElMessage.success('成就更新成功')
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
async function handleDelete(row: AdminAchievementItem) {
  try {
    await ElMessageBox.confirm(
      `确定要删除成就 "${row.title}" 吗？已有游客解锁记录的成就无法删除。`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    await deleteAdminAchievement(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 错误提示已由拦截器统一弹出
  }
}

// ===== 工具函数 =====
function conditionSummary(row: AdminAchievementItem) {
  if (!row.triggerCondition || Object.keys(row.triggerCondition).length === 0) return '—'
  return JSON.stringify(row.triggerCondition)
}

function formatTime(t: string) {
  return formatDate(t, 'YYYY-MM-DD HH:mm')
}

// ===== 初始化 =====
onMounted(() => {
  loadList()
})
</script>

<template>
  <div class="achievement-page">
    <!-- 筛选栏 -->
    <div class="achievement-page__toolbar">
      <div class="achievement-page__search">
        <el-input v-model="keyword" placeholder="标题/描述搜索" clearable style="width: 180px" />
        <el-select v-model="typeFilter" placeholder="触发类型" clearable style="width: 130px">
          <el-option
            v-for="(meta, key) in triggerMeta"
            :key="key"
            :label="meta.text"
            :value="key"
          />
        </el-select>
      </div>
      <RedButton type="primary" size="small" @click="openCreate">+ 新增成就</RedButton>
    </div>

    <!-- 表格（全量，前端筛选） -->
    <el-table :data="filteredData" v-loading="loading" border stripe style="width: 100%">
      <el-table-column label="图标" width="80">
        <template #default="{ row }">
          <el-image
            v-if="row.icon"
            :src="row.icon"
            fit="cover"
            class="achievement-page__icon"
            :preview-src-list="[row.icon]"
            preview-teleported
          />
          <span v-else class="achievement-page__no-icon">无</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="成就标题" min-width="130" />
      <el-table-column label="描述" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.description || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="触发类型" width="100">
        <template #default="{ row }">
          <el-tag :type="triggerMeta[row.triggerType]?.type" effect="plain">
            {{ triggerMeta[row.triggerType]?.text || row.triggerType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="触发条件" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <code class="achievement-page__condition">{{ conditionSummary(row) }}</code>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="150">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="achievement-page__total">共 {{ filteredData.length }} 项成就</div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" size="default">
        <el-form-item label="成就标题" prop="title">
          <el-input v-model="form.title" placeholder="1-50 字符" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="选填，图标图片 URL" />
        </el-form-item>
        <el-form-item v-if="form.icon" label="图标预览">
          <el-image
            :src="form.icon"
            fit="cover"
            class="achievement-page__preview"
            :preview-src-list="[form.icon]"
            preview-teleported
          />
        </el-form-item>
        <el-form-item label="成就描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="选填，最多 200 字符"
          />
        </el-form-item>
        <el-form-item label="触发类型" prop="triggerType">
          <el-radio-group :model-value="form.triggerType" @change="handleTypeChange">
            <el-radio-button v-for="(meta, key) in triggerMeta" :key="key" :value="key">
              {{ meta.text }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="触发条件" prop="conditionText">
          <div class="achievement-page__condition-editor">
            <el-input
              v-model="form.conditionText"
              type="textarea"
              :rows="5"
              :placeholder="conditionPlaceholders[form.triggerType]"
              class="achievement-page__json-input"
              @input="conditionTouched = true"
            />
            <el-button link type="primary" size="small" @click="resetConditionTemplate">
              重置为「{{ triggerMeta[form.triggerType].text }}」默认模板
            </el-button>
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
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.achievement-page {
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

  &__icon {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    border: 1px solid @color-border;
    cursor: pointer;
  }

  &__no-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    border-radius: 50%;
    background: @color-bg-page;
    color: @color-text-secondary;
    font-size: @font-size-sm;
  }

  &__condition {
    font-size: @font-size-sm;
    color: @color-text-regular;
    background: @color-bg-page;
    padding: 2px 6px;
    border-radius: 3px;
    word-break: break-all;
  }

  &__total {
    margin-top: @spacing-sm;
    color: @color-text-secondary;
    font-size: @font-size-sm;
    text-align: right;
  }

  &__condition-editor {
    width: 100%;
  }

  &__json-input {
    :deep(textarea) {
      font-family: Consolas, Monaco, 'Courier New', monospace;
      font-size: @font-size-sm;
    }
  }

  &__preview {
    width: 64px;
    height: 64px;
    border-radius: 50%;
    border: 1px solid @color-border;
    cursor: pointer;
  }
}
</style>
