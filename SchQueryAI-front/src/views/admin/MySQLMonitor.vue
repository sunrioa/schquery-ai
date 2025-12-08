<template>
  <div class="data-service-monitor">
    <div class="monitor-header">
      <h3>数据服务监控</h3>
      <div class="header-actions">
        <span class="last-update">最后更新: {{ lastUpdateTime }}</span>
        <el-button type="primary" size="small" @click="loadData" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-switch v-model="autoRefresh" active-text="自动刷新" @change="toggleAutoRefresh" />
      </div>
    </div>

    <!-- MySQL信息表格 -->
    <el-card class="info-card" shadow="never">
      <template #header>
        <div class="card-header-with-status">
          <span class="card-title">MySQL信息</span>
          <el-tag :type="mysqlData.connected ? 'success' : 'danger'" size="small">
            {{ mysqlData.connected ? '已连接' : '未连接' }}
          </el-tag>
        </div>
      </template>
      <table class="info-table">
        <tbody>
          <tr>
            <td class="label">版本</td>
            <td class="value">{{ mysqlData.version || '-' }}</td>
            <td class="label">运行时间</td>
            <td class="value">{{ formatUptime(mysqlData.uptime) }}</td>
            <td class="label">查询次数</td>
            <td class="value">{{ formatNumber(mysqlData.questions) }}</td>
          </tr>
          <tr>
            <td class="label">当前连接</td>
            <td class="value">{{ mysqlData.threadsConnected || 0 }}</td>
            <td class="label">最大连接</td>
            <td class="value">{{ mysqlData.maxConnections || 0 }}</td>
            <td class="label">连接使用率</td>
            <td class="value">{{ connectionUsagePercent }}%</td>
          </tr>
        </tbody>
      </table>
    </el-card>

    <!-- Redis信息表格 -->
    <el-card class="info-card" shadow="never">
      <template #header>
        <div class="card-header-with-status">
          <span class="card-title">Redis信息</span>
          <el-tag :type="redisData.connected ? 'success' : 'danger'" size="small">
            {{ redisData.connected ? '已连接' : '未连接' }}
          </el-tag>
        </div>
      </template>
      <table class="info-table">
        <tbody>
          <tr>
            <td class="label">版本</td>
            <td class="value">{{ redisData.version || '-' }}</td>
            <td class="label">运行时间</td>
            <td class="value">{{ formatUptime(redisData.uptime) }}</td>
            <td class="label">Key数量</td>
            <td class="value">{{ redisData.keyCount || 0 }}</td>
          </tr>
          <tr>
            <td class="label">客户端连接</td>
            <td class="value">{{ redisData.connectedClients || 0 }}</td>
            <td class="label">已用内存</td>
            <td class="value">{{ formatBytes(redisData.usedMemory) }}</td>
            <td class="label">内存使用率</td>
            <td class="value">{{ redisMemoryPercent }}%</td>
          </tr>
        </tbody>
      </table>
    </el-card>

    <!-- 统计图表 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <span class="card-title">MySQL连接使用率</span>
          </template>
          <div ref="mysqlChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <span class="card-title">Redis内存使用率</span>
          </template>
          <div ref="redisChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>
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
const redisData = ref({})
let refreshTimer = null
let mysqlChart = null, redisChart = null
const mysqlChartRef = ref(null)
const redisChartRef = ref(null)

const connectionUsagePercent = computed(() => {
  if (!mysqlData.value.maxConnections) return 0
  return Math.round((mysqlData.value.threadsConnected / mysqlData.value.maxConnections) * 100)
})

const redisMemoryPercent = computed(() => {
  if (!redisData.value.totalMemory) return 0
  return Math.round((redisData.value.usedMemory / redisData.value.totalMemory) * 100)
})

const loadData = async () => {
  try {
    loading.value = true
    const [mysqlRes, redisRes] = await Promise.all([
      monitorApi.getMySQLInfo(),
      monitorApi.getRedisInfo()
    ])
    if (mysqlRes.code === 200) {
      mysqlData.value = mysqlRes.data || {}
    }
    if (redisRes.code === 200) {
      redisData.value = redisRes.data || {}
    }
    lastUpdateTime.value = new Date().toLocaleString('zh-CN')
    nextTick(() => {
      initCharts()
    })
  } catch (error) {
    console.error('获取数据服务监控数据失败:', error)
    ElMessage.error('获取数据服务监控数据失败')
  } finally {
    loading.value = false
  }
}

