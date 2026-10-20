<script setup lang="ts">
// PC管理后台 框架布局：左侧深色可折叠菜单 + 顶部面包屑/用户信息/退出
// 注意：菜单已在左侧，故不再使用公共顶部 RedNav，避免双导航
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { NavItem, UserRole } from '@red-tour-project/common'
import { useUserStore } from '@/store'
import MenuIcon from '@/components/MenuIcon.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// ===== 侧边栏折叠 =====
const isCollapsed = ref(false)
// 用户手动切换过折叠后，不再随窗口宽度自动调整
const userToggled = ref(false)

function toggleSidebar() {
  userToggled.value = true
  isCollapsed.value = !isCollapsed.value
}

// 窄屏（笔记本/平板）默认收起侧边栏，给内容区留更多空间
function syncCollapsedByWidth() {
  if (!userToggled.value) isCollapsed.value = window.innerWidth < 1280
}
onMounted(() => {
  syncCollapsedByWidth()
  window.addEventListener('resize', syncCollapsedByWidth)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', syncCollapsedByWidth)
})

// ===== 菜单定义：roles 缺省 = 所有管理员可见；用户管理仅超管可见（PRD 5.1）=====
interface AdminMenuItem extends NavItem {
  roles?: UserRole[]
}
const allMenuItems: AdminMenuItem[] = [
  { title: '首页概览', path: '/', icon: 'grid' },
  { title: '景点管理', path: '/scenic', icon: 'map-pin' },
  { title: '路线管理', path: '/route', icon: 'map' },
  { title: '打卡点管理', path: '/checkpoint', icon: 'flag' },
  { title: '特产文创管理', path: '/product', icon: 'gift' },
  { title: '意向订单管理', path: '/order', icon: 'file-text' },
  { title: '文物管理', path: '/artifact', icon: 'archive' },
  { title: '知识库管理', path: '/wiki', icon: 'book' },
  { title: '剧本管理', path: '/script', icon: 'edit' },
  { title: '成就定义管理', path: '/achievement', icon: 'award', roles: ['super_admin'] },
  { title: '用户管理', path: '/user', icon: 'users', roles: ['super_admin'] },
  { title: '数据统计', path: '/stats', icon: 'chart' },
  { title: '设备监控', path: '/device', icon: 'monitor' },
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
const displayName = computed(() => userStore.displayName || '管理员')
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())

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

function handleCommand(command: string) {
  if (command === 'logout') handleLogout()
}
</script>

