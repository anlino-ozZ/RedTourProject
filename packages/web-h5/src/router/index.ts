import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

// 游客移动端H5 路由
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '首页', showTabBar: true },
  },
  {
    path: '/auth',
    name: 'auth',
    component: () => import('@/views/Auth.vue'),
    // 登录/注册页不显示标题栏与底部 TabBar，采用沉浸式全屏布局
    meta: { title: '', showTabBar: false },
  },
  {
    path: '/guide',
    name: 'guide',
    component: () => import('@/views/Guide.vue'),
    meta: { title: '导览', showTabBar: true },
  },
  {
    path: '/qa',
    name: 'qa',
    component: () => import('@/views/Qa.vue'),
    meta: { title: '问答', showTabBar: true },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/Profile.vue'),
    meta: { title: '我的', showTabBar: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
