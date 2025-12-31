<template>
  <div class="admin-page mysql-monitor">
    <div class="page-header">
      <div class="header-left">
        <h2>MySQL 监控</h2>
        <p class="sub">连接与运行状态</p>
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

    <!-- MySQL信息表格 -->
    <el-card class="info-card table-card" shadow="never">
      <template #header>
        <div class="card-header-with-status">
          <span class="card-title">MySQL信息</span>
          <el-tag :type="mysqlData.connected ? 'success' : 'danger'" size="small">
            {{ mysqlData.connected ? '已连接' : '未连接' }}
          </el-tag>
        </div>
      </template>
      <div class="info-table-wrap">
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
      </div>
    </el-card>

    <!-- 统计图表 -->
    <el-row :gutter="12">
      <el-col :span="24">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <span class="card-title">MySQL连接使用率</span>
          </template>
          <div ref="mysqlChartRef" class="chart-container"></div>
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
let refreshTimer = null
let mysqlChart = null
const mysqlChartRef = ref(null)
let removeResizeListener = null
let removeThemeListener = null

const connectionUsagePercent = computed(() => {
  if (!mysqlData.value.maxConnections) return 0
  return Math.round((mysqlData.value.threadsConnected / mysqlData.value.maxConnections) * 100)
})

const loadData = async () => {
  try {
    loading.value = true
    const mysqlRes = await monitorApi.getMySQLInfo()
    if (mysqlRes.code === 200) {
      mysqlData.value = mysqlRes.data || {}
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
}

const initPieChart = (chartRef, chart, usageRate, color, name) => {
  if (!chartRef) return
  
  if (chart) {
    chart.dispose()
  }
  
  chart = echarts.init(chartRef)
  const emptyColor = document.documentElement.getAttribute('data-theme') === 'dark' ? '#374151' : '#e5e7eb'
  
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
        { value: 100 - usageRate, name: '空闲', itemStyle: { color: emptyColor } }
      ]
    }]
  }
  
  chart.setOption(option)
  
  if (name === 'mysql') mysqlChart = chart
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

onMounted(() => {
  loadData()
  const handleResize = () => mysqlChart?.resize()
  const handleThemeChange = () => {
    nextTick(() => initCharts())
  }
  window.addEventListener('resize', handleResize)
  window.addEventListener('theme-change', handleThemeChange)
  removeResizeListener = () => window.removeEventListener('resize', handleResize)
  removeThemeListener = () => window.removeEventListener('theme-change', handleThemeChange)
})

onUnmounted(() => {
  removeResizeListener?.()
  removeThemeListener?.()
  if (refreshTimer) clearInterval(refreshTimer)
  if (mysqlChart) mysqlChart.dispose()
})
</script>

<style scoped>
.card-header-with-status {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