<template>
  <div class="admin-layout">
    <!-- 左侧菜单（唯一导航入口） -->
    <aside class="admin-layout__aside" :style="{ width: isCollapsed ? '72px' : '232px' }">
      <div class="admin-layout__brand">
        <span class="admin-layout__logo">★</span>
        <div v-show="!isCollapsed" class="admin-layout__brand-text">
          <span class="admin-layout__brand-title">红色文旅</span>
          <span class="admin-layout__brand-sub">智能导览管理后台</span>
        </div>
      </div>

      <div class="admin-layout__menu-wrap">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapsed"
          :collapse-transition="false"
          router
          class="admin-layout__menu"
        >
          <el-menu-item v-for="item in visibleMenuItems" :key="item.path" :index="item.path">
            <MenuIcon :name="item.icon || 'grid'" />
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </el-menu>
      </div>

      <div class="admin-layout__aside-footer" @click="toggleSidebar">
        <MenuIcon :name="isCollapsed ? 'chevron-right' : 'chevron-left'" :size="16" />
        <span v-show="!isCollapsed">收起菜单</span>
      </div>
    </aside>

    <!-- 右侧内容区 -->
    <div class="admin-layout__main-wrap">
      <header class="admin-layout__header">
        <div class="admin-layout__header-left">
          <button class="admin-layout__collapse" type="button" @click="toggleSidebar">
            <MenuIcon name="menu" :size="18" />
          </button>
          <el-breadcrumb separator="/">
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
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="admin-layout__user">
              <span class="admin-layout__avatar">{{ avatarText }}</span>
              <span class="admin-layout__user-text">
                <span class="admin-layout__user-name">{{ displayName }}</span>
                <span class="admin-layout__role">{{ roleLabel }}</span>
              </span>
              <MenuIcon name="chevron-down" :size="14" />
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <MenuIcon name="logout" :size="15" />
                  <span class="admin-layout__dropdown-label">退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="admin-layout__main">
        <router-view v-slot="{ Component }">
          <transition name="admin-page" mode="out-in">
            <section class="admin-layout__page">
              <component :is="Component" />
            </section>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.admin-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background-color: @color-bg-page;

  // ===== 侧边栏 =====
  &__aside {
    display: flex;
    flex-direction: column;
    flex: none;
    height: 100%;
    background: linear-gradient(180deg, #23252f 0%, #171921 100%);
    transition: width 0.25s ease;
    overflow: hidden;
  }

  &__brand {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    height: 60px;
    padding: 0 18px;
    flex: none;
  }

  &__logo {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    flex: none;
    border-radius: 10px;
    font-size: 18px;
    color: #fff;
    background: linear-gradient(135deg, @color-primary 0%, @color-primary-hover 100%);
    box-shadow: 0 4px 12px rgba(196, 30, 58, 0.4);
  }

  &__brand-text {
    display: flex;
    flex-direction: column;
    overflow: hidden;
    white-space: nowrap;
  }

  &__brand-title {
    font-size: @font-size-lg;
    font-weight: 700;
    color: #fff;
    letter-spacing: 1px;
  }

  &__brand-sub {
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.45);
  }

  &__menu-wrap {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
    padding: @spacing-xs 0;
  }

  &__menu {
    background: transparent;
    border-right: none;
    padding: 0 10px;

    // Element Plus 菜单主题变量
    --el-menu-bg-color: transparent;
    --el-menu-text-color: rgba(255, 255, 255, 0.72);
    --el-menu-active-color: #fff;
    --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
    --el-menu-item-height: 44px;
    --el-menu-item-font-size: @font-size-base;
    --el-menu-base-level-padding: 12px;
    --el-menu-icon-width: 22px;

    :deep(.el-menu-item) {
      height: 44px;
      line-height: 44px;
      margin-bottom: 2px;
      border-radius: @radius-base;
      transition: background 0.2s ease, color 0.2s ease;

      &:hover {
        color: #fff;
      }

      &.is-active {
        color: #fff;
        font-weight: 600;
        background: linear-gradient(90deg, @color-primary 0%, @color-primary-hover 100%);
        box-shadow: 0 4px 12px rgba(196, 30, 58, 0.32);
      }
    }

    // 折叠态：图标居中
    &.el-menu--collapse :deep(.el-menu-item) {
      justify-content: center;
      padding-left: 0;
      padding-right: 0;
    }
  }

  &__aside-footer {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: @spacing-xs;
    height: 46px;
    flex: none;
    border-top: 1px solid rgba(255, 255, 255, 0.08);
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.55);
    cursor: pointer;
    white-space: nowrap;
    user-select: none;

    &:hover {
      color: #fff;
      background: rgba(255, 255, 255, 0.06);
    }
  }

  // ===== 右侧 =====
  &__main-wrap {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-width: 0;
    height: 100%;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 60px;
    flex: none;
    padding: 0 @spacing-lg;
    background: @color-bg-card;
    border-bottom: 1px solid #eceef2;
  }

  &__header-left {
    display: flex;
    align-items: center;
    gap: @spacing-md;
    min-width: 0;
  }

  &__collapse {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border: none;
    border-radius: @radius-sm;
    background: transparent;
    color: @color-text-regular;

    &:hover {
      color: @color-primary;
      background: @color-primary-light;
    }
  }

  &__header-right {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
  }

  &__user {
    display: flex;
    align-items: center;
    gap: @spacing-sm;
    padding: 4px 8px;
    border-radius: 999px;
    cursor: pointer;
    outline: none;
    color: @color-text-regular;
    transition: background 0.2s ease;

    &:hover {
      background: @color-bg-page;
    }
  }

  &__avatar {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    font-size: @font-size-base;
    font-weight: 600;
    color: #fff;
    background: linear-gradient(135deg, @color-primary 0%, @color-primary-hover 100%);
  }

  &__user-text {
    display: flex;
    flex-direction: column;
    line-height: 1.25;
  }

  &__user-name {
    font-size: @font-size-base;
    font-weight: 600;
    color: @color-text-primary;
  }

  &__role {
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__dropdown-label {
    margin-left: @spacing-xs;
  }

  // ===== 内容区 =====
  &__main {
    flex: 1;
    overflow-y: auto;
    padding: @spacing-lg;
  }

  &__page {
    min-height: 100%;
    padding: @spacing-lg;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: 0 1px 3px rgba(31, 31, 31, 0.06);
  }
}

// 页面切换动画
.admin-page-enter-active,
.admin-page-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.admin-page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.admin-page-leave-to {
  opacity: 0;
}
</style>
