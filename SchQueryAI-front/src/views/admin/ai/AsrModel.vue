<template>
  <div class="admin-page ai-model-container">
    <div class="page-header">
      <div class="header-info">
        <h2 class="title">语音识别模型管理</h2>
        <p class="subtitle">配置语音识别服务的连接信息与识别参数</p>
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
          <el-table-column prop="endpoint" label="WebSocket 地址" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <code class="api-code">{{ row.endpoint || '-' }}</code>
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
                  <el-tooltip content="调用ASR服务时的模型名称" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.modelName" placeholder="例如：fun-asr-realtime-2025-11-07" />
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
                  <span>启用状态</span>
                  <el-tooltip content="关闭后将不参与识别链路" placement="top">
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
          <el-col :span="24">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>模型描述</span>
                  <el-tooltip content="描述模型用途和适用场景" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.modelDescribe" placeholder="简要描述该模型..." />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="form-section-title">连接配置</div>
        <el-row :gutter="24">
          <el-col :span="24">
            <el-form-item required>
              <template #label>
                <div class="form-label-container">
                  <span>WebSocket 地址</span>
                  <el-tooltip content="语音识别服务 WebSocket 地址" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.endpoint" placeholder="例如：wss://dashscope.aliyuncs.com/api-ws/v1/inference" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>API Key</span>
                  <el-tooltip content="用于鉴权的 API Key（默认使用 Bearer 方式）" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.apiKey" type="password" show-password placeholder="可选：sk-xxxx" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>Header 名称</span>
                  <el-tooltip content="自定义鉴权 Header 名称" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.authHeaderName" placeholder="默认：Authorization" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>Header 值</span>
                  <el-tooltip content="填写后将覆盖 API Key 的鉴权方式" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.authHeaderValue" type="password" show-password placeholder="例如：Bearer xxx" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>子协议</span>
                  <el-tooltip content="部分服务需要设置 WebSocket 子协议" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.subprotocol" placeholder="例如：funasr-ws" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>连接超时</span>
                  <el-tooltip content="WebSocket 连接超时（毫秒）" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input-number
                v-model="editor.connectTimeoutMs"
                :min="1000"
                :max="60000"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="form-section-title">识别参数</div>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>音频格式</span>
                  <el-tooltip content="上传到ASR服务的音频格式" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-select v-model="editor.format" placeholder="选择格式" style="width: 100%">
                <el-option label="PCM" value="pcm" />
                <el-option label="WAV" value="wav" />
                <el-option label="OPUS" value="opus" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>采样率</span>
                  <el-tooltip content="音频采样率（Hz）" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input-number v-model="editor.sampleRate" :min="8000" :max="48000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>中间结果</span>
                  <el-tooltip content="是否返回中间识别结果" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-switch v-model="editor.enableIntermediateResult" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>标点输出</span>
                  <el-tooltip content="是否自动补全标点" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-switch v-model="editor.enablePunctuation" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>数字转换</span>
                  <el-tooltip content="是否启用数字/文本归一化" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-switch v-model="editor.enableInverseTextNormalization" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>Header/Payload</span>
                  <el-tooltip content="是否使用 header/payload 结构发送配置" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-switch v-model="editor.useHeaderPayload" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>分片字节数</span>
                  <el-tooltip content="单次发送的音频分片大小" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input-number v-model="editor.chunkBytes" :min="160" :max="4096" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>分片间隔</span>
                  <el-tooltip content="发送分片的时间间隔（毫秒）" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input-number v-model="editor.chunkIntervalMs" :min="0" :max="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <template #label>
                <div class="form-label-container">
                  <span>热词配置</span>
                  <el-tooltip content="JSON 字符串或关键词列表" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-input v-model="editor.hotWords" type="textarea" :rows="2" placeholder='{"关键词":20}' />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="form-section-title">高级设置</div>
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
import { getAsrModelList, removeAsrModel, saveAsrModel } from '../../../api/ai/asrModel'

const isMobile = ref(false)
const updateIsMobile = () => {
  isMobile.value = window.innerWidth <= 768
}

