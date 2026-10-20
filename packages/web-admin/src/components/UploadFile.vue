<script setup lang="ts">
// 管理端统一文件上传组件（全局复用，接口文档 14.14）
//
// 值契约（v-model）：
//   multiple=false（默认）→ string，单个文件 URL，无文件为 ''
//   multiple=true         → string[]，文件 URL 数组
//
// 能力：
//   - type 驱动类型（image/audio/wiki/icon）：自动 accept 与大小限制（图10M/音50M/文档20M）
//   - 图片类卡片式上传 + 点击放大预览；非图片类按钮 + 文件列表
//   - 普管自动附带 scenicAreaId（store 兜底），也可由父组件显式传入
//   - 移除默认只更新本地值（不删服务器文件，避免编辑中途取消造成文件丢失）；
//     确需同步删除时开 remote-delete（业务数据已落库场景）
import { computed, ref, watch } from 'vue'
import { ElMessage, type UploadRequestOptions, type UploadUserFile } from 'element-plus'
import {
  uploadFiles,
  deleteUploadedFiles,
  resolveFileUrl,
  UPLOAD_ACCEPT,
  UPLOAD_SIZE_LIMIT_MB,
  type UploadFileType,
} from '@/api/upload'
import { useUserStore } from '@/store'

interface Props {
  /** v-model：单文件为 string，多文件为 string[]；允许 undefined（业务字段可选） */
  modelValue?: string | string[]
  /** 上传业务类型 */
  type?: UploadFileType
  /** 是否多文件 */
  multiple?: boolean
  /** 多文件最大数量 */
  maxCount?: number
  /** 覆盖默认 accept */
  accept?: string
  /** 覆盖默认大小上限（MB） */
  maxSizeMb?: number
  /** 归属景区 ID；不传时普管自动取其管辖景区 */
  scenicAreaId?: number
  /** 禁用（只读表单）：隐藏上传与删除入口 */
  disabled?: boolean
  /** 移除文件时是否同步调用后端删除接口（默认否） */
  remoteDelete?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  type: 'image',
  multiple: false,
  maxCount: 9,
  accept: undefined,
  maxSizeMb: undefined,
  scenicAreaId: undefined,
  disabled: false,
  remoteDelete: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | string[]): void
  (e: 'change', value: string | string[]): void
}>()

const userStore = useUserStore()

const isImageType = computed(() => props.type === 'image' || props.type === 'icon')
const accept = computed(() => props.accept ?? UPLOAD_ACCEPT[props.type])
const sizeLimitMb = computed(() => props.maxSizeMb ?? UPLOAD_SIZE_LIMIT_MB[props.type])

/** 提示中的格式说明 */
const formatText: Record<UploadFileType, string> = {
  image: '图片',
  icon: '图标图片',
  audio: '音频',
  wiki: 'PDF/Word/TXT/MD 文档',
}

/** 实际生效的归属景区：父组件显式传入优先，普管其次取管辖景区 */
const effectiveScenicAreaId = computed(() => props.scenicAreaId ?? userStore.userInfo?.scenicAreaId)

// ===== 当前 URL 集合（内部统一按数组处理） =====
const urls = ref<string[]>(normalizeValue(props.modelValue))

function normalizeValue(value?: string | string[]): string[] {
  if (props.multiple) {
    return Array.isArray(value) ? value.filter(Boolean) : value ? [value] : []
  }
  if (Array.isArray(value)) return value.filter(Boolean).slice(0, 1)
  return value ? [value] : []
}

// 外部值变化（编辑回显/重置表单）时同步
watch(
  () => props.modelValue,
  (value) => {
    urls.value = normalizeValue(value)
  },
)

// el-upload 受控文件列表（由 urls 派生）
const fileList = computed<UploadUserFile[]>(() =>
  urls.value.map((url) => ({
    name: url.split('/').pop() || url,
    url: resolveFileUrl(url),
    status: 'success',
  })),
)

const canUploadMore = computed(() => props.multiple || urls.value.length === 0)
const reachMax = computed(() => props.multiple && urls.value.length >= props.maxCount)

function emitValue(next: string[]) {
  urls.value = next
  if (props.multiple) {
    emit('update:modelValue', next)
    emit('change', next)
  } else {
    emit('update:modelValue', next[0] ?? '')
    emit('change', next[0] ?? '')
  }
}

// ===== 上传前校验 =====
function beforeUpload(file: File): boolean {
  // 大小校验
  if (file.size > sizeLimitMb.value * 1024 * 1024) {
    ElMessage.error(`「${file.name}」大小超过 ${sizeLimitMb.value}MB 限制`)
    return false
  }
  // 数量校验（多选时一次可能选多个文件，beforeUpload 逐文件触发）
  if (props.multiple && urls.value.length >= props.maxCount) {
    ElMessage.warning(`最多上传 ${props.maxCount} 个文件`)
    return false
  }
  return true
}