const initCharts = () => {
  initPieChart(mysqlChartRef.value, mysqlChart, connectionUsagePercent.value, '#10B981', 'mysql')
  initPieChart(redisChartRef.value, redisChart, redisMemoryPercent.value, '#3B82F6', 'redis')
}

const initPieChart = (chartRef, chart, usageRate, color, name) => {
  if (!chartRef) return
  
  if (chart) {
    chart.dispose()
  }
  
  chart = echarts.init(chartRef)
  
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c}%'
    },
    series: [{
      type: 'pie',
      radius: ['50%', '70%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: false,
      label: {
        show: true,
        position: 'center',
        formatter: () => `${usageRate}%`,
        fontSize: 14,
        fontWeight: 'bold',
        color: color
      },
      data: [
        { value: usageRate, name: '已使用', itemStyle: { color: color } },
        { value: 100 - usageRate, name: '空闲', itemStyle: { color: '#e5e7eb' } }
      ]
    }]
  }
  
  chart.setOption(option)
  
  if (name === 'mysql') mysqlChart = chart
  else if (name === 'redis') redisChart = chart
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

const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
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

onMounted(() => {
  loadData()
  window.addEventListener('resize', () => {
    mysqlChart?.resize()
    redisChart?.resize()
  })
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (mysqlChart) mysqlChart.dispose()
  if (redisChart) redisChart.dispose()
})
</script>

<style scoped>
.data-service-monitor {
  padding: 0;
}

.monitor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.monitor-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

[data-theme="dark"] .monitor-header h3 {
  color: #f3f4f6;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.last-update {
  font-size: 12px;
  color: #6b7280;
}

/* 信息卡片样式 */
.info-card {
  margin-bottom: 10px;
  border-radius: 4px;
  border: 1px solid #e5e7eb;
}

.info-card :deep(.el-card__header) {
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #e5e7eb;
}

.info-card :deep(.el-card__body) {
  padding: 0;
}

[data-theme="dark"] .info-card {
  background: #1e293b;
  border-color: #374151;
}

[data-theme="dark"] .info-card :deep(.el-card__header) {
  background: #111827;
  border-bottom-color: #374151;
  color: #f3f4f6;
}

[data-theme="dark"] .info-card :deep(.el-card__body) {
  background: #1e293b;
}

.card-header-with-status {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

[data-theme="dark"] .card-title {
  color: #f3f4f6;
}

/* 信息表格样式 */
.info-table {
  width: 100%;
  border-collapse: collapse;
}

.info-table td {
  padding: 6px 12px;
  border: 1px solid #e5e7eb;
  font-size: 12px;
}

.info-table td.label {
  background: #f8fafc;
  color: #6b7280;
  width: 100px;
  font-weight: 500;
}

.info-table td.value {
  color: #1f2937;
}

[data-theme="dark"] .info-table td {
  border-color: #374151;
}

[data-theme="dark"] .info-table td.label {
  background: #111827;
  color: #9ca3af;
}

[data-theme="dark"] .info-table td.value {
  color: #e5e7eb;
}

/* 图表卡片样式 */
.chart-card {
  margin-bottom: 0;
  border-radius: 4px;
  border: 1px solid #e5e7eb;
}

.chart-card :deep(.el-card__header) {
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #e5e7eb;
}

.chart-card :deep(.el-card__body) {
  padding: 8px;
}

[data-theme="dark"] .chart-card {
  background: #1e293b;
  border-color: #374151;
}

[data-theme="dark"] .chart-card :deep(.el-card__header) {
  background: #111827;
  border-bottom-color: #374151;
  color: #f3f4f6;
}

[data-theme="dark"] .chart-card :deep(.el-card__body) {
  background: #1e293b;
}

.chart-container {
  width: 100%;
  height: 130px;
}
</style>
