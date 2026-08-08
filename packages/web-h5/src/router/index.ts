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
  ],
})

export default router
