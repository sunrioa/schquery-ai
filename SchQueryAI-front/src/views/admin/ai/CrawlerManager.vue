<template>
  <div class="admin-page ai-crawler-manage">
    <div class="page-header">
      <div class="header-left">
        <h2>爬虫管理</h2>
        <p class="sub">统一控制抓取任务、调度策略与草稿入库流程</p>
      </div>
      <div class="header-actions">
        <el-button
          class="action-btn"
          type="primary"
          :loading="startingCrawler"
          :disabled="startButtonDisabled"
          @click="startCrawl"
        >
          <el-icon><VideoPlay /></el-icon>
          {{ startButtonText }}
        </el-button>
        <el-button
          class="action-btn"
          type="danger"
          :loading="stoppingCrawler"
          :disabled="!canStopCrawler"
          @click="stopCrawl"
        >
          <el-icon><VideoPause /></el-icon>
          停止爬虫
        </el-button>
        <el-button class="action-btn" @click="refreshStatus">
          <el-icon><Refresh /></el-icon>
          刷新状态
        </el-button>
      </div>
    </div>

    <el-card class="control-card" shadow="never">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">运行控制</span>
          <span class="panel-subtitle">先保存配置再启动，系统会严格按当前模式执行</span>
        </div>
      </template>

      <transition name="soft-fade" mode="out-in">
        <el-alert
          :key="configForm.runMode"
          :title="startGuideText"
          type="info"
          show-icon
          :closable="false"
          class="start-alert"
        />
      </transition>

      <el-row :gutter="16" class="start-options">
        <el-col :xs="24" :md="16">
          <el-input
            v-model="runForm.startUrl"
            placeholder="本次起始页面URL（留空则使用配置中的基础URL + 起始URL）"
            clearable
          />
        </el-col>
        <el-col :xs="24" :md="8">
          <div class="interval-field">
            <label class="interval-label">本次抓取间隔 (ms)</label>
            <el-input-number
              v-model="runForm.requestIntervalMs"
              :min="0"
              :max="60000"
              :step="100"
              controls-position="right"
              class="full-width"
            />
          </div>
        </el-col>
      </el-row>

      <div class="status-panel">
        <div class="metric-card">
          <span class="metric-label">已访问页面</span>
          <span class="metric-value">{{ status.visitedCount }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-label">待处理队列</span>
          <span class="metric-value">{{ status.pendingCount }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-label">抓取草稿</span>
          <span class="metric-value">{{ status.draftCount }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-label">运行状态</span>
          <el-tag :type="stateTagType" effect="light" round class="state-tag">
            {{ stateTagText }}
          </el-tag>
        </div>
      </div>

      <el-descriptions :column="2" border class="status-detail">
        <el-descriptions-item label="运行模式">
          {{ runModeLabel(configForm.runMode) }}
        </el-descriptions-item>
        <el-descriptions-item label="调度状态">
          {{ status.scheduleActive ? '已启动，等待到点执行' : '未启用自动调度' }}
        </el-descriptions-item>
        <el-descriptions-item label="当前 Cron">
          <span class="mono-text">
            {{ status.effectiveCron || (isScheduleMode ? configForm.scheduleCron : '-') }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="下次执行时间">
          {{ status.nextExecutionTime || (isScheduleMode ? '启动自动爬取后生成' : '一次执行模式无后续调度') }}
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">爬虫配置</el-divider>

      <el-form :model="configForm" label-width="130px" class="crawler-form">
        <div class="form-block">
          <div class="block-title">基础参数</div>
          <el-row :gutter="16">
            <el-col :xs="24" :lg="12">
              <el-form-item label="基础URL">
                <el-input v-model="configForm.baseUrl" placeholder="https://www.gzmtu.edu.cn" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :lg="12">
              <el-form-item label="起始URL">
                <el-input v-model="configForm.startUrl" placeholder="/index.htm" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <div class="form-block">
          <div class="block-title">抓取策略</div>
          <el-row :gutter="16">
            <el-col :xs="24" :md="12" :xl="8">
              <el-form-item label="最大页面数">
                <el-input-number v-model="configForm.maxPages" :min="100" :max="10000" class="full-width" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12" :xl="8">
              <el-form-item label="内容最小长度">
                <el-input-number v-model="configForm.contentMinLength" :min="0" :max="10000" class="full-width" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12" :xl="8">
              <el-form-item label="内容最大长度">
                <el-input-number v-model="configForm.contentMaxLength" :min="100" :max="200000" class="full-width" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="抓取间隔(ms)">
                <div class="inline-control">
                  <el-input-number v-model="configForm.requestIntervalMs" :min="0" :max="60000" :step="100" class="full-width" />
                  <span class="config-tip">用于控制每个页面抓取等待时间，避免访问频率过高。</span>
                </div>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="强校验向量存在">
                <div class="inline-control">
                  <el-switch v-model="configForm.strictVectorCheckEnabled" />
                  <span class="config-tip">开启后会额外查询向量库确认点位存在，准确但更慢。</span>
                </div>
              </el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-form-item label="知识库">
                <el-select
                  v-model="configForm.knowledgeId"
                  placeholder="请选择知识库"
                  filterable
                  clearable
                  class="full-width"
                >
                  <el-option
                    v-for="item in knowledgeList"
                    :key="item.id"
                    :label="item.kname"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <div class="form-block">
          <div class="block-title">调度模式</div>
          <el-form-item label="运行模式" class="run-mode-item">
            <el-radio-group v-model="configForm.runMode" class="mode-switcher">
              <el-radio-button value="schedule">按 Cron 自动爬取</el-radio-button>
              <el-radio-button value="once">启动后立即完整爬取一次并停止</el-radio-button>
            </el-radio-group>
            <span class="config-tip">顶部按钮会严格按这里的模式执行，不再维护独立“启用爬虫/定时任务”双开关。</span>
          </el-form-item>

          <transition name="mode-slide" mode="out-in">
            <el-form-item v-if="isScheduleMode" key="schedule-mode" label="定时任务Cron">
              <div class="mode-area">
                <CronSelect v-model="configForm.scheduleCron" />
                <span class="config-tip config-tip-inline">点击顶部“启动自动爬取”后，系统会按照这里配置的 Cron 自动执行。</span>
              </div>
            </el-form-item>
            <el-form-item v-else key="once-mode" label="执行说明">
              <span class="config-tip config-tip-inline">点击顶部按钮后会立即递归抓取一次全部页面，任务结束自动停止，不保留调度状态。</span>
            </el-form-item>
          </transition>
        </div>

        <el-form-item class="form-submit-row">
          <el-button class="action-btn" type="primary" @click="saveConfig">保存配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="result-card" shadow="never">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">抓取草稿</span>
          <span class="panel-subtitle">可编辑后再保存入库并向量化</span>
        </div>
      </template>

      <div class="result-toolbar">
        <div class="result-toolbar-left">
          <el-button class="action-btn" @click="refreshResults">
            <el-icon><Refresh /></el-icon>
            刷新草稿
          </el-button>
          <el-button
            class="action-btn"
            type="warning"
            :disabled="selectedResultIds.length === 0"
            :loading="savingSelected"
            @click="saveSelectedResults"
          >
            保存选中并向量化
          </el-button>
          <el-button
            class="action-btn"
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
        class="result-table"
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
            <el-button class="action-btn" size="small" type="primary" @click="openEditor(row)">
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="editorVisible" title="编辑抓取草稿" width="880px" class="editor-dialog" destroy-on-close>
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
        <el-button class="action-btn" @click="editorVisible = false">取消</el-button>
        <el-button class="action-btn" type="primary" :loading="savingEditor" @click="saveEditedResult">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
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
  enabled: false,
  scheduleActive: false,
  effectiveCron: '',
  nextExecutionTime: '',
  visitedCount: 0,
  pendingCount: 0,
  draftCount: 0,
  runMode: 'schedule'
})

const knowledgeList = ref([])

const defaultConfig = {
  baseUrl: 'https://www.gzmtu.edu.cn',
  startUrl: '/index.htm',
  maxPages: 5000,
  contentMinLength: 100,
  contentMaxLength: 10000,
  requestIntervalMs: 1000,
  strictVectorCheckEnabled: false,
  knowledgeId: null,
  scheduleCron: '0 0 2 ? * MON',
  runMode: 'schedule'
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

const startingCrawler = ref(false)
const stoppingCrawler = ref(false)
let pollTimer = null

const applyConfig = (target, source) => {
  target.baseUrl = source?.baseUrl ?? defaultConfig.baseUrl
  target.startUrl = source?.startUrl ?? defaultConfig.startUrl
  target.maxPages = source?.maxPages ?? defaultConfig.maxPages
  target.contentMinLength = source?.contentMinLength ?? defaultConfig.contentMinLength
  target.contentMaxLength = source?.contentMaxLength ?? defaultConfig.contentMaxLength
  target.requestIntervalMs = source?.requestIntervalMs ?? defaultConfig.requestIntervalMs
  target.strictVectorCheckEnabled = source?.strictVectorCheckEnabled ?? defaultConfig.strictVectorCheckEnabled
  target.knowledgeId = source?.knowledgeId ?? defaultConfig.knowledgeId
  target.scheduleCron = source?.scheduleCron ?? defaultConfig.scheduleCron
  target.runMode = source?.runMode ?? defaultConfig.runMode
}

const isScheduleMode = computed(() => configForm.runMode === 'schedule')
const startButtonDisabled = computed(() => status.running || status.scheduleActive || startingCrawler.value)
const canStopCrawler = computed(() => status.running || status.enabled || status.scheduleActive || stoppingCrawler.value)
const startButtonText = computed(() => {
  if (isScheduleMode.value && status.scheduleActive) {
    return '自动调度已启动'
  }
  if (!isScheduleMode.value && status.running) {
    return '正在执行一次完整爬取'
  }
  return isScheduleMode.value ? '启动自动爬取' : '立即执行一次'
})
const startGuideText = computed(() => {
  if (isScheduleMode.value) {
    return '点击顶部按钮后，爬虫会进入自动调度状态，并严格按照当前 Cron 表达式执行抓取。'
  }
  return '点击顶部按钮后，爬虫会立即从当前起始链接开始完整递归抓取一次，结束后自动停止。'
})
const stateTagType = computed(() => {
  if (status.running) return 'success'
  if (status.scheduleActive) return 'warning'
  return 'info'
})
const stateTagText = computed(() => {
  if (status.running) return '抓取中'
  if (status.scheduleActive) return '自动调度中'
  return '已停止'
})

const runModeLabel = (mode) => (mode === 'once' ? '立即完整爬取一次后停止' : '按 Cron 自动爬取')

const persistConfig = async (showMessage = true) => {
  const payloads = [
    { key: 'base.url', value: configForm.baseUrl },
    { key: 'start.url', value: configForm.startUrl },
    { key: 'max.pages', value: configForm.maxPages },
    { key: 'content.min.length', value: configForm.contentMinLength },
    { key: 'content.max.length', value: configForm.contentMaxLength },
    { key: 'request.interval.ms', value: configForm.requestIntervalMs },
    { key: 'strict.vector.check.enabled', value: configForm.strictVectorCheckEnabled },
    { key: 'knowledge.id', value: configForm.knowledgeId },
    { key: 'schedule.cron', value: configForm.scheduleCron },
    { key: 'run.mode', value: configForm.runMode }
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
    startingCrawler.value = true
    await persistConfig(false)
    const res = await startCrawlApi({
      startUrl: runForm.startUrl?.trim() || null,
      requestIntervalMs: runForm.requestIntervalMs,
      runMode: configForm.runMode
    })
    ElMessage.success(res.data || '爬虫任务已启动')
    await Promise.all([fetchStatus(true), fetchResults()])
  } catch (error) {
    ElMessage.error(error?.msg || '启动请求失败')
  } finally {
    startingCrawler.value = false
  }
}

const stopCrawl = async () => {
  try {
    stoppingCrawler.value = true
    const res = await stopCrawlApi()
    ElMessage.success(res.data || '爬虫已停止')
    await fetchStatus(true)
  } catch (error) {
    ElMessage.error(error?.msg || '停止请求失败')
  } finally {
    stoppingCrawler.value = false
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
  status.enabled = !!data.enabled
  status.scheduleActive = !!data.scheduleActive
  status.effectiveCron = data.effectiveCron || ''
  status.nextExecutionTime = data.nextExecutionTime || ''
  status.visitedCount = Number(data.visitedCount || 0)
  status.pendingCount = Number(data.pendingCount || 0)
  status.draftCount = Number(data.draftCount || 0)
  status.runMode = data?.config?.runMode || defaultConfig.runMode
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
    if (!status.running && !status.enabled && !status.scheduleActive) {
      return
    }
    try {
      const wasRunning = status.running
      await fetchStatus()
      if (status.running || wasRunning) {
        await fetchResults()
      }
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
.ai-crawler-manage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--app-text);
}

.panel-subtitle {
  font-size: 12px;
  color: var(--app-muted);
}

.start-alert {
  margin-bottom: 14px;
}

.start-options {
  margin-bottom: 16px;
}

.interval-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.interval-label {
  font-size: 12px;
  color: var(--app-muted);
}

.full-width {
  width: 100%;
}

.status-panel {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 94px;
  padding: 14px;
  border-radius: 10px;
  border: 1px solid var(--app-border);
  background: var(--app-surface-2);
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.metric-card:hover {
  border-color: var(--app-border-strong);
  box-shadow: var(--app-shadow-xs);
  transform: translateY(-1px);
}

.metric-label {
  font-size: 13px;
  color: var(--app-muted);
}

.metric-value {
  margin-top: 6px;
  font-size: 30px;
  line-height: 1.1;
  font-weight: 700;
  color: var(--app-text);
}

.state-tag {
  align-self: flex-start;
  font-weight: 600;
}

.status-detail {
  margin-bottom: 18px;
}

.mono-text {
  font-family: "JetBrains Mono", "SFMono-Regular", Consolas, "Liberation Mono", Menlo, Courier, monospace;
  font-size: 13px;
}

.crawler-form {
  padding-top: 4px;
}

.form-block + .form-block {
  margin-top: 10px;
  padding-top: 6px;
  border-top: 1px dashed var(--app-border);
}

.block-title {
  margin: 0 0 12px 2px;
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text);
}

.inline-control {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
}

.mode-switcher {
  margin-right: 8px;
}

.mode-area {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.config-tip {
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.5;
}

.config-tip-inline {
  margin-left: 0;
}

.form-submit-row {
  margin-bottom: 0;
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
  color: var(--app-muted);
  font-size: 13px;
}

.result-table {
  width: 100%;
}

.content-preview {
  display: -webkit-box;
  line-height: 1.45;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}

:deep(.action-btn.el-button) {
  transition: transform 180ms ease, box-shadow 180ms ease, border-color 180ms ease, background-color 180ms ease, color 180ms ease;
}

:deep(.action-btn.el-button:hover:not(.is-disabled)) {
  transform: translateY(-1px);
  box-shadow: var(--app-shadow-xs);
}

:deep(.action-btn.el-button:active:not(.is-disabled)) {
  transform: translateY(0);
}

:deep(.el-input__wrapper),
:deep(.el-textarea__inner),
:deep(.el-select__wrapper),
:deep(.el-input-number) {
  transition: box-shadow 180ms ease, border-color 180ms ease, background-color 180ms ease;
}

:deep(.el-input__wrapper.is-focus),
:deep(.el-select__wrapper.is-focused) {
  box-shadow: var(--app-ring);
}

.soft-fade-enter-active,
.soft-fade-leave-active {
  transition: opacity 220ms ease, transform 220ms ease;
}

.soft-fade-enter-from,
.soft-fade-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

.mode-slide-enter-active,
.mode-slide-leave-active {
  transition: opacity 240ms ease, transform 240ms ease;
}

.mode-slide-enter-from,
.mode-slide-leave-to {
  opacity: 0;
  transform: translateX(8px);
}

@media (max-width: 1200px) {
  .status-panel {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .status-panel {
    grid-template-columns: 1fr;
  }

  .inline-control {
    align-items: flex-start;
    flex-direction: column;
  }

  .mode-switcher {
    margin-right: 0;
    margin-bottom: 6px;
  }

  .metric-value {
    font-size: 26px;
  }
}
</style>
