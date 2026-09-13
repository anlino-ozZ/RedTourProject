<script setup lang="ts">
// PC管理后台 框架布局：顶部 RedNav + 左侧可折叠 el-menu + 头部面包屑/角色名/退出
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RedNav, RedButton } from '@red-tour-project/common'
import type { NavItem, UserRole } from '@red-tour-project/common'
import { useUserStore } from '@/store'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// ===== 侧边栏折叠 =====
const isCollapsed = ref(false)
function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
}

// ===== 菜单定义：roles 缺省 = 所有管理员可见；用户管理仅超管可见（PRD 5.1） =====
interface AdminMenuItem extends NavItem {
  roles?: UserRole[]
}
const allMenuItems: AdminMenuItem[] = [
  { title: '首页概览', path: '/' },
  { title: '景点管理', path: '/scenic' },
  { title: '路线管理', path: '/route' },
  { title: '打卡点管理', path: '/checkpoint' },
  { title: '特产文创管理', path: '/product' },
  { title: '意向订单管理', path: '/order' },
  { title: '文物管理', path: '/artifact' },
  { title: '知识库管理', path: '/wiki' },
  { title: '成就定义管理', path: '/achievement', roles: ['super_admin'] },
  { title: '用户管理', path: '/user', roles: ['super_admin'] },
  { title: '数据统计', path: '/stats' },
]

const visibleMenuItems = computed<AdminMenuItem[]>(() =>
  allMenuItems.filter((item) => !item.roles || item.roles.includes(userStore.role as UserRole)),
)

const activeMenu = computed(() => route.path)

// ===== 头部面包屑：从 route.matched 提取带 title 的层级 =====
const breadcrumbs = computed(() =>
  route.matched
    .filter((record) => record.meta.title)
    .map((record) => ({
      title: record.meta.title as string,
      path: record.path,
    })),
)

// ===== 头部用户信息 =====
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
      <el-aside :width="isCollapsed ? '64px' : '220px'" class="admin-layout__aside">
        <div class="admin-layout__menu-wrap">
          <el-menu
            :default-active="activeMenu"
            :collapse="isCollapsed"
            :collapse-transition="false"
            router
            class="admin-layout__menu"
          >
            <el-menu-item v-for="item in visibleMenuItems" :key="item.path" :index="item.path">
              <span>{{ item.title }}</span>
            </el-menu-item>
          </el-menu>
        </div>
        <div class="admin-layout__sidebar-footer" @click="toggleSidebar">
          <span v-if="isCollapsed" class="admin-layout__toggle-icon">&gt;&gt;</span>
          <template v-else>
            <span class="admin-layout__toggle-icon">&lt;&lt;</span>
            <span>收起菜单</span>
          </template>
        </div>
      </el-aside>

      <el-container class="admin-layout__content">
        <el-header class="admin-layout__header">
          <div class="admin-layout__breadcrumb">
            <el-breadcrumb :separator-icon="undefined" separator="/">
              <el-breadcrumb-item
                v-for="(crumb, idx) in breadcrumbs"
                :key="crumb.path"
                :to="idx < breadcrumbs.length - 1 ? crumb.path : undefined"
              >
                {{ crumb.title }}
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <div class="admin-layout__header-right">
            <span class="admin-layout__user-name">{{ userStore.displayName }}</span>
            <el-tag size="small" type="danger" effect="plain">{{ roleLabel }}</el-tag>
            <RedButton type="ghost" size="small" @click="handleLogout">退出登录</RedButton>
          </div>
        </el-header>

        <el-main class="admin-layout__main">
          <router-view />
        </el-main>
      </el-container>
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

  // ===== 侧边栏 =====
  &__aside {
    display: flex;
    flex-direction: column;
    background: @color-bg-card;
    border-right: 1px solid @color-border;
    transition: width 0.25s ease;
    overflow: hidden;
  }

  &__menu-wrap {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
  }

  &__menu {
    border-right: none;
  }

  &__sidebar-footer {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: @spacing-xs;
    padding: @spacing-sm 0;
    border-top: 1px solid @color-border;
    font-size: @font-size-sm;
    color: @color-text-secondary;
    cursor: pointer;
    white-space: nowrap;
    user-select: none;
    &:hover {
      color: @color-primary;
      background: @color-primary-light;
    }
  }

  &__toggle-icon {
    font-family: monospace;
    font-size: @font-size-sm;
  }

  // ===== 内容区（header + main）=====
  &__content {
    flex-direction: column;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 52px;
    padding: 0 @spacing-lg;
    background: @color-bg-card;
    border-bottom: 1px solid @color-border;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  }

  &__breadcrumb {
    display: flex;
    align-items: center;
  }

  &__header-right {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
  }

  &__user-name {
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
  }

  &__main {
    padding: @spacing-lg;
    background-color: @color-bg-page;
  }
}
</style>
