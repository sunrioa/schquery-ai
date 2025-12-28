<template>
  <div class="knowledge-upload">
    <div class="header">
      <h2>知识库上传</h2>
      <p class="sub">
        支持PDF（自动OCR）与文本类文件（txt/md/csv/log）；其他格式可在“文档内容”中手动粘贴文本。
      </p>
    </div>

    <el-card class="card">
      <el-form :model="form" label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="不填则默认使用文件名" />
        </el-form-item>

        <el-form-item label="元数据">
          <el-input
            v-model="form.metadata"
            type="textarea"
            :rows="3"
            placeholder='可选，JSON字符串，例如：{"source":"manual","tags":["a","b"]}'
          />
        </el-form-item>

        <el-form-item label="文档内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            placeholder="可选：当文件不支持解析时，可在此粘贴纯文本作为入库内容（优先级高于文件解析）"
          />
        </el-form-item>

        <el-form-item label="文件">
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            :file-list="fileList"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">单文件上传；最大大小由后端配置限制</div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="uploading" @click="submitUpload">上传并入库</el-button>
          <el-button :disabled="!documentId" @click="refreshStatus">刷新状态</el-button>
          <el-button v-if="polling" type="warning" plain @click="stopPolling">停止轮询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="documentId" class="card status-card">
      <template #header>
        <div class="status-header">
          <span>处理状态</span>
          <el-tag :type="statusTagType">{{ statusText }}</el-tag>
        </div>
      </template>

      <div class="status-grid">
        <div class="item"><span class="k">文档ID</span><span class="v">{{ documentId }}</span></div>
        <div class="item"><span class="k">标题</span><span class="v">{{ status?.title || '-' }}</span></div>
        <div class="item"><span class="k">分块数</span><span class="v">{{ status?.chunkCount ?? '-' }}</span></div>
        <div class="item"><span class="k">更新时间</span><span class="v">{{ status?.updateTime || '-' }}</span></div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { getKnowledgeStatus, uploadKnowledgeFile } from '../../api/knowledge'

const form = ref({
  title: '',
  metadata: '',
  content: ''
})

const fileList = ref([])
const selectedFile = ref(null)
const uploading = ref(false)

const documentId = ref(null)
const status = ref(null)

const polling = ref(false)
let pollTimer = null

const onFileChange = (file, files) => {
  selectedFile.value = file.raw
  fileList.value = files.slice(-1)
}

const onFileRemove = () => {
  selectedFile.value = null
  fileList.value = []
}

const parseMetadata = () => {
  const raw = form.value.metadata?.trim()
  if (!raw) return null
  try {
    JSON.parse(raw)
    return raw
  } catch (e) {
    ElMessage.error('元数据必须是合法JSON字符串')
    return null
  }
}

const refreshStatus = async () => {
  if (!documentId.value) return
  try {
    const res = await getKnowledgeStatus(documentId.value)
    status.value = res.data
  } catch (e) {
    // request.js 已统一弹窗
  }
}

const startPolling = () => {
  if (!documentId.value) return
  stopPolling()
  polling.value = true
  pollTimer = setInterval(async () => {
    await refreshStatus()
    const ps = status.value?.processStatus
    if (ps === 2 || ps === 3) {
      stopPolling()
    }
  }, 2000)
}

const stopPolling = () => {
  polling.value = false
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onBeforeUnmount(() => stopPolling())

const submitUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.error('请选择要上传的文件')
    return
  }
  const metadata = parseMetadata()
  if (form.value.metadata?.trim() && metadata == null) return

  uploading.value = true
  try {
    const res = await uploadKnowledgeFile({
      title: form.value.title?.trim(),
      content: form.value.content?.trim(),
      metadata,
      file: selectedFile.value
    })
    documentId.value = res.data
    ElMessage.success(`上传成功，文档ID：${documentId.value}`)
    await refreshStatus()
    startPolling()
  } finally {
    uploading.value = false
  }
}

const statusText = computed(() => {
  const ps = status.value?.processStatus
  if (ps === 0) return '未处理'
  if (ps === 1) return '处理中'
  if (ps === 2) return '处理完成'
  if (ps === 3) return '处理失败'
  return '未知'
})

const statusTagType = computed(() => {
  const ps = status.value?.processStatus
  if (ps === 2) return 'success'
  if (ps === 3) return 'danger'
  if (ps === 1) return 'warning'
  return 'info'
})
</script>

<style scoped>
.knowledge-upload {
  padding: 20px;
  max-width: 1100px;
  margin: 0 auto;
}

.header h2 {
  margin: 0 0 6px 0;
}

.sub {
  margin: 0 0 16px 0;
  color: #666;
  font-size: 13px;
}

.card {
  margin-bottom: 16px;
}

.status-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
}

.item {
  display: flex;
  gap: 10px;
}

.k {
  width: 70px;
  color: #888;
}

.v {
  color: #333;
  word-break: break-all;
}

@media (max-width: 768px) {
  .status-grid {
    grid-template-columns: 1fr;
  }
}
</style>

