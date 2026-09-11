<script setup lang="ts">
// PC管理后台 特产/文创管理页（PRD 5.5，表 product）
// 超管可增删改全部景区产品，普管仅查看管辖景区产品（只读）
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { RedButton } from '@red-tour-project/common'
import {
  getAdminProducts,
  createAdminProduct,
  updateAdminProduct,
  deleteAdminProduct,
  type AdminProductItem,
  type AdminProductPayload,
  type ProductCategory,
} from '@/api/admin'
import { getScenicAreas } from '@/api'
import { resolveFileUrl } from '@/api/upload'
import UploadFile from '@/components/UploadFile.vue'
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
const tableData = ref<AdminProductItem[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  pageSize: 10,
  keyword: '',
  category: '' as '' | ProductCategory,
  scenicAreaId: '' as number | '',
})

async function loadList() {
  loading.value = true
  try {
    const res = await getAdminProducts({
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
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增产品' : '编辑产品'))
const formRef = ref<FormInstance>()
const submitting = ref(false)

// 关联字段初始必须为 undefined，数字 0 会绕过 required 校验
const form = reactive<{
  id?: number
  scenicAreaId?: number
  name: string
  category: ProductCategory
  price?: number
  stock: number
  image: string
  description: string
  status: number
}>({
  id: undefined,
  scenicAreaId: boundScenicAreaId.value,
  name: '',
  category: 'specialty',
  price: undefined,
  stock: 0,
  image: '',
  description: '',
  status: 1,
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入产品名称', trigger: 'blur' },
    { min: 1, max: 100, message: '名称长度为 1-100 字符', trigger: 'blur' },
  ],
  scenicAreaId: [{ required: true, message: '请选择所属景区', trigger: 'change' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [
    {
      validator: (_rule, value: number | undefined, callback) => {
        if (value === undefined || value === null || Number.isNaN(value)) {
          return callback(new Error('请输入价格'))
        }
        if (value < 0 || value > 99999999.99) {
          return callback(new Error('价格范围 0 ~ 99999999.99'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  stock: [
    {
      validator: (_rule, value: number, callback) => {
        if (value === undefined || value === null || Number.isNaN(value)) {
          return callback(new Error('请输入库存数量'))
        }
        if (!Number.isInteger(value) || value < 0 || value > 99999999) {
          return callback(new Error('库存为 0 ~ 99999999 的整数'))
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
  form.name = ''
  form.category = 'specialty'
  form.price = undefined
  form.stock = 0
  form.image = ''
  form.description = ''
  form.status = 1
}

function openCreate() {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEdit(row: AdminProductItem) {
  resetForm()
  dialogMode.value = 'edit'
  form.id = row.id
  form.scenicAreaId = row.scenicAreaId
  form.name = row.name
  form.category = row.category
  form.price = row.price
  form.stock = row.stock
  form.image = row.image || ''
  form.description = row.description || ''
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload: AdminProductPayload = {
        name: form.name,
        scenicAreaId: form.scenicAreaId!,
        price: form.price!,
        stock: form.stock,
        category: form.category,
        image: form.image || undefined,
        description: form.description || undefined,
        status: form.status,
      }
      if (dialogMode.value === 'create') {
        await createAdminProduct(payload)
        ElMessage.success('产品创建成功')
      } else if (form.id) {
        await updateAdminProduct(form.id, payload)
        ElMessage.success('产品更新成功')
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
async function handleDelete(row: AdminProductItem) {
  try {
    await ElMessageBox.confirm(`确定要删除产品 "${row.name}" 吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteAdminProduct(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 错误提示已由拦截器统一弹出
  }
}

// ===== 工具函数 =====
const categoryMap: Record<ProductCategory, { text: string; type: 'warning' | 'success' }> = {
  specialty: { text: '特产', type: 'warning' },
  cultural: { text: '文创', type: 'success' },
}

function statusText(status: number) {
  return status === 1 ? '上架' : '下架'
}

function statusTagType(status: number) {
  return status === 1 ? 'success' : 'info'
}

function stockTagType(stock: number) {
  if (stock === 0) return 'danger'
  if (stock < 10) return 'warning'
  return 'info'
}

// ===== 初始化 =====
onMounted(() => {
  loadScenicOptions()
  // 普管进入页面即锁定管辖景区筛选
  if (boundScenicAreaId.value) {
    query.scenicAreaId = boundScenicAreaId.value
  }
  loadList()
})
</script>

<template>
  <div class="product-page">
    <!-- 筛选栏 -->
    <div class="product-page__toolbar">
      <div class="product-page__search">
        <el-input
          v-model="query.keyword"
          placeholder="产品名称搜索"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.category" placeholder="分类筛选" clearable style="width: 130px">
          <el-option label="特产" value="specialty" />
          <el-option label="文创" value="cultural" />
        </el-select>
        <el-select
          v-if="!isReadOnly"
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
      <RedButton v-if="!isReadOnly" type="primary" size="small" @click="openCreate">
        + 新增产品
      </RedButton>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
      <el-table-column label="图片" width="90">
        <template #default="{ row }">
          <el-image
            v-if="row.image"
            :src="resolveFileUrl(row.image)"
            fit="cover"
            class="product-page__thumb"
            :preview-src-list="[resolveFileUrl(row.image)]"
            preview-teleported
          />
          <span v-else class="product-page__no-img">无图</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="产品名称" min-width="140" />
      <el-table-column label="所属景区" width="140">
        <template #default="{ row }">
          {{ scenicNameMap.get(row.scenicAreaId) ?? `#${row.scenicAreaId}` }}
        </template>
      </el-table-column>
      <el-table-column label="分类" width="80">
        <template #default="{ row }">
          <el-tag :type="categoryMap[row.category]?.type" effect="plain">
            {{ categoryMap[row.category]?.text || row.category }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="价格" width="100">
        <template #default="{ row }">
          <span class="product-page__price">¥{{ Number(row.price).toFixed(2) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="库存" width="90">
        <template #default="{ row }">
          <el-tag :type="stockTagType(row.stock)" effect="plain">{{ row.stock }}</el-tag>
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
    <div class="product-page__pagination">
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
        <el-form-item label="产品名称" prop="name">
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
            <el-option v-for="s in scenicOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-radio-group v-model="form.category">
            <el-radio-button value="specialty">特产</el-radio-button>
            <el-radio-button value="cultural">文创</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="价格（元）" prop="price">
          <el-input-number
            v-model="form.price"
            :precision="2"
            :step="10"
            :min="0"
            :max="99999999.99"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="库存数量" prop="stock">
          <el-input-number
            v-model="form.stock"
            :step="1"
            :min="0"
            :max="99999999"
            :precision="0"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="产品图片">
          <UploadFile v-model="form.image" type="image" :scenic-area-id="form.scenicAreaId" />
        </el-form-item>
        <el-form-item label="产品描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="选填" />
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

.product-page {
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

  &__price {
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
