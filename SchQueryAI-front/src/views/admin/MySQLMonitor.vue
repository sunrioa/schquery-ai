<template>
  <div class="mysql-monitor">
    <div class="monitor-header">
      <h3>数据库监控</h3>
      <div class="header-actions">
        <span class="last-update">最后更新: {{ lastUpdateTime }}</span>
        <el-button type="primary" size="small" @click="loadData" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-switch v-model="autoRefresh" active-text="自动刷新" @change="toggleAutoRefresh" />
      </div>
    </div>

    <el-row :gutter="20">
      <!-- 连接状态 -->
      <el-col :xs="24" :lg="8">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>连接状态</span>
              <el-tag :type="mysqlData.connected ? 'success' : 'danger'" size="small">
                {{ mysqlData.connected ? '已连接' : '未连接' }}
              </el-tag>
            </div>
          </template>
          <div class="status-list">
            <div class="status-item">
              <span class="label">版本</span>
              <span class="value">{{ mysqlData.version || '-' }}</span>
            </div>
            <div class="status-item">
              <span class="label">运行时间</span>
              <span class="value">{{ formatUptime(mysqlData.uptime) }}</span>
            </div>
            <div class="status-item">
              <span class="label">当前连接数</span>
              <span class="value">{{ mysqlData.threadsConnected || 0 }}</span>
            </div>
            <div class="status-item">
              <span class="label">最大连接数</span>
              <span class="value">{{ mysqlData.maxConnections || 0 }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 连接数饼状图 -->
      <el-col :xs="24" :lg="8">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <span>连接数使用</span>
          </template>
          <div ref="connectionChartRef" class="chart-container"></div>
          <div class="chart-legend">
            <span>已使用: {{ mysqlData.threadsConnected || 0 }}</span>
            <span>最大: {{ mysqlData.maxConnections || 0 }}</span>
          </div>
        </el-card>
      </el-col>

      <!-- 查询统计 -->
      <el-col :xs="24" :lg="8">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <span>运行指标</span>
          </template>
          <el-table :data="metricsData" style="width: 100%" size="small">
            <el-table-column prop="name" label="指标" width="120" />
            <el-table-column prop="value" label="值" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 连接使用率进度 -->
    <el-card class="monitor-card progress-card" shadow="never">
      <template #header>
        <span>连接池使用率</span>
      </template>
      <div class="progress-container">
        <div class="progress-info">
          <span>当前连接: {{ mysqlData.threadsConnected || 0 }} / {{ mysqlData.maxConnections || 0 }}</span>
          <span class="usage-percent">{{ connectionUsagePercent }}%</span>
        </div>
        <el-progress 
          :percentage="connectionUsagePercent" 
          :color="getProgressColor(connectionUsagePercent)"
          :stroke-width="20"
        />
        <div class="progress-tips">
          <span v-if="connectionUsagePercent < 50" class="tip-success">连接池状态良好</span>
          <span v-else-if="connectionUsagePercent < 80" class="tip-warning">连接池使用率较高，请关注</span>
          <span v-else class="tip-danger">连接池使用率过高，请及时处理！</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import monitorApi from '@/api/monitor'

const loading = ref(false)
const autoRefresh = ref(false)
const lastUpdateTime = ref('-')
const mysqlData = ref({})
let refreshTimer = null
let connectionChart = null
const connectionChartRef = ref(null)

// 连接使用率百分比
const connectionUsagePercent = computed(() => {
  if (!mysqlData.value.maxConnections) return 0
  return Math.round((mysqlData.value.threadsConnected / mysqlData.value.maxConnections) * 100)
})

// 计算指标数据
const metricsData = computed(() => [
  { name: '版本', value: mysqlData.value.version || '-' },
  { name: '当前连接', value: mysqlData.value.threadsConnected || 0 },
  { name: '最大连接', value: mysqlData.value.maxConnections || 0 },
  { name: '查询次数', value: formatNumber(mysqlData.value.questions) },
  { name: '运行时间', value: formatUptime(mysqlData.value.uptime) }
])

const loadData = async () => {
  try {
    loading.value = true
    const response = await monitorApi.getMySQLInfo()
    if (response.code === 200) {
      mysqlData.value = response.data || {}
      lastUpdateTime.value = new Date().toLocaleString('zh-CN')
      nextTick(() => {
        initConnectionChart()
      })
    }
  } catch (error) {
    console.error('获取MySQL监控数据失败:', error)
    ElMessage.error('获取MySQL监控数据失败')
  } finally {
    loading.value = false
  }
}

const initConnectionChart = () => {
  if (!connectionChartRef.value) return
  
  if (connectionChart) {
    connectionChart.dispose()
  }
  
  connectionChart = echarts.init(connectionChartRef.value)
  
  const used = mysqlData.value.threadsConnected || 0
  const max = mysqlData.value.maxConnections || 1
  const free = max - used
  
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'horizontal',
      bottom: '0%',
      textStyle: {
        color: document.documentElement.getAttribute('data-theme') === 'dark' ? '#9ca3af' : '#666'
      }
    },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: false,
      label: {
        show: true,
        position: 'center',
        formatter: () => `${connectionUsagePercent.value}%`,
        fontSize: 20,
        fontWeight: 'bold',
        color: '#10B981'
      },
      data: [
        { value: used, name: '已使用', itemStyle: { color: '#10B981' } },
        { value: free, name: '空闲', itemStyle: { color: '#e5e7eb' } }
      ]
    }]
  }
  
  connectionChart.setOption(option)
}

