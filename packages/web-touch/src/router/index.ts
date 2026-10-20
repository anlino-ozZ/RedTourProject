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
    {
      path: '/voice-ask',
      name: 'voice-ask',
      component: () => import('@/views/VoiceAsk.vue'),
    },
    {
      path: '/pose-interaction',
      name: 'pose-interaction',
      component: () => import('@/views/PoseInteraction.vue'),
    },
    {
      path: '/quick-info',
      name: 'quick-info',
      component: () => import('@/views/QuickInfo.vue'),
    },
  ],
})

export default router
