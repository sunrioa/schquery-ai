<template>
  <div class="crawler-manager">
    <el-card header="爬虫管理">
      <div class="toolbar">
        <el-button
          type="primary"
          :loading="status.running"
          :disabled="status.running"
          @click="startCrawl"
        >
          <el-icon><VideoPlay /></el-icon>
          启动爬虫
        </el-button>
        <el-button
          type="danger"
          :disabled="!status.running"
          @click="stopCrawl"
        >
          <el-icon><VideoPause /></el-icon>
          停止爬虫
        </el-button>
        <el-button @click="refreshStatus">
          <el-icon><Refresh /></el-icon>
          刷新状态
        </el-button>
      </div>

      <el-alert
        title="可选：输入本次起始页面URL（支持完整URL），系统会自动递归抓取同域页面并在下方生成每页一条草稿。"
        type="info"
        show-icon
        :closable="false"
        class="start-alert"
      />

      <el-row :gutter="16" class="start-options">
        <el-col :xs="24" :md="16">
          <el-input
            v-model="runForm.startUrl"
            placeholder="本次起始页面URL（留空则使用配置中的基础URL + 起始URL）"
            clearable
          />
        </el-col>
        <el-col :xs="24" :md="8">
          <el-input-number
            v-model="runForm.requestIntervalMs"
            :min="0"
            :max="60000"
            :step="100"
            controls-position="right"
            style="width: 100%;"
          />
        </el-col>
      </el-row>

      <el-row :gutter="20" class="status-panel">
        <el-col :span="6">
          <el-statistic title="已访问页面" :value="status.visitedCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="待处理队列" :value="status.pendingCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="抓取草稿" :value="status.draftCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="运行状态">
            <template #default>
              <el-tag :type="status.running ? 'success' : 'info'">
                {{ status.running ? '运行中' : '已停止' }}
              </el-tag>
            </template>
          </el-statistic>
        </el-col>
      </el-row>

      <el-divider content-position="left">爬虫配置</el-divider>
      <el-form :model="configForm" label-width="150px">
        <el-form-item label="启用爬虫">
          <el-switch v-model="configForm.enabled" />
        </el-form-item>
        <el-form-item label="基础URL">
          <el-input v-model="configForm.baseUrl" placeholder="https://www.gzmtu.edu.cn" />
        </el-form-item>
        <el-form-item label="起始URL">
          <el-input v-model="configForm.startUrl" placeholder="/index.htm" />
        </el-form-item>
        <el-form-item label="最大页面数">
          <el-input-number v-model="configForm.maxPages" :min="100" :max="10000" />
        </el-form-item>
        <el-form-item label="内容最小长度">
          <el-input-number v-model="configForm.contentMinLength" :min="0" :max="10000" />
        </el-form-item>
        <el-form-item label="内容最大长度">
          <el-input-number v-model="configForm.contentMaxLength" :min="100" :max="200000" />
        </el-form-item>
        <el-form-item label="抓取间隔(ms)">
          <el-input-number v-model="configForm.requestIntervalMs" :min="0" :max="60000" :step="100" />
          <span class="config-tip">用于控制每个页面抓取的等待时间，避免访问频率过高</span>
        </el-form-item>
        <el-form-item label="强校验向量存在">
          <el-switch v-model="configForm.strictVectorCheckEnabled" />
          <span class="config-tip">开启后会额外查询向量库确认点位存在，准确但更慢</span>
        </el-form-item>
        <el-form-item label="知识库">
          <el-select
            v-model="configForm.knowledgeId"
            placeholder="请选择知识库"
            filterable
            clearable
            style="width: 100%;"
          >
            <el-option
              v-for="item in knowledgeList"
              :key="item.id"
              :label="item.kname"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="启用定时任务">
          <el-switch v-model="configForm.scheduleEnabled" />
        </el-form-item>
        <el-form-item label="定时任务Cron">
          <CronSelect v-model="configForm.scheduleCron" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveConfig">保存配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="result-card" header="抓取草稿（可编辑后再保存入库并向量化）">
      <div class="result-toolbar">
        <div class="result-toolbar-left">
          <el-button @click="refreshResults">
            <el-icon><Refresh /></el-icon>
            刷新草稿
          </el-button>
          <el-button
            type="warning"
            :disabled="selectedResultIds.length === 0"
            :loading="savingSelected"
            @click="saveSelectedResults"
          >
            保存选中并向量化
          </el-button>
          <el-button
            type="danger"
            plain
            :disabled="crawlerResults.length === 0"
            :loading="clearingResults"
            @click="clearResults"
          >
            清空草稿
          </el-button>
        </div>
        <span class="result-summary">共 {{ crawlerResults.length }} 条，已选 {{ selectedResultIds.length }} 条</span>
      </div>

      <el-table
        v-loading="loadingResults"
        :data="crawlerResults"
        row-key="id"
        stripe
        style="width: 100%"
        @selection-change="onResultSelectionChange"
      >
        <el-table-column type="selection" width="55" :selectable="isResultSelectable" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="url" label="来源链接" min-width="260" show-overflow-tooltip />
        <el-table-column prop="contentLength" label="内容长度" width="100" align="center" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.saved ? 'success' : 'warning'">
              {{ row.saved ? '已提交入库' : '待保存' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="抓取时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatTime(row.fetchTime) }}
          </template>
        </el-table-column>
        <el-table-column label="内容预览" min-width="300">
          <template #default="{ row }">
            <div class="content-preview">{{ row.content }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openEditor(row)">
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="editorVisible" title="编辑抓取草稿" width="880px">
      <el-form label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="editingResult.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="来源URL">
          <el-input v-model="editingResult.url" disabled />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="editingResult.content"
            type="textarea"
            :rows="16"
            placeholder="可在此修改爬取内容，保存后再勾选入库"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingEditor" @click="saveEditedResult">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import {
  clearCrawlerResultsApi,
  getCrawlerResultsApi,
  getCrawlerStatusApi,
  saveCrawlerResultsApi,
  startCrawlApi,
  stopCrawlApi,
  updateCrawlerConfigApi,
  updateCrawlerResultApi
} from '@/api/ai/crawler'
import { getKnowledgeList } from '@/api/ai/knowledge'
import { ElMessage, ElMessageBox } from 'element-plus'
import CronSelect from '@/components/CronSelect.vue'

const status = reactive({
  running: false,
  visitedCount: 0,
  pendingCount: 0,
  draftCount: 0
})

const knowledgeList = ref([])

const defaultConfig = {
  enabled: true,
  baseUrl: 'https://www.gzmtu.edu.cn',
  startUrl: '/index.htm',
  maxPages: 5000,
  contentMinLength: 100,
  contentMaxLength: 10000,
  requestIntervalMs: 1000,
  strictVectorCheckEnabled: false,
  knowledgeId: null,
  scheduleEnabled: true,
  scheduleCron: '0 0 2 ? * MON'
}

const config = reactive({ ...defaultConfig })
const configForm = reactive({ ...defaultConfig })
const runForm = reactive({
  startUrl: '',
  requestIntervalMs: defaultConfig.requestIntervalMs
})

const crawlerResults = ref([])
const loadingResults = ref(false)
const selectedResultIds = ref([])
const savingSelected = ref(false)
const clearingResults = ref(false)

const editorVisible = ref(false)
const savingEditor = ref(false)
const editingResult = reactive({
  id: '',
  title: '',
  url: '',
  content: ''
})

let pollTimer = null

const applyConfig = (target, source) => {
  target.enabled = source?.enabled ?? defaultConfig.enabled
  target.baseUrl = source?.baseUrl ?? defaultConfig.baseUrl
  target.startUrl = source?.startUrl ?? defaultConfig.startUrl
  target.maxPages = source?.maxPages ?? defaultConfig.maxPages
  target.contentMinLength = source?.contentMinLength ?? defaultConfig.contentMinLength
  target.contentMaxLength = source?.contentMaxLength ?? defaultConfig.contentMaxLength
  target.requestIntervalMs = source?.requestIntervalMs ?? defaultConfig.requestIntervalMs
  target.strictVectorCheckEnabled = source?.strictVectorCheckEnabled ?? defaultConfig.strictVectorCheckEnabled
  target.knowledgeId = source?.knowledgeId ?? defaultConfig.knowledgeId
  target.scheduleEnabled = source?.scheduleEnabled ?? defaultConfig.scheduleEnabled
  target.scheduleCron = source?.scheduleCron ?? defaultConfig.scheduleCron
}

const persistConfig = async (showMessage = true) => {
  const payloads = [
    { key: 'enabled', value: configForm.enabled },
    { key: 'base.url', value: configForm.baseUrl },
    { key: 'start.url', value: configForm.startUrl },
    { key: 'max.pages', value: configForm.maxPages },
    { key: 'content.min.length', value: configForm.contentMinLength },
    { key: 'content.max.length', value: configForm.contentMaxLength },
    { key: 'request.interval.ms', value: configForm.requestIntervalMs },
    { key: 'strict.vector.check.enabled', value: configForm.strictVectorCheckEnabled },
    { key: 'knowledge.id', value: configForm.knowledgeId },
    { key: 'schedule.enabled', value: configForm.scheduleEnabled },
    { key: 'schedule.cron', value: configForm.scheduleCron }
  ]

  for (const payload of payloads) {
    await updateCrawlerConfigApi(payload.key, payload.value)
  }
  applyConfig(config, configForm)
  if (showMessage) {
    ElMessage.success('配置已保存')
  }
}

const startCrawl = async () => {
  if (!runForm.startUrl && (!configForm.baseUrl || !configForm.baseUrl.trim() || !configForm.startUrl || !configForm.startUrl.trim())) {
    ElMessage.error('请先填写基础URL和起始URL')
    return
  }
  try {
    await persistConfig(false)
    const res = await startCrawlApi({
      startUrl: runForm.startUrl?.trim() || null,
      requestIntervalMs: runForm.requestIntervalMs
    })
    ElMessage.success(res.data || '爬虫任务已启动')
    await Promise.all([fetchStatus(), fetchResults()])
  } catch (error) {
    ElMessage.error(error?.msg || '启动请求失败')
  }
}

const stopCrawl = async () => {
  try {
    const res = await stopCrawlApi()
    ElMessage.success(res.data || '爬虫已停止')
    await fetchStatus()
  } catch (error) {
    ElMessage.error(error?.msg || '停止请求失败')
  }
}

const refreshStatus = async () => {
  await fetchStatus(true)
  ElMessage.success('状态已刷新')
}

const fetchStatus = async (syncForm = false) => {
  const res = await getCrawlerStatusApi()
  const data = res?.data || {}
  status.running = !!data.running
  status.visitedCount = Number(data.visitedCount || 0)
  status.pendingCount = Number(data.pendingCount || 0)
  status.draftCount = Number(data.draftCount || 0)
  applyConfig(config, data.config || {})
  if (syncForm) {
    applyConfig(configForm, data.config || {})
    runForm.requestIntervalMs = configForm.requestIntervalMs
  }
}

const saveConfig = async () => {
  try {
    await persistConfig(true)
  } catch (error) {
    ElMessage.error(error?.msg || '保存配置失败')
  }
}

const fetchKnowledgeList = async () => {
  try {
    const res = await getKnowledgeList({ status: 1, pageSize: 1000 })
    knowledgeList.value = res?.data?.records || []
  } catch (error) {
    ElMessage.error(error?.msg || '获取知识库列表失败')
  }
}

const fetchResults = async () => {
  loadingResults.value = true
  try {
    const res = await getCrawlerResultsApi()
    crawlerResults.value = res?.data || []
    const validIds = new Set(crawlerResults.value.map(item => item.id))
    selectedResultIds.value = selectedResultIds.value.filter(id => validIds.has(id))
  } finally {
    loadingResults.value = false
  }
}

const refreshResults = async () => {
  await fetchResults()
  await fetchStatus()
  ElMessage.success('草稿已刷新')
}

const onResultSelectionChange = (rows) => {
  selectedResultIds.value = rows.map(item => item.id)
}

const isResultSelectable = (row) => !row?.saved

const openEditor = (row) => {
  editingResult.id = row.id
  editingResult.title = row.title || ''
  editingResult.url = row.url || ''
  editingResult.content = row.content || ''
  editorVisible.value = true
}

const saveEditedResult = async () => {
  if (!editingResult.id) {
    return
  }
  if (!editingResult.content || !editingResult.content.trim()) {
    ElMessage.error('内容不能为空')
    return
  }

  savingEditor.value = true
  try {
    const res = await updateCrawlerResultApi({
      id: editingResult.id,
      title: editingResult.title,
      content: editingResult.content
    })

    const updated = res?.data
    if (updated?.id) {
      const idx = crawlerResults.value.findIndex(item => item.id === updated.id)
      if (idx >= 0) {
        crawlerResults.value[idx] = updated
      }
    }
    editorVisible.value = false
    ElMessage.success('草稿已更新')
  } finally {
    savingEditor.value = false
  }
}

const saveSelectedResults = async () => {
  if (selectedResultIds.value.length === 0) {
    ElMessage.warning('请先勾选要保存的草稿')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认保存选中的 ${selectedResultIds.value.length} 条草稿并触发向量化？`,
      '确认保存',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  savingSelected.value = true
  try {
    const res = await saveCrawlerResultsApi({
      ids: selectedResultIds.value,
      knowledgeId: configForm.knowledgeId
    })
    const result = res?.data || {}
    ElMessage.success(`已提交 ${result.submittedCount || 0} 条，跳过 ${result.skippedCount || 0} 条`)
    selectedResultIds.value = []
    await Promise.all([fetchResults(), fetchStatus()])
  } finally {
    savingSelected.value = false
  }
}

const clearResults = async () => {
  try {
    await ElMessageBox.confirm(
      '确认清空当前所有抓取草稿？清空后不可恢复。',
      '确认清空',
      {
        confirmButtonText: '清空',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  clearingResults.value = true
  try {
    await clearCrawlerResultsApi()
    selectedResultIds.value = []
    await Promise.all([fetchResults(), fetchStatus()])
    ElMessage.success('草稿已清空')
  } finally {
    clearingResults.value = false
  }
}

const formatTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (num) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const startPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
  pollTimer = setInterval(async () => {
    if (!status.running) {
      return
    }
    try {
      await Promise.all([fetchStatus(), fetchResults()])
    } catch (e) {
      // 忽略轮询异常，避免打断页面
    }
  }, 5000)
}

onMounted(async () => {
  await Promise.all([fetchKnowledgeList(), fetchStatus(true), fetchResults()])
  startPolling()
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.crawler-manager {
  padding: 20px;
}

.toolbar {
  margin-bottom: 20px;
}

.start-alert {
  margin-bottom: 12px;
}

.start-options {
  margin-bottom: 18px;
}

.status-panel {
  margin-bottom: 20px;
}

.config-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

.result-card {
  margin-top: 20px;
}

.result-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 12px;
  flex-wrap: wrap;
}

.result-toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.result-summary {
  color: #606266;
  font-size: 13px;
}

.content-preview {
  display: -webkit-box;
  line-height: 1.4;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}
</style>
