<script setup lang="ts">
// 特色产品中心（H-05，静态页面，数据为 mock，后续 H-07 接 GET /products 接口）
// 列表（分类筛选）→ 详情（图片轮播）→ 购买意向登记弹窗（离线场景不支持在线支付）
import { computed, ref } from 'vue'
import { RedButton, RedDialog } from '@red-tour-project/common'
import {
  products,
  type Product,
  type ProductCategory,
} from '@/mock/product'

type CategoryFilter = 'all' | ProductCategory

const tabs: { key: CategoryFilter; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'specialty', label: '特产' },
  { key: 'cultural', label: '文创' },
]

/** 当前分类筛选 */
const activeCategory = ref<CategoryFilter>('all')
/** 当前查看详情的商品（null = 列表视图） */
const selected = ref<Product | null>(null)

const filteredProducts = computed(() =>
  activeCategory.value === 'all'
    ? products
    : products.filter((p) => p.category === activeCategory.value),
)

function countOf(key: CategoryFilter): number {
  return key === 'all'
    ? products.length
    : products.filter((p) => p.category === key).length
}

function categoryLabel(p: Product): string {
  return p.category === 'specialty' ? '特产' : '文创'
}

function priceOf(p: Product): string {
  return `¥${p.price.toFixed(2)}`
}

function stockText(p: Product): string {
  if (p.stock <= 0) return '已售罄'
  if (p.stock <= 10) return `仅剩 ${p.stock} 件`
  return `库存 ${p.stock} 件`
}

function openDetail(p: Product) {
  selected.value = p
  activeImageIndex.value = 0
  // 内容区随窗体滚动，进入详情回到顶部
  window.scrollTo({ top: 0 })
}

function backToList() {
  selected.value = null
  window.scrollTo({ top: 0 })
}

// ===== 详情图片轮播 =====
const activeImageIndex = ref(0)

function carouselImages(): string[] {
  return selected.value ? selected.value.images : []
}

function prevImage() {
  const list = carouselImages()
  activeImageIndex.value =
    (activeImageIndex.value - 1 + list.length) % list.length
}

function nextImage() {
  const list = carouselImages()
  activeImageIndex.value = (activeImageIndex.value + 1) % list.length
}

// ===== 购买意向登记（mock，后续接 POST /products/{id}/order）=====
const orderVisible = ref(false)
const submitting = ref(false)
const orderResult = ref<{ orderId: number; createdAt: string } | null>(null)

const phone = ref('')
const quantity = ref(1)
const remark = ref('')
const formError = ref('')

const remarkMax = 200

function openOrder() {
  if (!selected.value || selected.value.stock <= 0) return
  phone.value = ''
  quantity.value = 1
  remark.value = ''
  formError.value = ''
  orderResult.value = null
  orderVisible.value = true
}

function closeOrder() {
  if (submitting.value) return
  orderVisible.value = false
}

function changeQuantity(delta: number) {
  if (!selected.value) return
  const max = Math.min(99, selected.value.stock)
  quantity.value = Math.min(max, Math.max(1, quantity.value + delta))
}

/** 校验规则对齐接口文档：手机号 11 位、数量 1-99、备注 ≤200 字 */
function validate(): boolean {
  if (!/^1[3-9]\d{9}$/.test(phone.value.trim())) {
    formError.value = '请输入正确的 11 位手机号'
    return false
  }
  if (
    !Number.isInteger(quantity.value) ||
    quantity.value < 1 ||
    quantity.value > 99 ||
    (selected.value && quantity.value > selected.value.stock)
  ) {
    formError.value = '数量需为 1-99 的整数且不超过库存'
    return false
  }
  if (remark.value.length > remarkMax) {
    formError.value = `备注最多 ${remarkMax} 字`
    return false
  }
  formError.value = ''
  return true
}

function submitOrder() {
  if (submitting.value || !validate()) return
  submitting.value = true
  // mock 提交：模拟网络耗时并防止重复提交（H-07 替换为真实接口）
  window.setTimeout(() => {
    const now = new Date()
    const day = [
      now.getFullYear(),
      String(now.getMonth() + 1).padStart(2, '0'),
      String(now.getDate()).padStart(2, '0'),
    ].join('')
    orderResult.value = {
      orderId: Number(`${day}${String(Date.now() % 100000).padStart(5, '0')}`),
      createdAt: now.toLocaleString(),
    }
    submitting.value = false
  }, 600)
}
</script>

