<script setup lang="ts">
// PC管理后台 剧本管理页（接口文档 14.8，表 scripts）
// 红色剧本杀剧本：角色列表 + 剧情节点树（场景文本 + 选项跳转），完成剧本可解锁成就
// 普管可管理管辖景区的剧本（景区自动绑定），超管可管理全部景区
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { RedButton, formatDate } from '@red-tour-project/common'
import {
  getAdminScripts,
  createAdminScript,
  updateAdminScript,
  deleteAdminScript,
  getAdminAchievements,
  type AdminScriptItem,
  type ScriptRole,
  type ScriptNode,
  type AdminAchievementItem,
} from '@/api/admin'
import { getScenicAreas } from '@/api'
import { resolveFileUrl } from '@/api/upload'
import UploadFile from '@/components/UploadFile.vue'
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
    return
  }
  // 剧本列表接口要求必传 scenicAreaId：超管未选时默认选中第一个景区
  if (isSuperAdmin.value && !query.scenicAreaId && scenicOptions.value.length > 0) {
    query.scenicAreaId = scenicOptions.value[0].id
  }
}

// ===== 成就（用于弹窗多选 + 表格标题映射）=====
const achievements = ref<AdminAchievementItem[]>([])
const achievementTitleMap = computed(() => {
  const map = new Map<number, string>()
  for (const a of achievements.value) {
    map.set(a.id, a.title)
  }
  return map
})

async function loadAchievements() {
  try {
    achievements.value = await getAdminAchievements()
  } catch {
    // 错误提示已由拦截器统一弹出
  }
}

// ===== 列表 =====
const loading = ref(false)
const tableData = ref<AdminScriptItem[]>([])
const query = reactive({
  keyword: '',
  status: '' as number | '',
  scenicAreaId: (boundScenicAreaId.value ?? '') as number | '',
})

async function loadList() {
  // 后端要求必传 scenicAreaId：未选中景区时不发请求，展示空列表
  const effectiveScenicAreaId = boundScenicAreaId.value ?? (query.scenicAreaId as number | '')
  if (!effectiveScenicAreaId) {
    tableData.value = []
    return
  }
  loading.value = true
  try {
    tableData.value = await getAdminScripts(effectiveScenicAreaId as number)
  } catch {
    // 错误提示已由拦截器统一弹出
  } finally {
    loading.value = false
  }
}

/** 前端过滤：关键词（名称/简介）+ 状态 */
const filteredData = computed(() => {
  const kw = query.keyword.trim()
  return tableData.value.filter((row) => {
    if (query.status !== '' && row.status !== query.status) return false
    if (kw && !(row.title.includes(kw) || (row.description || '').includes(kw))) return false
    return true
  })
})

function handleSearch() {
  // 前端过滤为 computed，无需重新请求
}

// ===== 新增/编辑弹窗 =====
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增剧本' : '编辑剧本'))
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<{
  id?: number
  scenicAreaId?: number
  title: string
  description: string
  coverImage: string
  roles: ScriptRole[]
  nodes: ScriptNode[]
  achievementIds: number[]
  status: number
}>({
  id: undefined,
  scenicAreaId: boundScenicAreaId.value,
  title: '',
  description: '',
  coverImage: '',
  roles: [],
  nodes: [],
  achievementIds: [],
  status: 1,
})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入剧本名称', trigger: 'blur' },
    { min: 1, max: 50, message: '名称长度为 1-50 字符', trigger: 'blur' },
  ],
  description: [{ max: 200, message: '简介最多 200 字符', trigger: 'blur' }],
  scenicAreaId: [{ required: true, message: '请选择所属景区', trigger: 'change' }],
}

function resetForm() {
  form.id = undefined
  form.scenicAreaId = boundScenicAreaId.value
  form.title = ''
  form.description = ''
  form.coverImage = ''
  form.roles = []
  form.nodes = []
  form.achievementIds = []
  form.status = 1
}