const toggleAutoRefresh = (enabled) => {
  if (enabled) {
    refreshTimer = setInterval(loadData, 5000)
    ElMessage.success('已开启自动刷新 (5秒)')
  } else {
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
    ElMessage.info('已关闭自动刷新')
  }
}

const formatNumber = (num) => {
  if (!num) return '0'
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toString()
}

const formatUptime = (seconds) => {
  if (!seconds) return '-'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  
  if (days > 0) return `${days}天${hours}小时`
  if (hours > 0) return `${hours}小时${minutes}分钟`
  return `${minutes}分钟`
}

const getProgressColor = (percentage) => {
  if (percentage < 50) return '#10B981'
  if (percentage < 80) return '#F59E0B'
  return '#EF4444'
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', () => connectionChart?.resize())
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (connectionChart) connectionChart.dispose()
})
</script>

<style scoped>
.mysql-monitor {
  padding: 0;
}

.monitor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.monitor-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

[data-theme="dark"] .monitor-header h3 {
  color: #f3f4f6;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.last-update {
  font-size: 12px;
  color: #6b7280;
}

.monitor-card {
  margin-bottom: 20px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}

[data-theme="dark"] .monitor-card {
  background: #1e293b;
  border-color: #374151;
}

[data-theme="dark"] .monitor-card :deep(.el-card__header) {
  border-bottom-color: #374151;
  color: #f3f4f6;
}

[data-theme="dark"] .monitor-card :deep(.el-card__body) {
  background: #1e293b;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px dashed #e5e7eb;
}

.status-item:last-child {
  border-bottom: none;
}

[data-theme="dark"] .status-item {
  border-bottom-color: #374151;
}

.status-item .label {
  font-size: 13px;
  color: #6b7280;
}

[data-theme="dark"] .status-item .label {
  color: #9ca3af;
}

.status-item .value {
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
}

[data-theme="dark"] .status-item .value {
  color: #e5e7eb;
}

.chart-container {
  width: 100%;
  height: 200px;
}

.chart-legend {
  display: flex;
  justify-content: space-around;
  font-size: 12px;
  color: #6b7280;
  margin-top: 10px;
}

[data-theme="dark"] .chart-legend {
  color: #9ca3af;
}

.progress-container {
  padding: 10px 0;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
  color: #4b5563;
}

[data-theme="dark"] .progress-info {
  color: #d1d5db;
}

.usage-percent {
  font-weight: 600;
  color: #10B981;
}

.progress-tips {
  margin-top: 12px;
  text-align: center;
}

.tip-success {
  color: #10B981;
}

.tip-warning {
  color: #F59E0B;
}

.tip-danger {
  color: #EF4444;
  font-weight: 500;
}

[data-theme="dark"] :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: #111827;
  --el-table-row-hover-bg-color: #1f2937;
  --el-table-text-color: #e5e7eb;
  --el-table-header-text-color: #9ca3af;
  --el-table-border-color: #374151;
}
</style>
