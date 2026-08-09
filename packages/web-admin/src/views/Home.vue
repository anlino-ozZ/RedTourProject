<script setup lang="ts">
// PC管理后台 占位首页
import { RedCard, RedButton } from '@red-tour-project/common'

function handleClick() {
  console.log('进入管理')
}
</script>

<template>
  <div class="home">
    <RedCard title="红色文旅管理后台">
      <p class="home__desc">PC管理端 · 景点 / 路线 / 用户 / 数据一站式管理</p>
      <div class="home__actions">
        <RedButton type="primary" @click="handleClick">开始管理</RedButton>
        <el-button type="primary">Element Plus 按钮</el-button>
        <el-button type="success">数据统计</el-button>
      </div>
    </RedCard>
  </div>
</template>

<style lang="less" scoped>
.home {
  max-width: 800px;
  margin: 0 auto;
  &__desc {
    color: #8c8c8c;
    font-size: 14px;
    margin-bottom: 16px;
  }
  &__actions {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
  }
}
</style>
