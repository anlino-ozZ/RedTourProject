<script setup lang="ts">
// PC管理后台 登录页：全屏红色主题 + 居中卡片，登录成功后按 redirect 回跳
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { RedButton } from '@red-tour-project/common'
import { useUserStore } from '@/store'

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
    <div class="login-page__card">
      <div class="login-page__brand">
        <div class="login-page__star">★</div>
        <h1 class="login-page__title">红色文旅</h1>
        <p class="login-page__subtitle">智能导览系统 · 管理后台</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        class="login-page__form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            clearable
          />
        </el-form-item>
        <el-form-item>
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
    <p class="login-page__footer">红色文旅智能导览系统 · 离线边缘部署</p>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.login-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: @spacing-lg;
  background: linear-gradient(
    135deg,
    @color-primary-active 0%,
    @color-primary 55%,
    @color-primary-hover 100%
  );

  &__card {
    width: 400px;
    max-width: 100%;
    padding: @spacing-xl @spacing-xl @spacing-lg;
    background: @color-bg-card;
    border-radius: @radius-lg;
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
  }

  &__brand {
    text-align: center;
    margin-bottom: @spacing-xl;
  }

  &__star {
    width: 56px;
    height: 56px;
    margin: 0 auto @spacing-md;
    line-height: 56px;
    font-size: 28px;
    color: #fff;
    background: @color-primary;
    border-radius: 50%;
    box-shadow: 0 4px 12px rgba(196, 30, 58, 0.4);
  }

  &__title {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
    letter-spacing: 4px;
    color: @color-primary;
  }

  &__subtitle {
    margin: @spacing-sm 0 0;
    font-size: @font-size-base;
    color: @color-text-secondary;
  }

  &__form {
    :deep(.el-form-item) {
      margin-bottom: @spacing-lg;
    }
  }

  &__submit {
    width: 100%;
    font-size: @font-size-lg;
    letter-spacing: 8px;
  }

  &__tip {
    margin: 0;
    text-align: center;
    font-size: @font-size-sm;
    color: @color-text-secondary;
  }

  &__footer {
    margin-top: @spacing-lg;
    font-size: @font-size-sm;
    color: rgba(255, 255, 255, 0.75);
  }
}
</style>
