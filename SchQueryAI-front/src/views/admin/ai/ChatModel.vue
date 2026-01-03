<template>
  <div class="admin-page ai-model-container">
    <div class="page-header">
      <div class="header-info">
        <h2 class="title">AI 模型库管理</h2>
        <p class="subtitle">配置大语言模型、向量模型以及重排序模型的 API 参数</p>
      </div>
      <div class="header-ops">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>添加新模型
        </el-button>
        <el-button @click="loadList">
          <el-icon><Refresh /></el-icon>重载
        </el-button>
      </div>
    </div>

    <el-card class="search-card" shadow="never">
      <div class="search-wrapper">
        <div class="search-items">
          <el-input
            v-model="query.modelName"
            placeholder="根据模型名称搜索..."
            clearable
            style="width: 240px"
            @keyup.enter="loadList"
            @clear="loadList"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="query.category" placeholder="模型分类" clearable style="width: 140px" @change="loadList">
            <el-option label="Chat (对话)" value="chat" />
            <el-option label="Vector (向量)" value="vector" />
            <el-option label="Rerank (重排)" value="rerank" />
            <el-option label="Image (绘图)" value="image" />
          </el-select>
          <el-select v-model="query.modelShow" placeholder="显示状态" clearable style="width: 120px" @change="loadList">
            <el-option label="已启用" :value="1" />
            <el-option label="已隐藏" :value="0" />
          </el-select>
        </div>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never" v-loading="loading">
      <el-table :data="list" stripe style="width: 100%" class="custom-table">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="category" label="分类" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getCategoryType(row.category)" effect="plain" size="small">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型标识" min-width="180">
          <template #default="{ row }">
            <div class="model-name-cell">
              <span class="name-text">{{ row.modelName }}</span>
              <div class="provider-text" v-if="row.providerName">{{ row.providerName }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" align="center" />
        <el-table-column prop="modelShow" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.modelShow === 1 ? 'success' : 'info'" effect="light">
              {{ row.modelShow === 1 ? '使用中' : '未开启' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="apiHost" label="API Endpoint" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="api-code">{{ row.apiHost || '-' }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新于" width="160" align="center">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.updateTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">配置</el-button>
            <el-divider direction="vertical" />
            <el-button type="danger" link @click="removeRow(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="editorVisible" :title="editorTitle" width="920px">
      <el-form :model="editor" label-width="120px">
        <el-form-item label="category" required>
          <el-select v-model="editor.category" placeholder="选择分类" style="width: 100%">
            <el-option label="chat" value="chat" />
            <el-option label="vector" value="vector" />
            <el-option label="rerank" value="rerank" />
            <el-option label="image" value="image" />
          </el-select>
        </el-form-item>
        <el-form-item label="modelName" required>
          <el-input v-model="editor.modelName" placeholder="例如：qwen-plus" />
        </el-form-item>
        <el-form-item label="providerName">
          <el-input v-model="editor.providerName" placeholder="可选" />
        </el-form-item>
        <el-form-item label="modelDescribe">
          <el-input v-model="editor.modelDescribe" placeholder="可选" />
        </el-form-item>
        <el-form-item label="priority">
          <el-input-number v-model="editor.priority" :min="1" :max="9999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="modelShow">
          <el-switch v-model="modelShowSwitch" />
        </el-form-item>

        <el-divider content-position="left">API 配置</el-divider>
        <el-form-item label="apiHost">
          <el-input v-model="editor.apiHost" placeholder="例如：https://dashscope.aliyuncs.com" />
        </el-form-item>
        <el-form-item label="apiUrl">
          <el-input v-model="editor.apiUrl" placeholder="可选：请求后缀" />
        </el-form-item>
        <el-form-item label="apiKey">
          <el-input v-model="editor.apiKey" type="password" show-password placeholder="可选：密钥" />
        </el-form-item>

        <el-divider content-position="left">Prompt / 备注</el-divider>
        <el-form-item label="systemPrompt">
          <el-input v-model="editor.systemPrompt" type="textarea" :rows="4" placeholder="可选：仅 chat 类模型常用" />
        </el-form-item>
        <el-form-item label="remark">
          <el-input v-model="editor.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Refresh } from '@element-plus/icons-vue'
import { getChatModelList, removeChatModel, saveChatModel } from '../../../api/ai/chatModel'

const loading = ref(false)
const saving = ref(false)

const query = ref({
  category: '',
  modelShow: undefined,
  modelName: ''
})

const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const list = ref([])

const loadList = async () => {
  loading.value = true
  try {
    const res = await getChatModelList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      category: query.value.category || undefined,
      modelShow: query.value.modelShow,
      modelName: query.value.modelName?.trim() || undefined
    })
    list.value = res?.data?.records || []
    total.value = res?.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleSizeChange = (size) => {
  pageSize.value = size
  pageNum.value = 1
  loadList()
}

const handlePageChange = (page) => {
  pageNum.value = page
  loadList()
}

const editorVisible = ref(false)
const editorTitle = ref('新建模型')
const editor = ref({
  id: null,
  category: 'chat',
  modelName: '',
  providerName: '',
  modelDescribe: '',
  modelPrice: null,
  modelType: '',
  modelShow: 1,
  systemPrompt: '',
  apiHost: '',
  apiKey: '',
  apiUrl: '',
  priority: 1,
  remark: ''
})

const modelShowSwitch = computed({
  get: () => Number(editor.value.modelShow) === 1,
  set: (val) => {
    editor.value.modelShow = val ? 1 : 0
  }
})

const openCreate = () => {
  editorTitle.value = '新建模型'
  editor.value = {
    id: null,
    category: 'chat',
    modelName: '',
    providerName: '',
    modelDescribe: '',
    modelPrice: null,
    modelType: '',
    modelShow: 1,
    systemPrompt: '',
    apiHost: '',
    apiKey: '',
    apiUrl: '',
    priority: 1,
    remark: ''
  }
  editorVisible.value = true
}

const openEdit = (row) => {
  editorTitle.value = `编辑模型：${row.modelName}`
  editor.value = { ...row }
  editorVisible.value = true
}

const save = async () => {
  if (!editor.value.category || !editor.value.category.trim()) {
    ElMessage.error('category不能为空')
    return
  }
  if (!editor.value.modelName || !editor.value.modelName.trim()) {
    ElMessage.error('modelName不能为空')
    return
  }
  saving.value = true
  try {
    await saveChatModel({
      ...editor.value,
      category: editor.value.category.trim(),
      modelName: editor.value.modelName.trim()
    })
    ElMessage.success('保存成功')
    editorVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

const removeRow = async (row) => {
  await ElMessageBox.confirm(`确认删除模型「${row.modelName}」？`, '提示', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await removeChatModel(row.id)
  ElMessage.success('删除成功')
  loadList()
}

const getCategoryType = (cat) => {
  const map = {
    chat: 'success',
    vector: 'warning',
    rerank: 'danger',
    image: 'info'
  }
  return map[cat] || ''
}

const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return String(timestamp)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
    date.getMinutes()
  )}:${pad(date.getSeconds())}`
}

onMounted(() => loadList())
</script>

<style scoped>
.ai-model-container {
  padding: 24px;
  background: #f5f7fa;
  min-height: calc(100vh - 84px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header .title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
}

.page-header .subtitle {
  margin: 4px 0 0;
  color: #606266;
  font-size: 14px;
}

.search-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.search-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-items {
  display: flex;
  gap: 12px;
}

.table-card {
  border-radius: 8px;
}

.model-name-cell {
  display: flex;
  flex-direction: column;
}

.name-text {
  font-weight: 600;
  color: #303133;
}

.provider-text {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.api-code {
  font-family: monospace;
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}

.time-text {
  font-size: 13px;
  color: #606266;
}

.pagination-container {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

:deep(.custom-table) {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-table__header-wrapper th) {
  background-color: #f8f9fb !important;
  color: #303133;
  font-weight: 600;
}
</style>
