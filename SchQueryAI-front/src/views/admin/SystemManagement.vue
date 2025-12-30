<template>
  <div class="admin-page legacy-page">
    <div class="page-header">
      <div class="header-left">
        <h2>系统管理（已下线）</h2>
        <p class="sub">旧版聚合页面已拆分为独立模块，请使用左侧菜单进入。</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="goDashboard">返回控制台</el-button>
      </div>
    </div>

    <el-card>
      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="该页面已废弃"
        description="请使用：用户管理 / 敏感词管理 / 分词管理 / 系统监控 / 系统日志 / 客服消息 / AI 管理等页面。"
      />
    </el-card>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goDashboard = () => {
  router.push('/dashboard')
}
</script>

<style scoped>
.legacy-page {
  max-width: 1100px;
  margin: 0 auto;
}
</style>
