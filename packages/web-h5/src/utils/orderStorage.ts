/** 我的订单本地记录：后端暂无「当前用户订单列表」接口，先持久化前端提交成功的订单 */
export type StoredOrderStatus = 'pending' | 'contacted' | 'closed'

export interface StoredOrder {
  id: string
  title: string
  spec: string
  status: StoredOrderStatus
  statusText: string
  date: string
  price: number
  qty: number
  cover?: string
}

export interface StoredOrderInput {
  orderId: number
  title: string
  price: number
  quantity: number
  image?: string
  createdAt?: string
}

const STORAGE_KEY = 'rt_orders'
const MAX_ORDERS = 50

const STATUS_TEXT: Record<StoredOrderStatus, string> = {
  pending: '待处理',
  contacted: '已联系',
  closed: '已关闭',
}

function normalizeStatus(value: unknown): StoredOrderStatus {
  return value === 'contacted' || value === 'closed' ? value : 'pending'
}

function isStoredOrder(value: unknown): value is StoredOrder {
  if (!value || typeof value !== 'object') return false
  const order = value as Partial<StoredOrder>
  return typeof order.id === 'string' && typeof order.title === 'string'
}

/** 读取本机订单，按提交时间倒序 */
export function getStoredOrders(): StoredOrder[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
    if (!Array.isArray(parsed)) return []
    return parsed.filter(isStoredOrder).map((order) => {
      const status = normalizeStatus(order.status)
      return { ...order, status, statusText: STATUS_TEXT[status] }
    })
  } catch {
    return []
  }
}

/** 下单成功后写入本机；同一订单号覆盖，最多保留 50 条 */
export function saveStoredOrder(input: StoredOrderInput): StoredOrder {
  const id = String(input.orderId)
  const date = (input.createdAt || new Date().toLocaleString()).slice(0, 16)
  const order: StoredOrder = {
    id,
    title: input.title,
    spec: `购买数量 ×${input.quantity}`,
    status: 'pending',
    statusText: STATUS_TEXT.pending,
    date,
    price: input.price,
    qty: input.quantity,
    cover: input.image,
  }
  const list = [order, ...getStoredOrders().filter((item) => item.id !== id)].slice(0, MAX_ORDERS)
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
  return order
}