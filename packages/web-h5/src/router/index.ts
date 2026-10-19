import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

// ????? H5 ??
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '??', showTabBar: true },
  },
  {
    path: '/auth',
    name: 'auth',
    component: () => import('@/views/Auth.vue'),
    // ??/???????????? TabBar??????????
    meta: { title: '', showTabBar: false },
  },
  {
    path: '/guide',
    name: 'guide',
    component: () => import('@/views/Guide.vue'),
    meta: { title: '????', showTabBar: true },
  },
  {
    path: '/guide/navigate',
    name: 'guide-navigate',
    component: () => import('@/views/GuideNav.vue'),
    meta: { title: '?????', showTabBar: false },
  },
  {
    path: '/guide/recommend',
    name: 'guide-recommend',
    component: () => import('@/views/GuideRecommend.vue'),
    meta: { title: 'AI ?????', showTabBar: true },
  },
  {
    path: '/qa',
    name: 'qa',
    component: () => import('@/views/Qa.vue'),
    // ???????????????? Layout ?????
    meta: { title: '', showTabBar: true },
  },
  {
    path: '/qa/history',
    name: 'qa-history',
    component: () => import('@/views/QaHistory.vue'),
    meta: { title: '', showTabBar: false },
  },
  {
    path: '/products',
    name: 'products',
    component: () => import('@/views/Products.vue'),
    meta: { title: '????', showTabBar: true },
  },
  {
    path: '/interactive',
    name: 'interactive',
    component: () => import('@/views/Interactive.vue'),
    // ??????? Hero ????????? Layout ?????
    meta: { title: '', showTabBar: true },
  },
  {
    path: '/interactive/scripts',
    name: 'interactive-scripts',
    component: () => import('@/views/ScriptList.vue'),
    meta: { title: '????', showTabBar: true },
  },
  {
    path: '/interactive/script',
    name: 'interactive-script',
    component: () => import('@/views/ScriptPlay.vue'),
    // ????????????????????? TabBar
    meta: { title: '', showTabBar: false },
  },
  {
    path: '/interactive/pose',
    name: 'interactive-pose',
    component: () => import('@/views/PoseInteract.vue'),
    meta: { title: '????', showTabBar: true },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/Profile.vue'),
    // ??????????????????????? Layout ???
    meta: { title: '', showTabBar: true },
  },
  {
    // ????????????W-T-07??????? /claim?token=xxx?
    path: '/claim',
    name: 'claim',
    component: () => import('@/views/Claim.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})
export default router
