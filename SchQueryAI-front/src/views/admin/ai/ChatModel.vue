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
            class="search-input"
            @keyup.enter="loadList"
            @clear="loadList"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="query.category" placeholder="模型分类" clearable class="search-select" @change="loadList">
            <el-option label="Chat (对话)" value="chat" />
            <el-option label="Vector (向量)" value="vector" />
            <el-option label="Rerank (重排)" value="rerank" />
            <el-option label="Image (绘图)" value="image" />
          </el-select>
          <el-select v-model="query.modelShow" placeholder="显示状态" clearable class="search-select-sm" @change="loadList">
            <el-option label="已启用" :value="1" />
            <el-option label="已隐藏" :value="0" />
          </el-select>
        </div>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never" v-loading="loading">
      <div class="table-scroll">
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
        <el-table-column label="操作" width="160" align="center" :fixed="isMobile ? undefined : 'right'">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">配置</el-button>
            <el-divider direction="vertical" />
            <el-button type="danger" link @click="removeRow(row)">移除</el-button>
          </template>
        </el-table-column>
        </el-table>
      </div>

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

    <el-dialog
      v-model="editorVisible"
      :title="editorTitle"
      width="800px"
      class="model-editor-dialog"
      destroy-on-close
      top="5vh"
    >
      <el-form :model="editor" label-width="100px" class="editor-form">
        <!-- Section: 基础信息 -->
        <div class="form-section-title">基础配置</div>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item required>
              <template #label>
                <div class="form-label-container">
                  <span>模型分类</span>
                  <el-tooltip content="选择模型的应用类型，不同类型的模型用于不同的场景" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-select v-model="editor.category" placeholder="选择分类" style="width: 100%">
                <el-option label="Chat (对话)" value="chat" />
                <el-option label="Vector (向量)" value="vector" />
                <el-option label="Rerank (重排)" value="rerank" />
                <el-option label="Image (绘图)" value="image" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item required>
              <template #label>
                <div class="form-label-container">
                  <span>模型标识</span>
                  <el-tooltip content="调用API时的模型ID，例如 'gpt-4' 或 'qwen-plus'" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.modelName" placeholder="例如：qwen-plus" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>服务商</span>
                  <el-tooltip content="模型提供商名称，仅用于前端展示标识" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.providerName" placeholder="例如：AliYun" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>优先级</span>
                  <el-tooltip content="数值越大，在列表中显示越靠前" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input-number
                v-model="editor.priority"
                :min="1"
                :max="9999"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>模型描述</span>
                  <el-tooltip content="展示给用户的简短描述，说明模型特点" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.modelDescribe" placeholder="简要描述该模型的特点..." />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>启用状态</span>
                  <el-tooltip content="关闭后，用户端将无法看到并使用该模型" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-switch
                v-model="modelShowSwitch"
                active-text="已启用"
                inactive-text="已停用"
                inline-prompt
                style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- Section: API 参数 -->
        <div class="form-section-title">API 连接配置</div>
        <el-row :gutter="24">
          <el-col :span="24">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>API 域名</span>
                  <el-tooltip content="接口的基础地址，例如 https://dashscope.aliyuncs.com" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.apiHost" placeholder="例如：https://dashscope.aliyuncs.com" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>API 密钥</span>
                  <el-tooltip content="用于鉴权的 API Key (sk-xxxxxxxx)" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.apiKey" type="password" show-password placeholder="请输入 API Key" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>请求后缀</span>
                  <el-tooltip
                    content="如果使用标准OpenAI格式通常不需要填，特殊接口可能需要指定路径"
                    placement="top"
                  >
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.apiUrl" placeholder="可选：例如 /v1/chat/completions" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- Section: 高级设置 -->
        <div class="form-section-title">高级设置</div>
        <el-form-item v-if="editor.category === 'chat'">
          <template #label>
            <div class="form-label-container">
              <span>系统提示</span>
              <el-tooltip content="设置模型的默认系统角色（System Prompt），定义其行为模式" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
          </template>
          <el-input
            v-model="editor.systemPrompt"
            type="textarea"
            :rows="3"
            placeholder="你是一个有用的助手..."
          />
        </el-form-item>
        <el-form-item>
          <template #label>
            <div class="form-label-container">
              <span>后台备注</span>
              <el-tooltip content="仅管理员可见的内部备注信息" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
          </template>
          <el-input v-model="editor.remark" type="textarea" :rows="2" placeholder="内部备注..." />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editorVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Refresh, QuestionFilled } from '@element-plus/icons-vue'
import { getChatModelList, removeChatModel, saveChatModel } from '../../../api/ai/chatModel'

const isMobile = ref(false)
const updateIsMobile = () => {
  isMobile.value = window.innerWidth <= 768
}

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

onMounted(() => {
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
  loadList()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateIsMobile)
})
</script>

<style scoped>
.ai-model-container {
  padding: 0;
  background: transparent;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 16px;
}

.page-header .title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--app-text);
}

.page-header .subtitle {
  margin: 4px 0 0;
  color: var(--app-muted);
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

.search-input {
  width: 240px;
}

.search-select {
  width: 140px;
}

.search-select-sm {
  width: 120px;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.table-scroll :deep(.el-table) {
  min-width: 980px;
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
  color: var(--app-text);
}

.provider-text {
  font-size: 12px;
  color: var(--app-muted-2);
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
  background-color: var(--app-surface-2) !important;
  color: var(--app-text);
  font-weight: 600;
}

/* 弹窗样式优化 */
.form-section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text);
  margin: 16px 0 16px;
  padding-left: 10px;
  border-left: 4px solid var(--app-primary);
  line-height: 1;
}

.form-label-container {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.help-icon {
  color: var(--app-muted-2);
  cursor: help;
  font-size: 14px;
}

.help-icon:hover {
  color: var(--app-primary);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 10px;
}

:deep(.model-editor-dialog) {
  border-radius: 12px;
}

:deep(.model-editor-dialog .el-dialog__header) {
  margin-right: 0;
  border-bottom: 1px solid var(--app-border);
  padding: 20px 24px;
}

:deep(.model-editor-dialog .el-dialog__body) {
  padding: 24px 32px;
}

:deep(.model-editor-dialog .el-dialog__footer) {
  border-top: 1px solid var(--app-border);
  padding: 16px 24px;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-ops {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    justify-content: flex-start;
  }

  .search-wrapper {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .search-items {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input,
  .search-select,
  .search-select-sm {
    width: 100%;
  }

  .pagination-container {
    justify-content: center;
    margin-top: 16px;
  }
}
</style>