const loading = ref(false)
const saving = ref(false)

const query = ref({
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
    const res = await getAsrModelList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      modelShow: query.value.modelShow,
      modelName: query.value.modelName?.trim() || undefined
    })
    list.value = res?.data?.records || []
    total.value = res?.data?.total || 0
  } catch (error) {
    console.error('获取语音识别配置失败:', error)
    ElMessage.error(error?.msg || '获取语音识别配置失败')
    list.value = []
    total.value = 0
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
const editorTitle = ref('新建配置')
const editor = ref({
  id: null,
  modelName: '',
  providerName: '',
  modelDescribe: '',
  modelShow: 1,
  endpoint: '',
  apiKey: '',
  authHeaderName: 'Authorization',
  authHeaderValue: '',
  subprotocol: '',
  format: 'pcm',
  sampleRate: 16000,
  enableIntermediateResult: true,
  enablePunctuation: true,
  enableInverseTextNormalization: true,
  hotWords: '',
  useHeaderPayload: true,
  chunkBytes: 960,
  chunkIntervalMs: 10,
  connectTimeoutMs: 10000,
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
  editorTitle.value = '新建配置'
  editor.value = {
    id: null,
    modelName: '',
    providerName: '',
    modelDescribe: '',
    modelShow: 1,
    endpoint: '',
    apiKey: '',
    authHeaderName: 'Authorization',
    authHeaderValue: '',
    subprotocol: '',
    format: 'pcm',
    sampleRate: 16000,
    enableIntermediateResult: true,
    enablePunctuation: true,
    enableInverseTextNormalization: true,
    hotWords: '',
    useHeaderPayload: true,
    chunkBytes: 960,
    chunkIntervalMs: 10,
    connectTimeoutMs: 10000,
    priority: 1,
    remark: ''
  }
  editorVisible.value = true
}

const openEdit = (row) => {
  editorTitle.value = `编辑配置：${row.modelName}`
  editor.value = {
    ...row,
    authHeaderName: row.authHeaderName || 'Authorization',
    format: row.format || 'pcm',
    sampleRate: row.sampleRate || 16000,
    enableIntermediateResult: row.enableIntermediateResult !== false,
    enablePunctuation: row.enablePunctuation !== false,
    enableInverseTextNormalization: row.enableInverseTextNormalization !== false,
    useHeaderPayload: row.useHeaderPayload !== false,
    chunkBytes: row.chunkBytes || 960,
    chunkIntervalMs: row.chunkIntervalMs ?? 10,
    connectTimeoutMs: row.connectTimeoutMs || 10000
  }
  editorVisible.value = true
}

const save = async () => {
  if (!editor.value.modelName || !editor.value.modelName.trim()) {
    ElMessage.error('modelName不能为空')
    return
  }
  if (Number(editor.value.modelShow) === 1 && !editor.value.endpoint) {
    ElMessage.error('启用状态下必须填写 WebSocket 地址')
    return
  }
  saving.value = true
  try {
    await saveAsrModel({
      ...editor.value,
      modelName: editor.value.modelName.trim()
    })
    ElMessage.success('保存成功')
    editorVisible.value = false
    loadList()
  } catch (error) {
    console.error('保存语音识别配置失败:', error)
    ElMessage.error(error?.msg || '保存配置失败')
  } finally {
    saving.value = false
  }
}

const removeRow = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除配置「${row.modelName}」？`, '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await removeAsrModel(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    console.error('删除语音识别配置失败:', error)
    ElMessage.error(error?.msg || '删除配置失败')
  }
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

.search-select-sm {
  width: 120px;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.table-scroll :deep(.el-table) {
  min-width: 960px;
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
}

.time-text {
  color: var(--app-muted);
  font-size: 12px;
}

.form-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text);
  margin: 8px 0 12px;
}

.form-label-container {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.help-icon {
  font-size: 14px;
  color: var(--app-muted);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .search-items {
    flex-direction: column;
    align-items: stretch;
    width: 100%;
  }

  .search-input,
  .search-select-sm {
    width: 100%;
  }
}
</style>