<template>
  <div class="products">
    <!-- ===== 商品列表 ===== -->
    <template v-if="!selected">
      <!-- 分类筛选 -->
      <div class="products__tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="products__tab"
          :class="{ 'is-active': activeCategory === tab.key }"
          @click="activeCategory = tab.key"
        >
          {{ tab.label }}
          <span class="products__tab-count">{{ countOf(tab.key) }}</span>
        </button>
      </div>

      <p class="products__hint">红色景区甄选好物 · 支持线下联系购买</p>

      <!-- 商品卡片（两列） -->
      <div class="products__grid">
        <div
          v-for="item in filteredProducts"
          :key="item.id"
          class="products__card"
          @click="openDetail(item)"
        >
          <div class="products__card-imgwrap">
            <img
              class="products__card-img"
              :src="item.image"
              :alt="item.name"
              loading="lazy"
            />
            <span v-if="item.tag" class="products__card-badge">{{
              item.tag
            }}</span>
            <span
              v-if="item.stock <= 0"
              class="products__card-soldout"
            >
              已售罄
            </span>
          </div>
          <div class="products__card-body">
            <span class="products__card-cat">{{ categoryLabel(item) }}</span>
            <h3 class="products__card-name">{{ item.name }}</h3>
            <div class="products__card-foot">
              <span class="products__card-price">{{ priceOf(item) }}</span>
              <span
                class="products__card-stock"
                :class="{
                  'is-low': item.stock > 0 && item.stock <= 10,
                  'is-zero': item.stock <= 0,
                }"
                >{{ stockText(item) }}</span
              >
            </div>
          </div>
        </div>
      </div>

      <div v-if="filteredProducts.length === 0" class="products__empty">
        该分类暂无商品
      </div>
    </template>

    <!-- ===== 商品详情 ===== -->
    <template v-else>
      <div class="products-detail">
        <button
          type="button"
          class="products-detail__back"
          @click="backToList"
        >
          ← 返回列表
        </button>

        <!-- 图片轮播 -->
        <div class="products-detail__carousel">
          <img
            class="products-detail__carousel-img"
            :src="carouselImages()[activeImageIndex]"
            :alt="selected.name"
          />
          <button
            type="button"
            class="products-detail__carousel-arrow products-detail__carousel-arrow--left"
            @click="prevImage"
          >
            ‹
          </button>
          <button
            type="button"
            class="products-detail__carousel-arrow products-detail__carousel-arrow--right"
            @click="nextImage"
          >
            ›
          </button>
          <span class="products-detail__carousel-index">{{
            activeImageIndex + 1
          }} / {{ carouselImages().length }}</span>
          <div class="products-detail__dots">
            <span
              v-for="(img, idx) in carouselImages()"
              :key="img"
              class="products-detail__dot"
              :class="{ 'is-active': idx === activeImageIndex }"
              @click="activeImageIndex = idx"
            ></span>
          </div>
        </div>

        <!-- 商品信息 -->
        <div class="products-detail__info">
          <div class="products-detail__head">
            <span class="products-detail__cat">{{
              categoryLabel(selected)
            }}</span>
            <h2 class="products-detail__name">{{ selected.name }}</h2>
            <div class="products-detail__price-row">
              <span class="products-detail__price">{{
                priceOf(selected)
              }}</span>
              <span
                class="products-detail__stock"
                :class="{
                  'is-low': selected.stock > 0 && selected.stock <= 10,
                  'is-zero': selected.stock <= 0,
                }"
                >{{ stockText(selected) }}</span
              >
            </div>
          </div>

          <div class="products-detail__section">
            <h3 class="products-detail__section-title">商品详情</h3>
            <p class="products-detail__desc">{{ selected.description }}</p>
          </div>

          <div class="products-detail__section">
            <h3 class="products-detail__section-title">购买说明</h3>
            <p class="products-detail__notice">
              景区为离线环境，暂不支持在线支付。提交意向登记后，景区商家将通过您留下的手机号主动联系，确认取货或邮寄事宜。
            </p>
          </div>
        </div>

        <!-- 底部操作栏（位于 TabBar 凸起按钮上方） -->
        <div class="products-detail__footer">
          <RedButton
            type="primary"
            size="large"
            :disabled="selected.stock <= 0"
            @click="openOrder"
          >
            {{ selected.stock <= 0 ? '已售罄' : '意向登记购买' }}
          </RedButton>
        </div>
      </div>
    </template>

    <!-- ===== 意向登记弹窗 ===== -->
    <RedDialog
      :visible="orderVisible"
      :title="orderResult ? '登记成功' : '购买意向登记'"
      width="min(420px, 92vw)"
      @update:visible="(v: boolean) => (orderVisible = v)"
    >
      <!-- 登记表单 -->
      <div v-if="!orderResult" class="order-form">
        <p class="order-form__product">
          {{ selected?.name }}
          <span class="order-form__product-price"
            >{{ selected ? priceOf(selected) : '' }}</span
          >
        </p>

        <label class="order-form__label" for="order-phone">联系手机</label>
        <input
          id="order-phone"
          v-model="phone"
          class="order-form__input"
          type="tel"
          inputmode="numeric"
          maxlength="11"
          placeholder="请输入 11 位手机号"
        />

        <label class="order-form__label">购买数量</label>
        <div class="order-form__qty">
          <button
            type="button"
            class="order-form__qty-btn"
            :disabled="quantity <= 1"
            @click="changeQuantity(-1)"
          >
            −
          </button>
          <span class="order-form__qty-val">{{ quantity }}</span>
          <button
            type="button"
            class="order-form__qty-btn"
            :disabled="!!selected && quantity >= Math.min(99, selected.stock)"
            @click="changeQuantity(1)"
          >
            +
          </button>
          <span class="order-form__qty-tip">最多 99 件</span>
        </div>

        <label class="order-form__label" for="order-remark">
          备注
          <span class="order-form__label-sub">（选填）</span>
        </label>
        <textarea
          id="order-remark"
          v-model="remark"
          class="order-form__textarea"
          :maxlength="remarkMax"
          rows="3"
          placeholder="如：需要快递邮寄 / 期望到店自取时间"
        ></textarea>
        <div class="order-form__counter">{{ remark.length }}/{{ remarkMax }}</div>

        <p v-if="formError" class="order-form__error">{{ formError }}</p>
      </div>

      <!-- 登记成功 -->
      <div v-else class="order-result">
        <div class="order-result__icon">✓</div>
        <p class="order-result__text">
          意向已提交，景区商家将尽快与您电话联系，请保持手机畅通。
        </p>
        <dl class="order-result__meta">
          <div>
            <dt>意向单号</dt>
            <dd>{{ orderResult.orderId }}</dd>
          </div>
          <div>
            <dt>提交时间</dt>
            <dd>{{ orderResult.createdAt }}</dd>
          </div>
        </dl>
      </div>

      <!-- 弹窗底部按钮（表单 / 成功两种状态） -->
      <template #footer>
        <template v-if="!orderResult">
          <RedButton type="ghost" size="medium" @click="closeOrder">
            取消
          </RedButton>
          <RedButton
            type="primary"
            size="medium"
            :loading="submitting"
            @click="submitOrder"
          >
            提交登记
          </RedButton>
        </template>
        <RedButton
          v-else
          type="primary"
          size="medium"
          @click="orderVisible = false"
        >
          完成
        </RedButton>
      </template>
    </RedDialog>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.products {
  // ===== 分类筛选 =====
  &__tabs {
    display: flex;
    gap: @spacing-sm;
    margin-bottom: @spacing-sm;
  }

  &__tab {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 30px;
    padding: 0 14px;
    border: 1px solid @color-border;
    border-radius: 15px;
    background: @color-bg-card;
    color: @color-text-regular;
    font-size: @font-size-base;
    transition: all 0.2s;

    &.is-active {
      border-color: @color-primary;
      background: @color-primary;
      color: #fff;
      font-weight: 600;
    }
  }

  &__tab-count {
    font-size: @font-size-sm;
    opacity: 0.7;
  }

  &__hint {
    margin: 0 0 @spacing-md;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  // ===== 商品卡片网格 =====
  &__grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: @spacing-md;
  }

  &__card {
    display: flex;
    flex-direction: column;
    background: @color-bg-card;
    border-radius: @radius-lg;
    overflow: hidden;
    box-shadow: @shadow-card;
    cursor: pointer;
    transition: transform 0.15s, box-shadow 0.2s;

    &:active {
      transform: scale(0.98);
      box-shadow: @shadow-hover;
    }
  }

  &__card-imgwrap {
    position: relative;
    aspect-ratio: 1 / 1;
    background: @color-bg-page;
  }

  &__card-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  &__card-badge {
    position: absolute;
    top: @spacing-sm;
    left: @spacing-sm;
    padding: 2px 8px;
    border-radius: @radius-sm;
    background: @color-accent;
    color: #fff;
    font-size: @font-size-sm;
    font-weight: 600;
  }

  &__card-soldout {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.45);
    color: #fff;
    font-size: @font-size-lg;
    font-weight: 600;
    letter-spacing: 2px;
  }

  &__card-body {
    padding: @spacing-sm @spacing-md @spacing-md;
  }

  &__card-cat {
    display: inline-block;
    padding: 1px 6px;
    border-radius: @radius-sm;
    background: fade(@color-primary, 8%);
    color: @color-primary;
    font-size: @font-size-sm;
  }

  &__card-name {
    margin: @spacing-xs 0 @spacing-sm;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
    line-height: 1.4;
    // 最多两行
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__card-foot {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: @spacing-xs;
  }

  &__card-price {
    color: @color-primary;
    font-size: @font-size-lg;
    font-weight: 700;
  }

  &__card-stock {
    flex-shrink: 0;
    font-size: @font-size-sm;
    color: @color-text-secondary;

    &.is-low {
      color: @color-warning;
    }
    &.is-zero {
      color: @color-info;
    }
  }

  &__empty {
    padding: @spacing-xl 0;
    text-align: center;
    font-size: @font-size-base;
    color: @color-text-secondary;
  }
}

