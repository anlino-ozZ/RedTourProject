import request from './request'

// 占位示例接口，后续按模块拆分（如 scenic / route / user 等）
export function getHealth() {
  return request.get('/health')
}