function openCreate() {
  resetForm()
  // 预置 1 个角色、3 个节点（满足最低要求）
  form.roles = [{ name: '', avatar: '', skill: '' }]
  form.nodes = [1, 2, 3].map((id) => ({
    id,
    sceneText: '',
    options: [{ text: '', nextNodeId: null }],
  }))
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEdit(row: AdminScriptItem) {
  resetForm()
  dialogMode.value = 'edit'
  form.id = row.id
  form.scenicAreaId = row.scenicAreaId
  form.title = row.title
  form.description = row.description || ''
  form.coverImage = row.coverImage || ''
  form.roles = (row.roles || []).map((r) => ({ ...r }))
  form.nodes = JSON.parse(JSON.stringify(row.nodes || [])) as ScriptNode[]
  form.achievementIds = (row.achievements || []).map((a) => a.id)
  form.status = row.status
  dialogVisible.value = true
}

// ===== 角色编辑 =====
function addRole() {
  form.roles.push({ name: '', avatar: '', skill: '' })
}

function removeRole(index: number) {
  form.roles.splice(index, 1)
}

// ===== 剧情节点编辑 =====
const nodeIdOptions = computed(() => form.nodes.map((n) => n.id))

function addNode() {
  const maxId = form.nodes.reduce((m, n) => Math.max(m, n.id), 0)
  form.nodes.push({ id: maxId + 1, sceneText: '', options: [{ text: '', nextNodeId: null }] })
}

function removeNode(index: number) {
  form.nodes.splice(index, 1)
}

function addOption(node: ScriptNode) {
  node.options.push({ text: '', nextNodeId: null })
}

function removeOption(node: ScriptNode, index: number) {
  node.options.splice(index, 1)
}

/** 校验角色与节点结构，返回错误提示；通过返回空串 */
function validateStructure(): string {
  if (form.roles.length < 1) {
    return '至少需要 1 个角色'
  }
  for (let i = 0; i < form.roles.length; i++) {
    if (!form.roles[i].name.trim()) {
      return `第 ${i + 1} 个角色未填写名称`
    }
  }
  if (form.nodes.length < 3) {
    return '剧情节点至少需要 3 个'
  }
  const idSet = new Set(form.nodes.map((n) => n.id))
  for (const node of form.nodes) {
    if (!node.sceneText.trim()) {
      return `节点 ${node.id} 未填写场景描述`
    }
    // 无选项的节点视为剧终节点，不做选项数量强制
    for (let j = 0; j < node.options.length; j++) {
      const opt = node.options[j]
      if (!opt.text.trim()) {
        return `节点 ${node.id} 的第 ${j + 1} 个选项未填写文本`
      }
      if (opt.nextNodeId != null && !idSet.has(opt.nextNodeId)) {
        return `节点 ${node.id} 的第 ${j + 1} 个选项跳转目标节点 ${opt.nextNodeId} 不存在`
      }
    }
  }
  return ''
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    const structError = validateStructure()
    if (structError) {
      ElMessage.warning(structError)
      return
    }
    const payload = {
      scenicAreaId: form.scenicAreaId!,
      title: form.title,
      description: form.description || undefined,
      coverImage: form.coverImage || undefined,
      roles: form.roles.map((r) => ({
        name: r.name.trim(),
        avatar: r.avatar || undefined,
        skill: r.skill || undefined,
      })),
      nodes: form.nodes.map((n) => ({
        id: n.id,
        sceneText: n.sceneText,
        options: n.options.map((o) => ({
          text: o.text.trim(),
          nextNodeId: o.nextNodeId != null ? o.nextNodeId : undefined,
        })),
      })),
      achievements:
        form.achievementIds.length > 0 ? form.achievementIds.map((id) => ({ id })) : undefined,
      status: form.status,
    }
    submitting.value = true
    try {
      if (dialogMode.value === 'create') {
        await createAdminScript(payload)
        ElMessage.success('剧本已创建')
      } else if (form.id) {
        await updateAdminScript(form.id, payload)
        ElMessage.success('剧本已更新')
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
async function handleDelete(row: AdminScriptItem) {
  try {
    await ElMessageBox.confirm(`确定要删除剧本 "${row.title}" 吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteAdminScript(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 错误提示已由拦截器统一弹出
  }
}

// ===== 展示辅助 =====
function rolesText(row: AdminScriptItem) {
  return row.roles && row.roles.length > 0 ? row.roles.map((r) => r.name).join('、') : '—'
}

function achievementsText(row: AdminScriptItem) {
  if (!row.achievements || row.achievements.length === 0) return '—'
  const titles = row.achievements.map((a) => achievementTitleMap.value.get(a.id) || `#${a.id}`)
  return titles.join('、')
}

function formatTime(t?: string) {
  return t ? formatDate(t, 'YYYY-MM-DD HH:mm') : '—'
}

// ===== 初始化 =====
onMounted(async () => {
  // 先加载景区选项（超管会默认选中第一个），再查列表
  await loadScenicOptions()
  loadAchievements()
  loadList()
})
</script>

<template>
  <div class="script-page">
    <!-- 筛选栏 -->
    <div class="script-page__toolbar">
      <div class="script-page__search">
        <el-input
          v-model="query.keyword"
          placeholder="名称/简介搜索"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px">
          <el-option label="上线" :value="1" />
          <el-option label="下线" :value="0" />
        </el-select>
        <el-select
          v-if="isSuperAdmin"
          v-model="query.scenicAreaId"
          placeholder="请选择景区"
          filterable
          style="width: 160px"
          @change="loadList"
        >
          <el-option v-for="s in scenicOptions" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <RedButton type="primary" size="small" @click="handleSearch">搜索</RedButton>
      </div>
      <RedButton type="primary" size="small" @click="openCreate">+ 新增剧本</RedButton>
    </div>

    <!-- 表格 -->
    <el-table :data="filteredData" v-loading="loading" border stripe style="width: 100%">
      <el-table-column label="封面" width="90">
        <template #default="{ row }">
          <el-image
            v-if="row.coverImage"
            :src="resolveFileUrl(row.coverImage)"
            fit="cover"
            class="script-page__cover"
            :preview-src-list="[resolveFileUrl(row.coverImage)]"
            preview-teleported
          />
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="剧本名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="description" label="简介" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.description || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          {{ rolesText(row) }}
        </template>
      </el-table-column>
      <el-table-column label="剧情节点" width="90" align="center">
        <template #default="{ row }">{{ row.nodes ? row.nodes.length : 0 }} 个</template>
      </el-table-column>
      <el-table-column label="可解锁成就" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ achievementsText(row) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="plain">
            {{ row.status === 1 ? '上线' : '下线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="780px"
      :close-on-click-modal="false"
      top="6vh"
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
        <el-form-item label="剧本名称" prop="title">
          <el-input v-model="form.title" placeholder="1-50 字符" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="简介" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="剧本背景与玩法简介"
          />
        </el-form-item>
        <el-form-item label="封面图">
          <UploadFile v-model="form.coverImage" type="image" :scenic-area-id="form.scenicAreaId" />
        </el-form-item>

        <!-- 角色列表 -->
        <el-form-item label="角色列表">
          <div class="script-page__roles">
            <div v-for="(role, i) in form.roles" :key="i" class="script-page__role-item">
              <div class="script-page__role-fields">
                <el-input v-model="role.name" placeholder="角色名称（必填）" style="width: 180px" />
                <el-input
                  v-model="role.skill"
                  placeholder="技能描述（选填）"
                  style="width: 240px"
                />
                <el-button
                  link
                  type="danger"
                  :disabled="form.roles.length <= 1"
                  @click="removeRole(i)"
                >
                  删除角色
                </el-button>
              </div>
              <div class="script-page__role-avatar">
                <span class="script-page__role-label">头像：</span>
                <UploadFile
                  v-model="role.avatar"
                  type="image"
                  :scenic-area-id="form.scenicAreaId"
                />
              </div>
            </div>
            <el-button link type="primary" @click="addRole">+ 添加角色</el-button>
          </div>
        </el-form-item>

        <!-- 剧情节点 -->
        <el-form-item label="剧情节点">
          <div class="script-page__nodes">
            <div v-for="(node, ni) in form.nodes" :key="node.id" class="script-page__node-item">
              <div class="script-page__node-head">
                <span class="script-page__node-title">节点 {{ node.id }}</span>
                <el-button
                  link
                  type="danger"
                  size="small"
                  :disabled="form.nodes.length <= 3"
                  @click="removeNode(ni)"
                >
                  删除节点
                </el-button>
              </div>
              <el-input
                v-model="node.sceneText"
                type="textarea"
                :rows="2"
                placeholder="场景描述，如：1935年1月，你带领红军到达遵义城外..."
              />
              <div class="script-page__options">
                <div v-for="(opt, oi) in node.options" :key="oi" class="script-page__option-row">
                  <el-input
                    v-model="opt.text"
                    placeholder="选项文本（必填）"
                    style="width: 300px"
                  />
                  <el-select
                    v-model="opt.nextNodeId"
                    placeholder="跳转节点（留空=剧终）"
                    clearable
                    style="width: 190px"
                  >
                    <el-option
                      v-for="nid in nodeIdOptions"
                      :key="nid"
                      :label="`节点 ${nid}`"
                      :value="nid"
                    />
                  </el-select>
                  <el-button
                    link
                    type="danger"
                    :disabled="node.options.length <= 1"
                    @click="removeOption(node, oi)"
                  >
                    删除
                  </el-button>
                </div>
                <el-button link type="primary" size="small" @click="addOption(node)">
                  + 添加选项
                </el-button>
              </div>
            </div>
            <el-button link type="primary" @click="addNode">+ 添加节点</el-button>
          </div>
        </el-form-item>

        <el-form-item label="可解锁成就">
          <el-select
            v-model="form.achievementIds"
            multiple
            filterable
            clearable
            placeholder="选填，完成剧本后解锁的成就"
            style="width: 100%"
          >
            <el-option
              v-for="a in achievements"
              :key="a.id"
              :label="`${a.title}（${a.triggerType}）`"
              :value="a.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上线</el-radio>
            <el-radio :value="0">下线</el-radio>
          </el-radio-group>
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

.script-page {
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

  &__cover {
    width: 56px;
    height: 40px;
    border-radius: 4px;
    border: 1px solid @color-border;
    cursor: pointer;
  }

  &__roles {
    width: 100%;
  }

  &__role-item {
    padding: @spacing-sm;
    border: 1px solid @color-border;
    border-radius: 4px;
    margin-bottom: @spacing-sm;
    background: @color-bg-page;
  }

  &__role-fields {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
  }

  &__role-avatar {
    display: flex;
    align-items: flex-start;
    margin-top: @spacing-sm;
  }

  &__role-label {
    font-size: @font-size-sm;
    color: @color-text-secondary;
    line-height: 32px;
  }

  &__nodes {
    width: 100%;
  }

  &__node-item {
    padding: @spacing-sm;
    border: 1px solid @color-border;
    border-radius: 4px;
    margin-bottom: @spacing-sm;
  }

  &__node-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: @spacing-xs;
  }

  &__node-title {
    font-weight: 600;
    color: @color-text-regular;
  }

  &__options {
    margin-top: @spacing-sm;
    display: flex;
    flex-direction: column;
    gap: @spacing-xs;
  }

  &__option-row {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
  }
}
</style>
