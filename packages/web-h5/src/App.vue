<script setup lang="ts">
// 游客移动端H5 根组件：统一布局（Layout + TabBar）
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import Layout from '@/layouts/Layout.vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const userStore = useUserStore()

// 启动时用本地 token 恢复登录态：token 失效会被清空，网络异常保留登录态
onMounted(() => {
  void userStore.refreshMe()
})
// 从路由 meta 读取标题与是否显示 TabBar
const title = computed(() => (route.meta.title as string) ?? '')
const showTabBar = computed(() => route.meta.showTabBar !== false)
</script>

<template>
  <Layout :title="title" :show-tab-bar="showTabBar">
    <router-view />
  </Layout>
</template>