// ===== 商品详情 =====
.products-detail {
  // 为底部固定操作栏 + TabBar 凸起按钮留空
  padding-bottom: 132px;

  &__back {
    display: inline-flex;
    align-items: center;
    padding: 4px 0;
    margin-bottom: @spacing-sm;
    border: none;
    background: transparent;
    color: @color-primary;
    font-size: @font-size-base;
    cursor: pointer;
  }

  // 轮播
  &__carousel {
    position: relative;
    border-radius: @radius-lg;
    overflow: hidden;
    background: @color-bg-page;
    box-shadow: @shadow-card;
  }

  &__carousel-img {
    width: 100%;
    aspect-ratio: 4 / 3;
    object-fit: cover;
    display: block;
  }

  &__carousel-arrow {
    position: absolute;
    top: 50%;
    transform: translateY(-50%);
    width: 32px;
    height: 32px;
    border: none;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.32);
    color: #fff;
    font-size: 20px;
    line-height: 1;
    cursor: pointer;

    &--left {
      left: @spacing-sm;
    }
    &--right {
      right: @spacing-sm;
    }
  }

  &__carousel-index {
    position: absolute;
    right: @spacing-sm;
    bottom: 30px;
    padding: 2px 8px;
    border-radius: 10px;
    background: rgba(0, 0, 0, 0.4);
    color: #fff;
    font-size: @font-size-sm;
  }

  &__dots {
    position: absolute;
    left: 0;
    right: 0;
    bottom: @spacing-sm;
    display: flex;
    justify-content: center;
    gap: 6px;
  }

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.55);
    cursor: pointer;
    transition: all 0.2s;

    &.is-active {
      width: 16px;
      border-radius: 3px;
      background: #fff;
    }
  }

  // 信息区
  &__info {
    margin-top: @spacing-md;
  }

  &__head {
    padding: @spacing-md;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: @shadow-card;
  }

  &__cat {
    display: inline-block;
    padding: 2px 8px;
    border-radius: @radius-sm;
    background: fade(@color-primary, 8%);
    color: @color-primary;
    font-size: @font-size-sm;
  }

  &__name {
    margin: @spacing-sm 0;
    font-size: 18px;
    font-weight: 700;
    color: @color-text-primary;
    line-height: 1.4;
  }

  &__price-row {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
  }

  &__price {
    color: @color-primary;
    font-size: 24px;
    font-weight: 700;
  }

  &__stock {
    font-size: @font-size-sm;
    color: @color-text-secondary;

    &.is-low {
      color: @color-warning;
      font-weight: 600;
    }
    &.is-zero {
      color: @color-info;
    }
  }

  &__section {
    margin-top: @spacing-md;
    padding: @spacing-md;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: @shadow-card;
  }

  &__section-title {
    margin: 0 0 @spacing-sm;
    padding-left: @spacing-sm;
    border-left: 3px solid @color-primary;
    font-size: @font-size-base;
    font-weight: 700;
    color: @color-text-primary;
  }

  &__desc {
    margin: 0;
    font-size: @font-size-base;
    line-height: 1.7;
    color: @color-text-regular;
  }

  &__notice {
    margin: 0;
    font-size: @font-size-sm;
    line-height: 1.7;
    color: @color-text-secondary;
  }

  // 底部固定操作栏
  &__footer {
    position: fixed;
    left: 0;
    right: 0;
    bottom: calc(56px + 18px); // TabBar 高度 + 问答凸起按钮高度
    padding: @spacing-sm @spacing-md;
    background: fade(@color-bg-card, 96%);
    box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.06);
    z-index: 90;

    :deep(.rt-red-btn) {
      width: 100%;
      white-space: nowrap;
    }
  }
}

