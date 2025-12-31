<template>
  <div class="admin-page legacy-page">
    <div class="page-header">
      <div class="header-left">
        <h2>知识库上传（已下线）</h2>
        <p class="sub">已合并进「知识库管理 → 文档」</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="goKnowledge">打开知识库管理</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="该页面已废弃"
        description="请前往：AI 管理 → 知识库管理，进入具体知识库后在文档页进行上传/重建/片段编辑。"
      />
    </el-card>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goKnowledge = () => {
  router.push('/admin/ai/knowledge')
}
</script>

<style scoped>
.legacy-page {
  max-width: 1100px;
  margin: 0 auto;
}
</style>
