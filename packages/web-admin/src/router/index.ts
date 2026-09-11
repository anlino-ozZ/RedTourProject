import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store'
import type { UserRole } from '@red-tour-project/common'

// 路由元信息扩展
declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean // 是否需要登录（默认 true）
    roles?: UserRole[] // 允许访问的角色（缺省 = 已登录即可）
    title?: string
  }
}

const LOGIN_PATH = '/login'
const HOME_PATH = '/'

// PC管理后台路由
const routes: RouteRecordRaw[] = [
  {
    path: LOGIN_PATH,
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false, title: '登录' },
  },
  {
    // 后台框架（RedNav + 侧边菜单），业务页面作为 children 追加
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { title: '管理后台' },
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页概览' },
      },
      {
        path: 'user',
        name: 'user',
        component: () => import('@/views/User.vue'),
        meta: { title: '用户管理', roles: ['super_admin'] },
      },
      {
        path: 'scenic',
        name: 'scenic',
        component: () => import('@/views/Scenic.vue'),
        meta: { title: '景区管理' },
      },
      {
        path: 'route',
        name: 'route',
        component: () => import('@/views/Route.vue'),
        meta: { title: '路线管理' },
      },
      {
        path: 'checkpoint',
        name: 'checkpoint',
        component: () => import('@/views/Checkpoint.vue'),
        meta: { title: '打卡点管理' },
      },
      {
        path: 'product',
        name: 'product',
        component: () => import('@/views/Product.vue'),
        meta: { title: '特产文创管理' },
      },
      {
        path: 'order',
        name: 'product-order',
        component: () => import('@/views/Order.vue'),
        meta: { title: '意向订单管理' },
      },
      // 统计等模块路由由后续 W-A-xx 任务在此追加
    ],
  },
  // 未匹配路由统一回首页（后续可替换为 404 页面）
  { path: '/:pathMatch(.*)*', name: 'not-found', redirect: HOME_PATH },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局前置守卫：登录态校验 + 角色权限 + redirect 回跳闭环
router.beforeEach(async (to) => {
  const userStore = useUserStore()

  // 已登录用户访问登录页 → 直接进后台
  if (to.path === LOGIN_PATH) {
    if (userStore.isLoggedIn) return HOME_PATH
    return true
  }

  // 公开页（requiresAuth === false）直接放行
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth !== false)
  if (!requiresAuth) return true

  // 未登录 → 跳登录页并记录目标地址，登录成功后回跳
  if (!userStore.isLoggedIn) {
    return { path: LOGIN_PATH, query: { redirect: to.fullPath } }
  }

  // 有 token 但本地用户信息缺失（如存储被部分清理）→ 拉 /auth/me 恢复
  if (!userStore.userInfo) {
    try {
      await userStore.fetchProfile()
    } catch {
      userStore.clearAuth()
      return { path: LOGIN_PATH, query: { redirect: to.fullPath } }
    }
  }

  // 角色权限校验
  const allowedRoles = to.meta.roles
  if (allowedRoles && allowedRoles.length > 0) {
    const role = userStore.role as UserRole
    if (role && !allowedRoles.includes(role)) {
      ElMessage.error('无权限访问该页面')
      return HOME_PATH
    }
  }

  return true
})

export default router
