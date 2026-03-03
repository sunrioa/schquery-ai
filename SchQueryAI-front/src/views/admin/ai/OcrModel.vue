<template>
  <div class="admin-page ai-model-container">
    <div class="page-header">
      <div class="header-info">
        <h2 class="title">OCR 文字识别模型管理</h2>
        <p class="subtitle">配置阿里云通义千问 VL-OCR 模型的 API 参数，用于 PDF 扫描件识别</p>
      </div>
      <div class="header-ops">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>添加配置
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
          <el-select v-model="query.modelShow" placeholder="启用状态" clearable class="search-select-sm" @change="loadList">
            <el-option label="已启用" :value="1" />
            <el-option label="已停用" :value="0" />
          </el-select>
        </div>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never" v-loading="loading">
      <div class="table-scroll">
        <el-table :data="list" stripe style="width: 100%" class="custom-table">
          <el-table-column prop="id" label="ID" width="80" align="center" />
          <el-table-column prop="modelName" label="模型标识" min-width="200">
            <template #default="{ row }">
              <div class="model-name-cell">
                <span class="name-text">{{ row.modelName }}</span>
                <div class="provider-text" v-if="row.providerName">{{ row.providerName }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="apiHost" label="API 地址" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <code class="api-code">{{ row.apiHost || '-' }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="priority" label="优先级" width="90" align="center" />
          <el-table-column prop="modelShow" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.modelShow === 1 ? 'success' : 'info'" effect="light">
                {{ row.modelShow === 1 ? '已启用' : '已停用' }}
              </el-tag>
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
      width="840px"
      class="model-editor-dialog"
      destroy-on-close
      top="5vh"
    >
      <el-form :model="editor" label-width="110px" class="editor-form">
        <div class="form-section-title">基础配置</div>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item required>
              <template #label>
                <div class="form-label-container">
                  <span>模型标识</span>
                  <el-tooltip content="调用 OCR 服务时的模型名称" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.modelName" placeholder="例如：qwen-vl-max" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>服务商</span>
                  <el-tooltip content="仅用于后台展示的供应商名称" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.providerName" placeholder="例如：Aliyun" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>优先级</span>
                  <el-tooltip content="数值越大越优先被选中" placement="top">
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
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>状态</span>
                  <el-tooltip content="停用后该模型将不会被使用" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-switch
                v-model="editor.modelShow"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="停用"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="form-section-title">API 配置</div>
        <el-row :gutter="24">
          <el-col :span="24">
            <el-form-item required>
              <template #label>
                <div class="form-label-container">
                  <span>API 地址</span>
                  <el-tooltip content="阿里云 DashScope OpenAI 兼容接口地址" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input
                v-model="editor.apiHost"
                placeholder="例如：https://dashscope.aliyuncs.com/compatible-mode/v1"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item required>
              <template #label>
                <div class="form-label-container">
                  <span>API Key</span>
                  <el-tooltip content="阿里云 DashScope API 密钥" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input
                v-model="editor.apiKey"
                placeholder="请输入 API Key"
                type="password"
                show-password
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>请求后缀</span>
                  <el-tooltip content="可选，一般留空" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.apiUrl" placeholder="例如：/chat/completions（通常留空）" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="form-section-title">其他配置</div>
        <el-row :gutter="24">
          <el-col :span="24">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>模型描述</span>
                  <el-tooltip content="对该模型的简短描述" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input
                v-model="editor.modelDescribe"
                type="textarea"
                :rows="2"
                placeholder="例如：阿里云通义千问 VL-OCR 模型，支持高精度文字识别"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>备注</span>
                  <el-tooltip content="其他补充说明信息" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input
                v-model="editor.remark"
                type="textarea"
                :rows="2"
                placeholder="例如：适用于 PDF 扫描件、图片中的文字提取"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editorVisible = false">取消</el-button>
          <el-button type="primary" @click="saveEditor" :loading="saving">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search, QuestionFilled } from '@element-plus/icons-vue'
import { getChatModelList, saveChatModel, removeChatModel } from '@/api/ai/chatModel'

const { confirm } = ElMessageBox

// 列表数据
const list = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

// 查询参数
const query = reactive({
  modelName: '',
  modelShow: null
})

// 编辑器
const editorVisible = ref(false)
const editorTitle = ref('')
const saving = ref(false)
const editor = reactive({
  id: null,
  category: 'image',
  modelName: '',
  providerName: '',
  priority: 1,
  modelShow: 1,
  apiHost: '',
  apiKey: '',
  apiUrl: '',
  modelDescribe: '',
  remark: ''
})

// 是否为移动端
const isMobile = computed(() => {
  return window.innerWidth < 768
})

// 加载列表
const loadList = async () => {
  loading.value = true
  try {
    const res = await getChatModelList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      category: 'image',
      ...query
    })
    list.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    console.error('加载 OCR 模型列表失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

// 分页处理
const handleSizeChange = (size) => {
  pageSize.value = size
  loadList()
}

const handlePageChange = (page) => {
  pageNum.value = page
  loadList()
}

// 打开创建
const openCreate = () => {
  editorVisible.value = true
  editorTitle.value = '添加 OCR 模型'
  resetEditor()
}

// 打开编辑
const openEdit = (row) => {
  editorVisible.value = true
  editorTitle.value = '编辑 OCR 模型'
  Object.assign(editor, row)
}

// 重置编辑器
const resetEditor = () => {
  editor.id = null
  editor.category = 'image'
  editor.modelName = ''
  editor.providerName = ''
  editor.priority = 1
  editor.modelShow = 1
  editor.apiHost = ''
  editor.apiKey = ''
  editor.apiUrl = ''
  editor.modelDescribe = ''
  editor.remark = ''
}

// 保存编辑器
const saveEditor = async () => {
  if (!editor.modelName) {
    ElMessage.warning('请输入模型标识')
    return
  }
  if (!editor.apiHost) {
    ElMessage.warning('请输入 API 地址')
    return
  }
  if (!editor.apiKey) {
    ElMessage.warning('请输入 API Key')
    return
  }

  saving.value = true
  try {
    await saveChatModel({ ...editor })
    ElMessage.success('保存成功')
    editorVisible.value = false
    loadList()
  } catch (error) {
    console.error('保存 OCR 模型失败:', error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 删除
const removeRow = async (row) => {
  try {
    await confirm('确定要删除该 OCR 模型配置吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await removeChatModel(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除 OCR 模型失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

onMounted(() => {
  loadList()
})
</script>

<style scoped lang="scss">
.admin-page.ai-model-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .header-info {
    .title {
      font-size: 20px;
      font-weight: bold;
      margin: 0;
    }

    .subtitle {
      font-size: 13px;
      color: #666;
      margin: 8px 0 0 0;
    }
  }
}

.search-card {
  margin-bottom: 16px;
}

.search-wrapper {
  .search-items {
    display: flex;
    gap: 12px;

    .search-input {
      width: 300px;
    }

    .search-select-sm {
      width: 120px;
    }
  }
}

.custom-table {
  .model-name-cell {
    display: flex;
    flex-direction: column;
    gap: 4px;

    .name-text {
      font-weight: 500;
    }

    .provider-text {
      font-size: 12px;
      color: #666;
    }
  }

  .api-code {
    font-family: Consolas, monospace;
    font-size: 12px;
    background: #f5f7fa;
    padding: 2px 6px;
    border-radius: 3px;
  }

  .time-text {
    color: #666;
    font-size: 13px;
  }
}

.model-editor-dialog {
  .form-section-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    margin: 20px 0 12px 0;
    padding-left: 8px;
    border-left: 3px solid #409EFF;
  }

  .form-label-container {
    display: flex;
    align-items: center;
    gap: 6px;

    .help-icon {
      color: #909399;
      cursor: help;
    }
  }

  .editor-form {
    max-height: 60vh;
    overflow-y: auto;
  }
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
