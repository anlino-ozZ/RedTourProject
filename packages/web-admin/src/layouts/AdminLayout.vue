<script setup lang="ts">
// PC管理后台 框架布局：顶部 RedNav + 左侧 el-menu（按角色显隐）+ 主区 router-view
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RedNav, RedButton } from '@red-tour-project/common'
import type { NavItem, UserRole } from '@red-tour-project/common'
import { useUserStore } from '@/store'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 菜单定义：roles 缺省 = 所有管理员可见；用户管理仅超管可见（PRD 5.1）
interface AdminMenuItem extends NavItem {
  roles?: UserRole[]
}
const allMenuItems: AdminMenuItem[] = [
  { title: '首页概览', path: '/' },
  { title: '景点管理', path: '/scenic' },
  { title: '路线管理', path: '/route' },
  { title: '用户管理', path: '/user', roles: ['super_admin'] },
  { title: '数据统计', path: '/stats' },
]

const visibleMenuItems = computed<NavItem[]>(() =>
  allMenuItems
    .filter((item) => !item.roles || item.roles.includes(userStore.role as UserRole))
    .map(({ title, path }) => ({ title, path })),
)

const activeMenu = computed(() => route.path)
const roleLabel = computed(() => (userStore.role === 'super_admin' ? '超级管理员' : '普通管理员'))

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出当前管理员账号吗？', '退出确认', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return // 用户取消
  }
  await userStore.logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <div class="admin-layout">
    <RedNav :items="visibleMenuItems" title="红色文旅-管理后台" />
    <el-container class="admin-layout__body">
      <el-aside width="220px" class="admin-layout__aside">
        <el-menu :default-active="activeMenu" router class="admin-layout__menu">
          <el-menu-item v-for="item in visibleMenuItems" :key="item.path" :index="item.path">
            <span>{{ item.title }}</span>
          </el-menu-item>
        </el-menu>
        <div class="admin-layout__user">
          <div class="admin-layout__user-info">
            <div class="admin-layout__user-name">{{ userStore.displayName }}</div>
            <el-tag size="small" type="danger" effect="plain">{{ roleLabel }}</el-tag>
          </div>
          <RedButton type="ghost" size="small" class="admin-layout__logout" @click="handleLogout">
            退出登录
          </RedButton>
        </div>
      </el-aside>
      <el-main class="admin-layout__main">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.admin-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: @color-bg-page;

  &__body {
    flex: 1;
  }

  &__aside {
    display: flex;
    flex-direction: column;
    background: @color-bg-card;
    border-right: 1px solid @color-border;
  }

  &__menu {
    flex: 1;
    border-right: none;
  }

  &__user {
    padding: @spacing-md;
    border-top: 1px solid @color-border;
  }

  &__user-info {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: @spacing-sm;
    margin-bottom: @spacing-sm;
  }

  &__user-name {
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__logout {
    width: 100%;
  }

  &__main {
    padding: @spacing-lg;
  }
}
</style>
