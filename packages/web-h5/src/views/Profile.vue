<script setup lang="ts">
// 个人中心页占位（待后续任务开发）
import { RedCard, RedButton } from '@red-tour-project/common'
</script>

<template>
  <div class="profile">
    <RedCard title="我的">
      <div class="profile__avatar">
        <div class="profile__avatar-placeholder">游客</div>
      </div>
      <p class="profile__desc">登录后可同步打卡记录与成就</p>
      <div class="profile__actions">
        <RedButton type="primary" size="medium">登录 / 注册</RedButton>
      </div>
    </RedCard>
  </div>
</template>

<style lang="less" scoped>
@import '@common/style/variables.less';

.profile {
  &__avatar {
    display: flex;
    justify-content: center;
    margin-bottom: @spacing-md;
  }
  &__avatar-placeholder {
    width: 64px;
    height: 64px;
    border-radius: 50%;
    background: @color-primary-light;
    color: @color-primary;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: @font-size-base;
    font-weight: 600;
  }
  &__desc {
    color: @color-text-secondary;
    font-size: @font-size-sm;
    text-align: center;
    margin-bottom: @spacing-md;
  }
  &__actions {
    display: flex;
    justify-content: center;
  }
}
</style>
