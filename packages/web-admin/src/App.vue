<script setup lang="ts">
// PC管理后台 根组件：顶部 RedNav + 左侧 el-menu 占位 + 主区 router-view
import { RedNav } from '@red-tour/common'
import type { NavItem } from '@red-tour/common'

const navItems: NavItem[] = [
  { title: '首页', path: '/' },
  { title: '景点管理', path: '/scenic' },
  { title: '路线管理', path: '/route' },
  { title: '用户管理', path: '/user' },
  { title: '数据统计', path: '/stats' },
]
</script>

<template>
  <div class="app-container">
    <RedNav :items="navItems" title="红色文旅-管理后台" />
    <el-container class="app-body">
      <el-aside width="220px" class="app-aside">
        <el-menu :default-active="'/'" router>
          <el-menu-item index="/">
            <span>首页概览</span>
          </el-menu-item>
          <el-menu-item index="/scenic">
            <span>景点管理</span>
          </el-menu-item>
          <el-menu-item index="/route">
            <span>路线管理</span>
          </el-menu-item>
          <el-menu-item index="/user">
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/stats">
            <span>数据统计</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<style lang="less" scoped>
.app-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #f5f5f7;
}
.app-body {
  flex: 1;
}
.app-aside {
  background: #fff;
  border-right: 1px solid #e0e0e0;
}
.app-main {
  padding: 24px;
}
</style>
