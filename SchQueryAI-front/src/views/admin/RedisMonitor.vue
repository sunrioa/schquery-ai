<template>
  <div class="redis-monitor">
    <div class="monitor-header">
      <h3>Redis 监控</h3>
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
              <el-tag :type="redisData.connected ? 'success' : 'danger'" size="small">
                {{ redisData.connected ? '已连接' : '未连接' }}
              </el-tag>
            </div>
          </template>
          <div class="status-list">
            <div class="status-item">
              <span class="label">版本</span>
              <span class="value">{{ redisData.version || '-' }}</span>
            </div>
            <div class="status-item">
              <span class="label">运行时间</span>
              <span class="value">{{ formatUptime(redisData.uptime) }}</span>
            </div>
            <div class="status-item">
              <span class="label">客户端连接数</span>
              <span class="value">{{ redisData.connectedClients || 0 }}</span>
            </div>
            <div class="status-item">
              <span class="label">Key数量</span>
              <span class="value">{{ redisData.keyCount || 0 }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 内存使用饼状图 -->
      <el-col :xs="24" :lg="8">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <span>内存使用</span>
          </template>
          <div ref="memoryChartRef" class="chart-container"></div>
          <div class="chart-legend">
            <span>已使用: {{ formatBytes(redisData.usedMemory) }}</span>
            <span>总内存: {{ formatBytes(redisData.totalMemory) }}</span>
          </div>
        </el-card>
      </el-col>

      <!-- 连接数表格 -->
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
const redisData = ref({})
let refreshTimer = null
let memoryChart = null
const memoryChartRef = ref(null)

// 计算指标数据
const metricsData = computed(() => [
  { name: '版本', value: redisData.value.version || '-' },
  { name: '连接客户端', value: redisData.value.connectedClients || 0 },
  { name: '已用内存', value: formatBytes(redisData.value.usedMemory) },
  { name: 'Key数量', value: redisData.value.keyCount || 0 },
  { name: '运行时间', value: formatUptime(redisData.value.uptime) }
])

const loadData = async () => {
  try {
    loading.value = true
    const response = await monitorApi.getRedisInfo()
    if (response.code === 200) {
      redisData.value = response.data || {}
      lastUpdateTime.value = new Date().toLocaleString('zh-CN')
      nextTick(() => {
        initMemoryChart()
      })
    }
  } catch (error) {
    console.error('获取Redis监控数据失败:', error)
    ElMessage.error('获取Redis监控数据失败')
  } finally {
    loading.value = false
  }
}

const initMemoryChart = () => {
  if (!memoryChartRef.value) return
  
  if (memoryChart) {
    memoryChart.dispose()
  }
  
  memoryChart = echarts.init(memoryChartRef.value)
  
  const used = redisData.value.usedMemory || 0
  const total = redisData.value.totalMemory || 1
  const free = total - used
  
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
        formatter: () => {
          const percentage = total > 0 ? ((used / total) * 100).toFixed(1) : 0
          return `${percentage}%`
        },
        fontSize: 20,
        fontWeight: 'bold',
        color: '#3B82F6'
      },
      data: [
        { value: used, name: '已使用', itemStyle: { color: '#3B82F6' } },
        { value: free, name: '空闲', itemStyle: { color: '#e5e7eb' } }
      ]
    }]
  }
  
  memoryChart.setOption(option)
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
  window.addEventListener('resize', () => memoryChart?.resize())
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (memoryChart) memoryChart.dispose()
})
</script>

<style scoped>
.redis-monitor {
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
