<template>
  <div class="admin-page ai-knowledge-manage">
    <div class="page-header">
      <div class="header-left">
        <h2>知识库管理</h2>
        <p class="sub">创建/配置知识库，并进入文档与片段管理</p>
      </div>

      <div class="header-actions">
        <el-input
          v-model="query.kname"
          placeholder="搜索知识库名称"
          clearable
          style="width: 220px"
          @keyup.enter="loadList"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="loadList">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="openCreate">新建</el-button>
        <el-button @click="loadList">刷新</el-button>
      </div>
    </div>

    <el-card class="table-card" v-loading="loading">
      <el-table :data="list" stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="id" label="ID" width="90" align="center" />
        <el-table-column prop="kname" label="名称" min-width="160" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retrieveLimit" label="topK" width="80" align="center" />
        <el-table-column prop="candidateCount" label="候选" width="80" align="center" />
        <el-table-column prop="textBlockSize" label="块大小" width="90" align="center" />
        <el-table-column prop="overlapChar" label="重叠" width="80" align="center" />
        <el-table-column prop="useRerank" label="Rerank" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.useRerank === 1 ? 'warning' : 'info'">
              {{ row.useRerank === 1 ? '启用' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="180" align="center">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="goDetail(row)">文档</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="removeRow(row)">删除</el-button>
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
.table-card {
  margin-bottom: 16px;
}
</style>