// ===== 自定义上传 =====
const uploading = ref(false)

async function customUpload(options: UploadRequestOptions) {
  const file = options.file as File
  uploading.value = true
  try {
    const result = await uploadFiles([file], {
      type: props.type,
      scenicAreaId: effectiveScenicAreaId.value,
    })
    if (!result || result.length === 0) {
      throw new Error('上传响应未返回文件 URL')
    }
    // 单文件覆盖、多文件追加（受 maxCount 截断保护）
    const next = props.multiple
      ? [...urls.value, ...result].slice(0, props.maxCount)
      : result.slice(0, 1)
    emitValue(next)
    ElMessage.success('上传成功')
    options.onSuccess?.(result)
  } catch (error) {
    // 业务错误提示已由 request 拦截器统一弹出
    // 从 el-upload onError 签名提取错误参数类型（UploadAjaxError 未从根导出）
    type OnErrorParam = Parameters<NonNullable<UploadRequestOptions['onError']>>[0]
    options.onError?.(error as OnErrorParam)
  } finally {
    uploading.value = false
  }
}

// ===== 移除 =====
async function handleRemove(file: UploadUserFile) {
  // file.url 已被 resolveFileUrl 转成完整地址，需要按原始相对 URL 匹配
  const removedRaw = urls.value.find((u) => resolveFileUrl(u) === file.url) ?? ''
  const next = urls.value.filter((u) => u !== removedRaw)
  emitValue(next)
  if (props.remoteDelete && removedRaw) {
    try {
      await deleteUploadedFiles([removedRaw])
    } catch {
      // 删除失败仅记录，不回滚本地移除（错误已由拦截器弹出）
    }
  }
}

// ===== 图片预览 =====
const previewVisible = ref(false)
const previewIndex = ref(0)
const previewList = computed(() => urls.value.map(resolveFileUrl))

function handlePreview(file: UploadUserFile) {
  const index = previewList.value.findIndex((u) => u === file.url)
  previewIndex.value = index >= 0 ? index : 0
  previewVisible.value = true
}
</script>

<template>
  <div class="upload-file">
    <!-- 图片类：卡片网格 -->
    <el-upload
      v-if="isImageType"
      :file-list="fileList"
      list-type="picture-card"
      :accept="accept"
      :multiple="multiple"
      :disabled="disabled || uploading"
      :show-file-list="true"
      :before-upload="beforeUpload"
      :http-request="customUpload"
      :on-remove="handleRemove"
      :on-preview="handlePreview"
    >
      <template #default>
        <div v-if="!reachMax && canUploadMore && !disabled" class="upload-file__trigger">
          <span class="upload-file__plus">+</span>
          <span class="upload-file__trigger-text">
            {{ uploading ? '上传中…' : '上传图片' }}
          </span>
        </div>
      </template>
    </el-upload>

    <!-- 音频/文档类：按钮 + 文件列表 -->
    <el-upload
      v-else
      :file-list="fileList"
      :accept="accept"
      :multiple="multiple"
      :disabled="disabled || uploading || reachMax"
      :before-upload="beforeUpload"
      :http-request="customUpload"
      :on-remove="handleRemove"
    >
      <el-button v-if="!reachMax && canUploadMore && !disabled" size="small" :loading="uploading">
        {{ uploading ? '上传中…' : `点击上传${type === 'audio' ? '音频' : '文档'}` }}
      </el-button>
    </el-upload>

    <!-- 图片预览 -->
    <el-image-viewer
      v-if="previewVisible"
      :url-list="previewList"
      :initial-index="previewIndex"
      teleported
      @close="previewVisible = false"
    />

    <!-- 提示文案 -->
    <div class="upload-file__tip">
      <slot name="tip">
        支持{{ formatText[type] }}文件，单个不超过 {{ sizeLimitMb }}MB
        <template v-if="multiple">，最多 {{ maxCount }} 个</template>
      </slot>
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.upload-file {
  width: 100%;

  &__trigger {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: @color-text-secondary;
  }

  &__plus {
    font-size: 22px;
    line-height: 1;
  }

  &__trigger-text {
    margin-top: 4px;
    font-size: @font-size-sm;
  }

  &__tip {
    margin-top: @spacing-xs;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    line-height: 1.5;
  }

  // 上传中遮罩
  :deep(.el-upload-list__item.is-success) {
    .el-upload-list__item-status-label {
      display: inline-flex;
    }
  }
}
</style>
