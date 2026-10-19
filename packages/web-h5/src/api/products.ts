/**
 * 文创/特产商品接口（对齐 docs/05 第七节与后端 ProductPublicController / ProductOrderController）
 * - GET  /products             商品列表（分页，公开，仅上架）
 * - GET  /products/{id}        商品详情（公开）
 * - POST /products/{id}/order  购买意向登记（需登录）
 *   请求体：{ contactName, contactPhone, quantity, remark }
 *   响应：ProductOrderResult（id / quantity / createdAt / status ...）
 *
 * 真实接口优先；后端未就绪 / 网络异常时降级 mock（下单降级为本地生成单号）。
 * 注意：未登录（401）不降级，交给页面提示登录，避免"假下单成功"。
 */
import request, { ApiError } from './request'
import { DEFAULT_SCENIC_AREA_ID } from './scenic'
import { products as mockProducts, type Product, type ProductCategory } from '@/mock/product'

export type { Product, ProductCategory }

/** 数据来源：api=后端真实接口；fallback=后端未就绪时的本地兜底数据 */
export type ProductDataSource = 'api' | 'fallback'

export interface ProductListResult {
  list: Product[]
  source: ProductDataSource
}

export interface ProductDetailResult {
  product?: Product
  source: ProductDataSource
}

export interface ProductOrderSubmitResult {
  orderId: number
  createdAt: string
  source: ProductDataSource
}

const PAGE_SIZE = 50

/** 后端商品返回结构 */
interface ProductDto {
  id: number
  name: string
  price?: number | string | null
  description?: string | null
  image?: string | null
  images?: string | string[] | null
  stock?: number | null
  category?: string | null
  status?: number | null
}

/** 后端订单返回结构（只取页面需要的字段） */
interface ProductOrderDto {
  id: number
  quantity?: number
  createdAt?: string | null
}

/** 兼容后端返回数组或分页对象（PageResult.list） */
function toDtoList(payload: unknown): ProductDto[] {
  if (Array.isArray(payload)) return payload as ProductDto[]
  const page = payload as { list?: ProductDto[] } | null
  return page?.list ?? []
}

/** images 是 JSON 字符串（或已经是数组） */
function parseImages(images: ProductDto['images']): string[] {
  if (!images) return []
  if (Array.isArray(images)) return images
  try {
    const parsed: unknown = JSON.parse(images)
    return Array.isArray(parsed)
      ? parsed.filter((item): item is string => typeof item === 'string')
      : []
  } catch {
    return [images]
  }
}

function toCategory(value?: string | null): ProductCategory {
  return value === 'cultural' ? 'cultural' : 'specialty'
}

function mapProduct(dto: ProductDto): Product {
  const images = parseImages(dto.images)
  const image = dto.image || images[0] || ''
  return {
    id: dto.id,
    name: dto.name ?? '',
    price: Number(dto.price ?? 0),
    description: dto.description ?? '',
    image,
    images: images.length ? images : image ? [image] : [],
    stock: dto.stock ?? 0,
    category: toCategory(dto.category),
  }
}

/** 后端时间（2026-10-19T13:45:00）→ 页面展示用本地格式 */
function formatCreatedAt(value?: string | null): string {
  if (!value) return new Date().toLocaleString()
  const text = String(value).replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}

/** 401（未登录/登录失效）不降级，其它异常降级兜底 */
function isUnauthorized(error: unknown): boolean {
  if (error instanceof ApiError) return error.code === 401
  const status = (error as { response?: { status?: number } } | null)?.response?.status
  return status === 401
}

/** 获取商品列表（公开接口；后端未就绪时静默降级 mock） */
export async function getProducts(): Promise<ProductListResult> {
  try {
    const payload: unknown = await request.get('/products', {
      params: { scenicAreaId: DEFAULT_SCENIC_AREA_ID, page: 1, pageSize: PAGE_SIZE },
      silent: true,
    })
    const dtos = toDtoList(payload)
    return { list: dtos.map(mapProduct), source: 'api' }
  } catch {
    // 后端接口未上线或网络异常：走下方兜底数据
  }
  return { list: mockProducts, source: 'fallback' }
}

/** 获取商品详情（公开接口；后端未就绪时静默降级 mock） */
export async function getProductById(id: number): Promise<ProductDetailResult> {
  try {
    const payload: unknown = await request.get(`/products/${id}`, { silent: true })
    const dto = payload as ProductDto | null
    if (dto?.id) return { product: mapProduct(dto), source: 'api' }
  } catch {
    // 后端接口未上线或网络异常：走下方兜底数据
  }
  return { product: mockProducts.find((item) => item.id === id), source: 'fallback' }
}

/** 购买意向登记（需登录；后端未就绪时本地生成单号兜底） */
export async function submitOrder(params: {
  productId: number
  phone: string
  quantity: number
  remark?: string
  /** 联系人，缺省为「游客」 */
  contactName?: string
}): Promise<ProductOrderSubmitResult> {
  try {
    const payload: unknown = await request.post(
      `/products/${params.productId}/order`,
      {
        contactName: params.contactName ?? '游客',
        contactPhone: params.phone,
        quantity: params.quantity,
        remark: params.remark,
      },
      { silent: true },
    )
    const dto = payload as ProductOrderDto | null
    if (dto?.id) {
      return { orderId: dto.id, createdAt: formatCreatedAt(dto.createdAt), source: 'api' }
    }
  } catch (error) {
    // 未登录 / 登录失效：交给页面提示，不用 mock 冒充成功
    if (isUnauthorized(error)) throw error
    // 其它异常（后端未上线/网络异常）走下方本地兜底
  }
  // mock：模拟网络延迟生成订单号
  await new Promise((resolve) => setTimeout(resolve, 600))
  const now = new Date()
  const day = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('')
  return {
    orderId: Number(`${day}${String(Date.now() % 100000).padStart(5, '0')}`),
    createdAt: now.toLocaleString(),
    source: 'fallback',
  }
}