// ===== 意向登记表单 =====
.order-form {
  &__product {
    margin: 0 0 @spacing-md;
    padding: @spacing-sm @spacing-md;
    border-radius: @radius-base;
    background: @color-primary-light;
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  &__product-price {
    color: @color-primary;
  }

  &__label {
    display: block;
    margin-bottom: @spacing-xs;
    font-size: @font-size-sm;
    font-weight: 600;
    color: @color-text-primary;
  }

  &__label-sub {
    font-weight: 400;
    color: @color-text-secondary;
  }

  &__input,
  &__textarea {
    width: 100%;
    padding: 10px 12px;
    border: 1px solid @color-border;
    border-radius: @radius-base;
    font-size: @font-size-base;
    color: @color-text-primary;
    background: @color-bg-card;
    box-sizing: border-box;
    outline: none;
    transition: border-color 0.2s;

    &:focus {
      border-color: @color-primary;
    }
  }

  &__input {
    margin-bottom: @spacing-md;
  }

  &__textarea {
    resize: none;
    line-height: 1.5;
  }

  &__qty {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    margin-bottom: @spacing-md;
  }

  &__qty-btn {
    width: 32px;
    height: 32px;
    border: 1px solid @color-border;
    border-radius: @radius-base;
    background: @color-bg-card;
    color: @color-text-regular;
    font-size: 18px;
    line-height: 1;
    cursor: pointer;

    &:active:not(:disabled) {
      border-color: @color-primary;
      color: @color-primary;
    }
    &:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
  }

  &__qty-val {
    min-width: 40px;
    text-align: center;
    font-size: @font-size-lg;
    font-weight: 600;
  }

  &__qty-tip {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__counter {
    margin-top: 2px;
    text-align: right;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__error {
    margin: @spacing-sm 0 0;
    font-size: @font-size-sm;
    color: @color-danger;
  }
}

// 弹窗底部按钮间距（RedDialog footer 内）
:deep(.rt-dialog__footer) {
  display: flex;
  justify-content: flex-end;
  gap: @spacing-sm;
}

// ===== 登记成功 =====
.order-result {
  text-align: center;

  &__icon {
    width: 56px;
    height: 56px;
    margin: 0 auto @spacing-md;
    line-height: 56px;
    border-radius: 50%;
    background: fade(@color-success, 12%);
    color: @color-success;
    font-size: 30px;
    font-weight: 700;
  }

  &__text {
    margin: 0 0 @spacing-md;
    font-size: @font-size-base;
    line-height: 1.6;
    color: @color-text-regular;
  }

  &__meta {
    margin: 0 0 @spacing-sm;
    text-align: left;
    background: @color-bg-page;
    border-radius: @radius-base;
    padding: @spacing-sm @spacing-md;

    div {
      display: flex;
      justify-content: space-between;
      padding: 4px 0;
      font-size: @font-size-sm;
    }
    dt {
      color: @color-text-secondary;
    }
    dd {
      margin: 0;
      color: @color-text-primary;
      font-weight: 600;
    }
  }
}
</style>
