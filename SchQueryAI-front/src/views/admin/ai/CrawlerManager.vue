<template>
  <div class="crawler-manager">
    <el-card header="爬虫管理">
      <!-- 操作按钮 -->
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

      <!-- 状态显示 -->
      <el-row :gutter="20" class="status-panel">
        <el-col :span="8">
          <el-statistic title="已访问页面" :value="status.visitedCount" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="待处理队列" :value="status.pendingCount" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="运行状态">
            <template #default>
              <el-tag :type="status.running ? 'success' : 'info'">
                {{ status.running ? '运行中' : '已停止' }}
              </el-tag>
            </template>
          </el-statistic>
        </el-col>
      </el-row>

      <!-- 配置表单 -->
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
          <el-input-number v-model="configForm.contentMinLength" :min="0" :max="1000" />
        </el-form-item>
        <el-form-item label="内容最大长度">
          <el-input-number v-model="configForm.contentMaxLength" :min="100" :max="100000" />
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { startCrawlApi, stopCrawlApi, getCrawlerStatusApi, getCrawlerConfigApi, updateCrawlerConfigApi } from '@/api/ai/crawler'
import { getKnowledgeList } from '@/api/ai/knowledge'
import { ElMessage } from 'element-plus'
import CronSelect from '@/components/CronSelect.vue'

// 状态数据
const status = reactive({
  running: false,
  visitedCount: 0,
  pendingCount: 0
})

// 知识库列表
const knowledgeList = ref([])

// 配置数据
const config = reactive({
  enabled: true,
  baseUrl: 'https://www.gzmtu.edu.cn',
  startUrl: '/index.htm',
  maxPages: 5000,
  contentMinLength: 100,
  contentMaxLength: 10000,
  knowledgeId: 1,
  scheduleEnabled: true,
  scheduleCron: '0 0 2 ? * MON'
})

// 表单数据（用于编辑）
const configForm = reactive({ ...config })

// 启动爬虫
const startCrawl = async () => {
  try {
    const res = await startCrawlApi()
    if (res.code === 200) {
      ElMessage.success(res.data || '爬虫已启动')
      await fetchStatus()
    } else {
      ElMessage.error(res.message || '启动失败')
    }
  } catch (error) {
    ElMessage.error('启动请求失败')
  }
}

// 停止爬虫
const stopCrawl = async () => {
  try {
    const res = await stopCrawlApi()
    if (res.code === 200) {
      ElMessage.success(res.data || '爬虫已停止')
      await fetchStatus()
    } else {
      ElMessage.error(res.message || '停止失败')
    }
  } catch (error) {
    ElMessage.error('停止请求失败')
  }
}

// 刷新状态
const refreshStatus = async () => {
  await fetchStatus()
  ElMessage.success('状态已刷新')
}

// 获取状态
const fetchStatus = async () => {
  try {
    const res = await getCrawlerStatusApi()
    if (res.code === 200) {
      Object.assign(status, res.data)
      Object.assign(config, res.data.config || {})
      Object.assign(configForm, res.data.config || {})
    }
  } catch (error) {
    ElMessage.error('获取状态失败')
  }
}

// 保存配置
const saveConfig = async () => {
  try {
    // 批量更新配置
    const updates = []
    if (config.enabled !== configForm.enabled) updates.push({ key: 'enabled', value: configForm.enabled })
    if (config.baseUrl !== configForm.baseUrl) updates.push({ key: 'base.url', value: configForm.baseUrl })
    if (config.startUrl !== configForm.startUrl) updates.push({ key: 'start.url', value: configForm.startUrl })
    if (config.maxPages !== configForm.maxPages) updates.push({ key: 'max.pages', value: configForm.maxPages })
    if (config.contentMinLength !== configForm.contentMinLength) updates.push({ key: 'content.min.length', value: configForm.contentMinLength })
    if (config.contentMaxLength !== configForm.contentMaxLength) updates.push({ key: 'content.max.length', value: configForm.contentMaxLength })
    if (config.knowledgeId !== configForm.knowledgeId) updates.push({ key: 'knowledge.id', value: configForm.knowledgeId })
    if (config.scheduleEnabled !== configForm.scheduleEnabled) updates.push({ key: 'schedule.enabled', value: configForm.scheduleEnabled })
    if (config.scheduleCron !== configForm.scheduleCron) updates.push({ key: 'schedule.cron', value: configForm.scheduleCron })

    for (const update of updates) {
      await updateCrawlerConfigApi(update.key, update.value)
    }

    Object.assign(config, configForm)
    ElMessage.success('配置已保存')
  } catch (error) {
    ElMessage.error('保存配置失败')
  }
}

// 获取知识库列表
const fetchKnowledgeList = async () => {
  try {
    // 设置较大的 pageSize 以获取所有启用的知识库
    const res = await getKnowledgeList({ status: 1, pageSize: 1000 })
    if (res.code === 200) {
      // IPage 返回格式：{ records: [], total: 0, size: 0, current: 0, pages: 0 }
      knowledgeList.value = res.data?.records || []
    }
  } catch (error) {
    ElMessage.error('获取知识库列表失败')
  }
}

// 组件挂载时获取状态
onMounted(() => {
  fetchStatus()
  fetchKnowledgeList()
  // 定时刷新状态（每5秒）
  setInterval(() => {
    if (status.running) {
      fetchStatus()
    }
  }, 5000)
})
</script>

<style scoped>
.crawler-manager {
  padding: 20px;
}

.toolbar {
  margin-bottom: 20px;
}

.status-panel {
  margin-bottom: 20px;
}
</style>
