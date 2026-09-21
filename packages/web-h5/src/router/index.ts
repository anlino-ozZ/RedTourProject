import { createRouter, createWebHistory } from 'vue-router'

// 游客移动端H5 路由
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/Home.vue'),
    },
    {
      // 姿态成就扫码领取落地页（W-T-07，触摸屏二维码 /claim?token=xxx）
      path: '/claim',
      name: 'claim',
      component: () => import('@/views/Claim.vue'),
    },
  ],
})
export default router
