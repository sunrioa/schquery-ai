<template>
  <div class="admin-page redis-monitor">
    <div class="page-header">
      <div class="header-left">
        <h2>Redis 监控</h2>
        <p class="sub">连接与内存使用</p>
      </div>
      <div class="header-actions">
        <span class="last-update">最后更新: {{ lastUpdateTime }}</span>
        <el-button type="primary" size="small" @click="loadData" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-switch v-model="autoRefresh" active-text="自动刷新" @change="toggleAutoRefresh" />
      </div>
    </div>

    <el-card class="info-card" shadow="never">
      <template #header>
        <div class="card-header-with-status">
          <span class="card-title">Redis 信息</span>
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
            <td class="value">{{ redisData.keyCount ?? 0 }}</td>
          </tr>
          <tr>
            <td class="label">客户端连接</td>
            <td class="value">{{ redisData.connectedClients ?? 0 }}</td>
            <td class="label">已用内存</td>
            <td class="value">{{ formatBytes(redisData.usedMemory) }}</td>
            <td class="label">内存使用率</td>
            <td class="value">{{ redisMemoryPercent }}%</td>
          </tr>
        </tbody>
      </table>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="24">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <span class="card-title">Redis 内存使用率</span>
          </template>
          <div ref="redisChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import monitorApi from '@/api/monitor'

const loading = ref(false)
const autoRefresh = ref(false)
const lastUpdateTime = ref('-')
const redisData = ref({})

let refreshTimer = null
let redisChart = null
const redisChartRef = ref(null)

const redisMemoryPercent = computed(() => {
  const totalMemory = Number(redisData.value.totalMemory || redisData.value.maxMemory || 0)
  const usedMemory = Number(redisData.value.usedMemory || 0)
  if (!totalMemory) return 0
  const percent = (usedMemory / totalMemory) * 100
  if (Number.isNaN(percent) || !Number.isFinite(percent)) return 0
  return Math.min(100, Math.max(0, Math.round(percent)))
})

const loadData = async () => {
  try {
    loading.value = true
    const res = await monitorApi.getRedisInfo()
    if (res.code === 200) {
      redisData.value = res.data || {}
    }
    lastUpdateTime.value = new Date().toLocaleString('zh-CN')
    nextTick(() => {
      initCharts()
    })
  } catch (error) {
    console.error('获取Redis监控数据失败:', error)
    ElMessage.error('获取Redis监控数据失败')
  } finally {
    loading.value = false
  }
}

const initCharts = () => {
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
    series: [
      {
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
      }
    ]
  }

  chart.setOption(option)

  if (name === 'redis') redisChart = chart
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
  window.addEventListener('resize', () => {
    redisChart?.resize()
  })
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (redisChart) redisChart.dispose()
})
</script>

<style scoped>
.last-update {
  font-size: 12px;
  color: var(--admin-muted, #6b7280);
}

.info-card {
  margin-bottom: 16px;
}

.info-card :deep(.el-card__header) {
  padding: 12px 16px;
}

.info-card :deep(.el-card__body) {
  padding: 0;
}

.card-header-with-status {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--admin-text, #1f2937);
}

.info-table {
  width: 100%;
  border-collapse: collapse;
}

.info-table td {
  padding: 6px 12px;
  border: 1px solid var(--admin-border, #e5e7eb);
  font-size: 12px;
}

.info-table td.label {
  background: rgba(107, 114, 128, 0.08);
  color: var(--admin-muted, #6b7280);
  width: 100px;
  font-weight: 500;
}

.info-table td.value {
  color: var(--admin-text, #1f2937);
}

[data-theme="dark"] .info-table td.label {
  background: rgba(255, 255, 255, 0.06);
}

.chart-card {
  margin-bottom: 0;
}

.chart-card :deep(.el-card__header) {
  padding: 12px 16px;
}

.chart-card :deep(.el-card__body) {
  padding: 8px;
}

.chart-container {
  width: 100%;
  height: 130px;
}
</style>
