<template>
  <div class="admin-page ai-knowledge-detail">
    <div class="page-header">
      <div class="header-left">
        <el-button @click="goBack">返回</el-button>
        <div class="title">
          <h2>{{ headerTitle }}</h2>
          <p class="sub">管理文档上传、内容重建、片段编辑与重索引</p>
        </div>
      </div>

      <div class="header-actions">
        <el-button type="primary" @click="openUpload">上传文档</el-button>
        <el-button @click="refreshAll">刷新</el-button>
      </div>
    </div>

    <el-card class="table-card" shadow="never" v-loading="loading">
      <div class="table-scroll">
        <el-table :data="docs" stripe style="width: 100%">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="id" label="文档ID" width="100" align="center" />
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column prop="fileType" label="类型" width="90" align="center" />
          <el-table-column prop="processStatus" label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="getProcessTag(row.processStatus)">
                {{ getProcessText(row.processStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="180" align="center">
            <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="360" align="center" :fixed="isMobile ? undefined : 'right'">
            <template #default="{ row }">
              <div class="op-actions">
                <el-button size="small" @click="openDocInfo(row)">详情/重建</el-button>
                <el-button size="small" type="primary" @click="openFragments(row)">片段</el-button>
                <el-button size="small" type="success" :loading="reindexingId === row.id" @click="reindex(row)">
                  重索引
                </el-button>
                <el-button size="small" type="danger" @click="removeDoc(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 上传文档 -->
    <el-dialog v-model="uploadVisible" title="上传文档" :width="isMobile ? '94vw' : '760px'">
      <el-form :model="uploadForm" label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="uploadForm.title" placeholder="不填则默认使用文件名" />
        </el-form-item>
        <el-form-item label="元数据">
          <el-input
            v-model="uploadForm.metadata"
            type="textarea"
            :rows="3"
            placeholder='可选，JSON字符串，例如：{"source":"manual","tags":["a","b"]}'
          />
        </el-form-item>
        <el-form-item label="文档内容">
          <el-input
            v-model="uploadForm.content"
            type="textarea"
            :rows="5"
            placeholder="可选：当文件不支持解析时，可在此粘贴纯文本作为入库内容（优先级高于文件解析）"
          />
        </el-form-item>
        <el-form-item label="文件" required>
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            :file-list="uploadFileList"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
          >
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
      </template>
    </el-dialog>

    <!-- 文档详情/重建 -->
    <el-dialog v-model="docVisible" title="文档详情 / 重建索引" :width="isMobile ? '94vw' : '920px'">
      <div v-loading="docLoading">
        <el-descriptions v-if="docInfo" :column="isMobile ? 1 : 2" border style="margin-bottom: 12px">
          <el-descriptions-item label="文档ID">{{ docInfo.documentId }}</el-descriptions-item>
          <el-descriptions-item label="知识库ID">{{ docInfo.knowledgeId }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="isMobile ? 1 : 2">{{ docInfo.title }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getProcessTag(docInfo.processStatus)">
              {{ getProcessText(docInfo.processStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="分块数">{{ docInfo.chunkCount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(docInfo.updateTime) }}</el-descriptions-item>
        </el-descriptions>

        <el-form label-width="90px">
          <el-form-item label="内容">
            <el-input v-model="docContent" type="textarea" :rows="14" placeholder="编辑后点击“重建”将重新分块并写入向量库" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="docVisible = false">关闭</el-button>
        <el-button type="success" :loading="rebuilding" @click="rebuild">重建</el-button>
      </template>
    </el-dialog>

    <!-- 片段列表 -->
    <el-drawer v-model="fragVisible" :title="fragTitle" :size="isMobile ? '100%' : '60%'">
      <div v-loading="fragLoading" class="frag-body">
        <div class="table-scroll">
          <el-table :data="fragments" stripe style="width: 100%">
          <el-table-column prop="chunkIndex" label="#" width="70" align="center" />
          <el-table-column prop="id" label="片段ID" width="90" align="center" />
          <el-table-column prop="chunkContent" label="内容" min-width="260">
            <template #default="{ row }">
              <span class="frag-preview">{{ row.chunkContent }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="chunkLength" label="长度" width="90" align="center" />
          <el-table-column label="操作" width="160" align="center" :fixed="isMobile ? undefined : 'right'">
            <template #default="{ row }">
              <el-button size="small" @click="openFragEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeFragment(row)">删除</el-button>
            </template>
          </el-table-column>
          </el-table>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="fragEditVisible" title="编辑片段内容" :width="isMobile ? '94vw' : '900px'">
      <el-form label-width="90px">
        <el-form-item label="片段ID">
          <el-input v-model="fragEditor.id" disabled />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="fragEditor.content" type="textarea" :rows="16" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fragEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="fragSaving" @click="saveFragment">保存并同步向量</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getKnowledgeAttachInfo,
  getKnowledgeAttachList,
  getKnowledgeDetail,
  getKnowledgeFragmentList,
  reindexKnowledgeAttach,
  rebuildKnowledgeAttach,
  removeKnowledgeAttach,
  removeKnowledgeFragment,
  updateKnowledgeFragmentContent,
  uploadKnowledgeAttach
} from '../../../api/ai/knowledge'

const route = useRoute()
const router = useRouter()

const isMobile = ref(false)
const updateIsMobile = () => {
  isMobile.value = window.innerWidth <= 768
}

const knowledgeId = computed(() => route.params.id)
const headerTitle = computed(() => {
  const kname = route.query.kname
  return kname ? `知识库：${kname}` : `知识库ID：${knowledgeId.value}`
})

const loading = ref(false)
const knowledge = ref(null)
const docs = ref([])

// 分页状态
const pagination = ref({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const loadKnowledge = async () => {
  if (!knowledgeId.value) return
  const res = await getKnowledgeDetail(knowledgeId.value)
  knowledge.value = res?.data || null
}

const loadDocs = async () => {
  if (!knowledgeId.value) return
  loading.value = true
  try {
    const res = await getKnowledgeAttachList(knowledgeId.value, {
      pageNum: pagination.value.pageNum,
      pageSize: pagination.value.pageSize
    })
    docs.value = res?.data?.records || res?.data || []
    pagination.value.total = res?.data?.total || 0
  } finally {
    loading.value = false
  }
}

// 分页大小变化
const handleSizeChange = (size) => {
  pagination.value.pageSize = size
  pagination.value.pageNum = 1
  loadDocs()
}

// 页码变化
const handlePageChange = (page) => {
  pagination.value.pageNum = page
  loadDocs()
}

const refreshAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadKnowledge(), loadDocs()])
  } finally {
    loading.value = false
  }
}

watch(
  () => knowledgeId.value,
  () => refreshAll(),
  { immediate: true }
)

onMounted(() => {
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateIsMobile)
})

const goBack = () => {
  router.push('/admin/ai/knowledge')
}

// 上传
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadForm = ref({
  title: '',
  metadata: '',
  content: ''
})
const uploadFileList = ref([])
const uploadFileRaw = ref(null)

const openUpload = () => {
  uploadForm.value = { title: '', metadata: '', content: '' }
  uploadFileList.value = []
  uploadFileRaw.value = null
  uploadVisible.value = true
}

const onFileChange = (file, files) => {
  uploadFileRaw.value = file.raw
  uploadFileList.value = files.slice(-1)
}

const onFileRemove = () => {
  uploadFileRaw.value = null
  uploadFileList.value = []
}

const validateJson = (raw) => {
  const t = raw?.trim()
  if (!t) return ''
  try {
    JSON.parse(t)
    return t
  } catch (e) {
    ElMessage.error('元数据必须是合法JSON字符串')
    return null
  }
}

const submitUpload = async () => {
  if (!uploadFileRaw.value) {
    ElMessage.error('请选择要上传的文件')
    return
  }
  const metadata = validateJson(uploadForm.value.metadata)
  if (uploadForm.value.metadata?.trim() && metadata == null) return

  uploading.value = true
  try {
    await uploadKnowledgeAttach({
      knowledgeId: knowledgeId.value,
      title: uploadForm.value.title?.trim(),
      content: uploadForm.value.content?.trim(),
      metadata: metadata || undefined,
      file: uploadFileRaw.value
    })
    ElMessage.success('上传成功')
    uploadVisible.value = false
    loadDocs()
  } finally {
    uploading.value = false
  }
}

// 文档详情/重建
const docVisible = ref(false)
const docLoading = ref(false)
const rebuilding = ref(false)
const docInfo = ref(null)
const docContent = ref('')

const openDocInfo = async (row) => {
  docVisible.value = true
  docLoading.value = true
  try {
    const res = await getKnowledgeAttachInfo(row.id)
    docInfo.value = res?.data || null
    docContent.value = docInfo.value?.content || ''
  } finally {
    docLoading.value = false
  }
}

const rebuild = async () => {
  if (!docInfo.value?.documentId) return
  if (!docContent.value || !docContent.value.trim()) {
    ElMessage.error('内容不能为空')
    return
  }
  try {
    await ElMessageBox.confirm('确认重建该文档索引？将删除旧向量与分块并重新生成。', '提示', {
      confirmButtonText: '重建',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  rebuilding.value = true
  try {
    try {
      await rebuildKnowledgeAttach({ docId: docInfo.value.documentId, content: docContent.value })
      ElMessage.success('重建成功')
      docVisible.value = false
      loadDocs()
    } catch {
      // 错误提示已由请求拦截器统一处理，避免未捕获 Promise 导致 dev overlay
    }
  } finally {
    rebuilding.value = false
  }
}

// 重索引
const reindexingId = ref(null)
const reindex = async (row) => {
  try {
    await ElMessageBox.confirm('确认重索引？将基于当前片段内容重算向量（不重新切分）。', '提示', {
      confirmButtonText: '重索引',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  reindexingId.value = row.id
  try {
    try {
      await reindexKnowledgeAttach(row.id)
      ElMessage.success('重索引完成')
      loadDocs()
    } catch {
      // 错误提示已由请求拦截器统一处理，避免未捕获 Promise 导致 dev overlay
    }
  } finally {
    reindexingId.value = null
  }
}

// 删除
const removeDoc = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除文档「${row.title || row.id}」？将同时删除其向量数据。`, '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await removeKnowledgeAttach(row.id)
    ElMessage.success('删除成功')
    loadDocs()
  } catch {
    // 错误提示已由请求拦截器统一处理，避免未捕获 Promise 导致 dev overlay
  }
}

// 片段
const fragVisible = ref(false)
const fragLoading = ref(false)
const fragments = ref([])
const currentDocId = ref(null)
const fragTitle = computed(() => (currentDocId.value ? `片段列表（文档ID：${currentDocId.value}）` : '片段列表'))

const openFragments = async (row) => {
  currentDocId.value = row.id
  fragVisible.value = true
  await loadFragments()
}

const loadFragments = async () => {
  if (!currentDocId.value) return
  fragLoading.value = true
  try {
    const res = await getKnowledgeFragmentList(currentDocId.value)
    fragments.value = res?.data || []
  } finally {
    fragLoading.value = false
  }
}

const refreshDocInfoIfOpen = async () => {
  if (!docVisible.value) return
  if (!docInfo.value?.documentId) return
  if (!currentDocId.value || docInfo.value.documentId !== currentDocId.value) return

  const baseline = docInfo.value?.content || ''
  const shouldUpdateDocContent = docContent.value === baseline
  try {
    const res = await getKnowledgeAttachInfo(currentDocId.value)
    docInfo.value = res?.data || null
    if (shouldUpdateDocContent) {
      docContent.value = docInfo.value?.content || ''
    }
  } catch {
    // 错误提示已由请求拦截器统一处理，避免未捕获 Promise 导致 dev overlay
  }
}

const fragEditVisible = ref(false)
const fragSaving = ref(false)
const fragEditor = ref({ id: null, content: '' })

const openFragEdit = (row) => {
  fragEditor.value = { id: row.id, content: row.chunkContent || '' }
  fragEditVisible.value = true
}

const saveFragment = async () => {
  if (!fragEditor.value.id) return
  if (!fragEditor.value.content || !fragEditor.value.content.trim()) {
    ElMessage.error('内容不能为空')
    return
  }
  fragSaving.value = true
  try {
    try {
      await updateKnowledgeFragmentContent({ id: fragEditor.value.id, content: fragEditor.value.content })
      ElMessage.success('保存成功')
      fragEditVisible.value = false
      await loadFragments()
      await refreshDocInfoIfOpen()
    } catch {
      // 错误提示已由请求拦截器统一处理，避免未捕获 Promise 导致 dev overlay
    }
  } finally {
    fragSaving.value = false
  }
}

const removeFragment = async (row) => {
  if (!row?.id) return
  try {
    await ElMessageBox.confirm(`确认删除该片段（#${row.chunkIndex ?? '-'}，ID：${row.id}）？将同时删除其向量数据。`, '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await removeKnowledgeFragment(row.id)
    ElMessage.success('删除成功')
    loadFragments()
  } catch {
    // 错误提示已由请求拦截器统一处理，避免未捕获 Promise 导致 dev overlay
  }
}

const getProcessText = (ps) => {
  if (ps === 0) return '未处理'
  if (ps === 1) return '处理中'
  if (ps === 2) return '处理完成'
  if (ps === 3) return '处理失败'
  return '未知'
}

const getProcessTag = (ps) => {
  if (ps === 2) return 'success'
  if (ps === 3) return 'danger'
  if (ps === 1) return 'warning'
  return 'info'
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
</script>

<style scoped>
.frag-body {
  padding: 4px 0;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.table-scroll :deep(.el-table) {
  min-width: 760px;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.op-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
}

.frag-preview {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
