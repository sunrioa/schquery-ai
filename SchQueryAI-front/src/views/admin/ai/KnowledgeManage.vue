<template>
  <div class="admin-page ai-knowledge-container">
    <div class="page-header">
      <div class="header-info">
        <h2 class="title">RAG 知识库管理</h2>
        <p class="subtitle">管理知识库配置、分块策略以及检索参数</p>
      </div>
      <div class="header-ops">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新建知识库
        </el-button>
        <el-button @click="loadList">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <el-card class="search-card" shadow="never">
      <div class="search-wrapper">
        <div class="search-items">
          <el-input
            v-model="query.kname"
            placeholder="搜索知识库名称..."
            clearable
            style="width: 240px"
            @keyup.enter="loadList"
            @clear="loadList"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="query.status" placeholder="状态筛选" clearable style="width: 140px" @change="loadList">
            <el-option label="全部状态" :value="undefined" />
            <el-option label="已启用" :value="1" />
            <el-option label="已停用" :value="0" />
          </el-select>
        </div>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never" v-loading="loading">
      <el-table :data="list" stripe style="width: 100%" class="custom-table">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="kname" label="知识库名称" min-width="180">
          <template #default="{ row }">
            <div class="knowledge-name-cell">
              <span class="name-text">{{ row.kname }}</span>
              <div class="desc-text" v-if="row.description">{{ row.description }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" round>
              {{ row.status === 1 ? '使用中' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分块策略" align="center">
          <el-table-column prop="textBlockSize" label="Size" width="80" align="center" />
          <el-table-column prop="overlapChar" label="Overlap" width="80" align="center" />
        </el-table-column>
        <el-table-column label="检索配置" align="center">
          <el-table-column prop="retrieveLimit" label="topK" width="70" align="center" />
          <el-table-column prop="useRerank" label="Rerank" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.useRerank === 1 ? 'warning' : 'info'" size="small">
                {{ row.useRerank === 1 ? '开启' : '关闭' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table-column>
        <el-table-column prop="updateTime" label="最后更新" width="160" align="center">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.updateTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="goDetail(row)">
              <el-icon><Document /></el-icon>管理文档
            </el-button>
            <el-divider direction="vertical" />
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="removeRow(row)">删除</el-button>
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

    <el-dialog v-model="editorVisible" :title="editorTitle" width="760px">
      <el-form :model="editor" label-width="120px">
        <el-form-item label="知识库名称" required>
          <el-input v-model="editor.kname" placeholder="例如：研发知识库" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editor.description" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editor.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-divider content-position="left">分块参数</el-divider>
        <el-form-item label="textBlockSize">
          <el-input-number
            v-model="editor.textBlockSize"
            :min="100"
            :max="4000"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="overlapChar">
          <el-input-number
            v-model="editor.overlapChar"
            :min="0"
            :max="2000"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>

        <el-divider content-position="left">检索参数</el-divider>
        <el-form-item label="retrieveLimit(topK)">
          <el-input-number
            v-model="editor.retrieveLimit"
            :min="1"
            :max="50"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="candidateCount">
          <el-input-number
            v-model="editor.candidateCount"
            :min="1"
            :max="200"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="useRerank">
          <el-switch v-model="useRerankSwitch" />
        </el-form-item>
        <el-form-item v-if="useRerankSwitch" label="rerankModelName">
          <el-select
            v-model="editor.rerankModelName"
            filterable
            allow-create
            default-first-option
            placeholder="例如：bge-reranker-v2-m3"
            style="width: 100%"
          >
            <el-option v-for="m in rerankModelOptions" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="useRerankSwitch" label="minScore">
          <el-input-number
            v-model="editor.minScore"
            :min="0"
            :max="1"
            :step="0.01"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>

        <el-divider content-position="left">向量模型</el-divider>
        <el-form-item label="embeddingModelName">
          <el-select
            v-model="editor.embeddingModelName"
            filterable
            clearable
            allow-create
            default-first-option
            placeholder="不填则使用系统默认 embedding 模型"
            style="width: 100%"
          >
            <el-option v-for="m in embeddingModelOptions" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">提示词</el-divider>
        <el-form-item label="systemPrompt">
          <el-input v-model="editor.systemPrompt" type="textarea" :rows="4" placeholder="可选：知识库专用系统提示词" />
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
import { useRouter } from 'vue-router'
import { Search, Plus, Refresh, Document } from '@element-plus/icons-vue'
import { getKnowledgeList, removeKnowledge, saveKnowledge } from '../../../api/ai/knowledge'
import { getChatModelList } from '../../../api/ai/chatModel'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)

const query = ref({
  kname: '',
  status: undefined
})

const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const list = ref([])

const embeddingModelOptions = ref([])
const rerankModelOptions = ref([])

const loadList = async () => {
  loading.value = true
  try {
    const res = await getKnowledgeList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      kname: query.value.kname?.trim() || undefined,
      status: query.value.status
    })
    list.value = res?.data?.records || []
    total.value = res?.data?.total || 0
  } finally {
    loading.value = false
  }
}

const loadModelOptions = async () => {
  try {
    const [embeddingRes, rerankRes] = await Promise.all([
      getChatModelList({ pageNum: 1, pageSize: 200, category: 'vector', modelShow: 1 }),
      getChatModelList({ pageNum: 1, pageSize: 200, category: 'rerank', modelShow: 1 })
    ])
    const embeddingRecords = embeddingRes?.data?.records || []
    const rerankRecords = rerankRes?.data?.records || []
    embeddingModelOptions.value = embeddingRecords.map((r) => r.modelName).filter(Boolean)
    rerankModelOptions.value = rerankRecords.map((r) => r.modelName).filter(Boolean)
  } catch (e) {
    // 忽略：不阻塞知识库管理
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
const editorTitle = ref('新建知识库')
const editor = ref({
  id: null,
  kname: '',
  description: '',
  systemPrompt: '',
  textBlockSize: 600,
  overlapChar: 100,
  retrieveLimit: 6,
  candidateCount: 20,
  useRerank: 0,
  rerankModelName: '',
  minScore: null,
  vectorModelName: 'qdrant',
  embeddingModelName: '',
  status: 1
})

const useRerankSwitch = computed({
  get: () => Number(editor.value.useRerank) === 1,
  set: (val) => {
    editor.value.useRerank = val ? 1 : 0
    if (!val) {
      editor.value.rerankModelName = ''
      editor.value.minScore = null
    }
  }
})

const openCreate = () => {
  editorTitle.value = '新建知识库'
  editor.value = {
    id: null,
    kname: '',
    description: '',
    systemPrompt: '',
    textBlockSize: 600,
    overlapChar: 100,
    retrieveLimit: 6,
    candidateCount: 20,
    useRerank: 0,
    rerankModelName: '',
    minScore: null,
    vectorModelName: 'qdrant',
    embeddingModelName: '',
    status: 1
  }
  editorVisible.value = true
}

const openEdit = (row) => {
  editorTitle.value = `编辑知识库：${row.kname}`
  editor.value = { ...row }
  editorVisible.value = true
}

const save = async () => {
  if (!editor.value.kname || !editor.value.kname.trim()) {
    ElMessage.error('知识库名称不能为空')
    return
  }
  saving.value = true
  try {
    await saveKnowledge({
      ...editor.value,
      kname: editor.value.kname.trim()
    })
    ElMessage.success('保存成功')
    editorVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

const removeRow = async (row) => {
  await ElMessageBox.confirm(`确认删除知识库「${row.kname}」？该操作将同时删除其文档与向量数据。`, '提示', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await removeKnowledge(row.id)
  ElMessage.success('删除成功')
  loadList()
}

const goDetail = (row) => {
  router.push({
    path: `/admin/ai/knowledge/${row.id}`,
    query: { kname: row.kname || '' }
  })
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
  loadModelOptions()
  loadList()
})
</script>

<style scoped>
.ai-knowledge-container {
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

.knowledge-name-cell {
  display: flex;
  flex-direction: column;
}

.name-text {
  font-weight: 600;
  color: #303133;
}

.desc-text {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
