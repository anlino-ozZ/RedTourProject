import { createRouter, createWebHistory } from 'vue-router'

// PC管理后台路由
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
