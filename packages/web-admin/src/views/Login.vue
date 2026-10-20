<script setup lang="ts">
// PC管理后台 登录页：左侧红色品牌区 + 右侧表单卡片，登录成功后按 redirect 回跳
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { RedButton } from '@red-tour-project/common'
import { useUserStore } from '@/store'
import MenuIcon from '@/components/MenuIcon.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
})

// 校验规则与后端 /auth/login 契约一致：用户名 3-20、密码 6-32
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为 3-20 字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 字符', trigger: 'blur' },
  ],
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login({
        username: form.username.trim(),
        password: form.password,
      })
      // 登录成功：优先回跳守卫记录的目标页，否则进首页
      const redirect = (route.query.redirect as string) || '/'
      router.replace(redirect)
    } catch {
      // 错误提示已由 axios 拦截器 / userStore 统一弹出，这里只需结束 loading
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-page">
    <!-- 左侧品牌区 -->
    <section class="login-page__brand">
      <div class="login-page__brand-content">
        <div class="login-page__logo">★</div>
        <h1 class="login-page__brand-title">红色文旅</h1>
        <p class="login-page__brand-sub">智能导览系统 · 管理后台</p>

        <ul class="login-page__features">
          <li>
            <MenuIcon name="map-pin" :size="18" />
            <span>景区 / 路线 / 打卡点统一管理</span>
          </li>
          <li>
            <MenuIcon name="book" :size="18" />
            <span>知识库与红色剧本内容配置</span>
          </li>
          <li>
            <MenuIcon name="chart" :size="18" />
            <span>客流热点与设备状态实时监控</span>
          </li>
        </ul>
      </div>
      <p class="login-page__brand-footer">红色文旅智能导览系统 · 离线边缘部署</p>
    </section>

    <!-- 右侧表单 -->
    <section class="login-page__panel">
      <div class="login-page__card">
        <h2 class="login-page__title">管理员登录</h2>
        <p class="login-page__desc">请使用管理员账号登录后台</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          class="login-page__form"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              clearable
            >
              <template #prefix><MenuIcon name="user" :size="16" /></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              show-password
              clearable
            >
              <template #prefix><MenuIcon name="lock" :size="16" /></template>
            </el-input>
          </el-form-item>
          <el-form-item class="login-page__submit-item">
            <RedButton
              type="primary"
              size="large"
              class="login-page__submit"
              :loading="loading"
              @click="handleLogin"
            >
              登 录
            </RedButton>
          </el-form-item>
        </el-form>

        <p class="login-page__tip">默认管理员账号：admin / admin123</p>
      </div>
    </section>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.login-page {
  display: flex;
  min-height: 100vh;
  background: @color-bg-page;

  // ===== 左侧品牌区 =====
  &__brand {
    position: relative;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    width: 46%;
    max-width: 640px;
    padding: @spacing-xl * 1.5;
    color: #fff;
    overflow: hidden;
    background: linear-gradient(150deg, @color-primary-active 0%, @color-primary 55%, #e0354f 100%);

    // 装饰圆
    &::before,
    &::after {
      content: '';
      position: absolute;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.08);
    }
    &::before {
      width: 320px;
      height: 320px;
      right: -120px;
      top: -80px;
    }
    &::after {
      width: 220px;
      height: 220px;
      left: -80px;
      bottom: -60px;
    }
  }

  &__brand-content {
    position: relative;
    z-index: 1;
  }

  &__logo {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 56px;
    margin-bottom: @spacing-lg;
    font-size: 28px;
    border-radius: 16px;
    background: rgba(255, 255, 255, 0.18);
    box-shadow: 0 6px 18px rgba(0, 0, 0, 0.16);
  }

  &__brand-title {
    margin: 0;
    font-size: 32px;
    font-weight: 700;
    letter-spacing: 6px;
  }

  &__brand-sub {
    margin: @spacing-sm 0 0;
    font-size: @font-size-lg;
    color: rgba(255, 255, 255, 0.82);
  }

  &__features {
    margin-top: @spacing-xl * 1.5;
    display: flex;
    flex-direction: column;
    gap: @spacing-md;

    li {
      display: flex;
      align-items: center;
      gap: @spacing-sm;
      font-size: @font-size-base;
      color: rgba(255, 255, 255, 0.9);
    }
  }

  &__brand-footer {
    position: relative;
    z-index: 1;
    margin: 0;
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.7);
  }

  // ===== 右侧表单 =====
  &__panel {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: @spacing-lg;
  }

  &__card {
    width: 380px;
    max-width: 100%;
    padding: @spacing-xl;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: 0 12px 40px rgba(31, 31, 31, 0.1);
  }

  &__title {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
    color: @color-text-primary;
  }

  &__desc {
    margin: @spacing-xs 0 @spacing-lg;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__form {
    :deep(.el-form-item) {
      margin-bottom: @spacing-lg;
    }

    :deep(.el-input__prefix-inner) {
      display: flex;
      align-items: center;
      color: @color-text-secondary;
    }
  }

  &__submit-item {
    margin-bottom: 0;
  }

  &__submit {
    width: 100%;
    font-size: @font-size-lg;
    letter-spacing: 8px;
  }

  &__tip {
    margin: @spacing-lg 0 0;
    text-align: center;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  // 窄屏隐藏品牌区，仅保留表单
  @media (max-width: 900px) {
    &__brand {
      display: none;
    }
  }
}
</style>
