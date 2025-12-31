<template>
  <div class="admin-page ai-chat-model">
    <div class="page-header">
      <div class="header-left">
        <h2>模型管理</h2>
        <p class="sub">管理 chat_model（chat/vector/rerank 等）</p>
      </div>

      <div class="header-actions">
        <el-select v-model="query.category" placeholder="分类" clearable style="width: 140px" @change="loadList">
          <el-option label="chat" value="chat" />
          <el-option label="vector" value="vector" />
          <el-option label="rerank" value="rerank" />
          <el-option label="image" value="image" />
        </el-select>
        <el-select v-model="query.modelShow" placeholder="启用" clearable style="width: 120px" @change="loadList">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-input
          v-model="query.modelName"
          placeholder="搜索模型名"
          clearable
          style="width: 220px"
          @keyup.enter="loadList"
          @clear="loadList"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="openCreate">新建</el-button>
        <el-button @click="loadList">刷新</el-button>
      </div>
    </div>

    <el-card class="table-card" shadow="never" v-loading="loading">
      <el-table :data="list" stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="id" label="ID" width="90" align="center" />
        <el-table-column prop="category" label="分类" width="110" align="center">
          <template #default="{ row }">
            <el-tag>{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型名" min-width="200" />
        <el-table-column prop="providerName" label="供应商" width="140" />
        <el-table-column prop="priority" label="优先级" width="90" align="center" />
        <el-table-column prop="modelShow" label="启用" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.modelShow === 1 ? 'success' : 'info'">
              {{ row.modelShow === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="apiHost" label="apiHost" min-width="180" />
        <el-table-column prop="updateTime" label="更新时间" width="180" align="center">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="removeRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
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
