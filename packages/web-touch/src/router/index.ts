import { createRouter, createWebHistory } from 'vue-router'

// 触摸屏大屏路由
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/Home.vue'),
    },
  ],
})

export default router
