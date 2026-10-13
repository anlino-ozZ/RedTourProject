/**
 * 文创商品接口桩（后端 S-07 Product CRUD 尚未完成）
 * - GET /products             商品列表（分类筛选 specialty/cultural、上下架+库存）
 * - GET /products/{id}        商品详情
 * - POST /products/{id}/order 购买意向登记（手机号+数量+备注）
 * 当前 USE_MOCK=true 返回 mock 数据；后端 ready 后改为 false 即可零改动切换
 */
import request from './request'
import { products as mockProducts, type Product, type ProductCategory } from '@/mock/product'

export type { Product, ProductCategory }

const USE_MOCK = true

/** 获取商品列表 */
export async function getProducts(): Promise<Product[]> {
  if (!USE_MOCK) {
    return request.get('/products', { params: { status: 'online' } })
  }
  return mockProducts
}

/** 获取商品详情 */
export async function getProductById(id: number): Promise<Product | undefined> {
  if (!USE_MOCK) {
    return request.get(`/products/${id}`)
  }
  return mockProducts.find((p) => p.id === id)
}

/** 购买意向登记（mock：本地生成订单号） */
export async function submitOrder(params: {
  productId: number
  phone: string
  quantity: number
  remark?: string
}): Promise<{ orderId: number; createdAt: string }> {
  if (!USE_MOCK) {
    return request.post(`/products/${params.productId}/order`, params)
  }
  // mock：模拟网络延迟生成订单号
  await new Promise((r) => setTimeout(r, 600))
  const now = new Date()
  const day = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('')
  return {
    orderId: Number(`${day}${String(Date.now() % 100000).padStart(5, '0')}`),
    createdAt: now.toLocaleString(),
  }
}
