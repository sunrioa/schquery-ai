<template>
  <div class="admin-page ai-mcp-manage">
    <div class="page-header">
      <div class="header-left">
        <h2>MCP 管理</h2>
        <p class="sub">查看 MCP Server 列表并刷新客户端缓存</p>
      </div>

      <div class="header-actions">
        <el-button type="primary" @click="goChatConfig">去配置默认参数</el-button>
        <el-button :loading="refreshing" type="success" @click="refreshClients">刷新客户端</el-button>
        <el-button :loading="clearing" type="warning" plain @click="clearClients">清空缓存</el-button>
        <el-button :loading="loading" @click="loadServers">刷新列表</el-button>
      </div>
    </div>

    <el-card class="table-card" shadow="never" v-loading="loading">
      <el-table :data="servers" stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column label="Server URL" min-width="360">
          <template #default="{ row }">
            <span class="server-url">{{ row }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="servers.length === 0" class="empty-tip">
        当前未配置 MCP Server。请在“默认对话参数”中填写 `mcpServers`，多个地址用英文逗号分隔。
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearMcpClients, getMcpServers, refreshMcpClients } from '../../../api/ai/mcp'

const router = useRouter()

const loading = ref(false)
const refreshing = ref(false)
const clearing = ref(false)
const servers = ref([])

const loadServers = async () => {
  loading.value = true
  try {
    const res = await getMcpServers()
    servers.value = res?.data?.servers || []
  } finally {
    loading.value = false
  }
}

const refreshClients = async () => {
  refreshing.value = true
  try {
    await refreshMcpClients()
    ElMessage.success('MCP 客户端已刷新')
    loadServers()
  } finally {
    refreshing.value = false
  }
}

const clearClients = async () => {
  await ElMessageBox.confirm('确认清空 MCP 客户端缓存？', '提示', {
    confirmButtonText: '清空',
    cancelButtonText: '取消',
    type: 'warning'
  })

  clearing.value = true
  try {
    await clearMcpClients()
    ElMessage.success('已清空缓存')
    loadServers()
  } finally {
    clearing.value = false
  }
}

const goChatConfig = () => {
  router.push('/admin/ai/chat-config')
}

onMounted(() => loadServers())
</script>

<style scoped>
.server-url {
  word-break: break-all;
}

.empty-tip {
  padding: 12px 16px 16px;
  color: var(--admin-muted, #6b7280);
  font-size: 13px;
}
</style>
