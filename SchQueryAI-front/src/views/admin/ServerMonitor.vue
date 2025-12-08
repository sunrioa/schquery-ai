<template>
  <div class="server-monitor">
    <div class="monitor-header">
      <h3>服务器监控</h3>
      <div class="header-actions">
        <span class="last-update">最后更新: {{ lastUpdateTime }}</span>
        <el-button type="primary" size="small" @click="loadData" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-switch v-model="autoRefresh" active-text="自动刷新" @change="toggleAutoRefresh" />
      </div>
    </div>

    <!-- 系统信息表格 -->
    <el-card class="info-card" shadow="never">
      <template #header>
        <span class="card-title">系统信息</span>
      </template>
      <table class="info-table">
        <tbody>
          <tr>
            <td class="label">操作系统</td>
            <td class="value">{{ systemData.osName || '-' }}</td>
            <td class="label">系统版本</td>
            <td class="value">{{ systemData.osVersion || '-' }}</td>
            <td class="label">系统架构</td>
            <td class="value">{{ systemData.osArch || '-' }}</td>
          </tr>
          <tr>
            <td class="label">主机名</td>
            <td class="value">{{ systemData.hostName || '-' }}</td>
            <td class="label">JDK版本</td>
            <td class="value">{{ systemData.javaVersion || '-' }}</td>
            <td class="label">运行时间</td>
            <td class="value">{{ formatUptime(systemData.uptime) }}</td>
          </tr>
        </tbody>
      </table>
    </el-card>

    <!-- CPU信息表格 -->
    <el-card class="info-card" shadow="never">
      <template #header>
        <span class="card-title">CPU信息</span>
      </template>
      <table class="info-table">
        <tbody>
          <tr>
            <td class="label">CPU名称</td>
            <td class="value" colspan="3">{{ cpuData.name || '-' }}</td>
            <td class="label">核心数</td>
            <td class="value">{{ cpuData.cores || '-' }}</td>
          </tr>
          <tr>
            <td class="label">型号</td>
            <td class="value" colspan="3">{{ cpuData.model || '-' }}</td>
            <td class="label">使用率</td>
            <td class="value">{{ (cpuData.usage || 0).toFixed(1) }}%</td>
          </tr>
        </tbody>
      </table>
    </el-card>

    <!-- 网络信息表格 -->
    <el-card class="info-card" shadow="never">
      <template #header>
        <span class="card-title">网络信息</span>
      </template>
      <table class="info-table">
        <tbody>
          <tr>
            <td class="label">总上传</td>
            <td class="value">{{ formatBytes(networkData.totalUpload) }}</td>
            <td class="label">总下载</td>
            <td class="value">{{ formatBytes(networkData.totalDownload) }}</td>
            <td class="label">上传速率</td>
            <td class="value">{{ formatSpeed(networkData.uploadSpeed) }}</td>
          </tr>
        </tbody>
      </table>
    </el-card>

    <!-- 统计图表 -->
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <span class="card-title">CPU使用率</span>
          </template>
          <div ref="cpuChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <span class="card-title">内存使用率</span>
          </template>
          <div ref="memoryChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <span class="card-title">磁盘使用率</span>
          </template>
          <div ref="diskChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import monitorApi from '@/api/monitor'

const loading = ref(false)
const autoRefresh = ref(false)
const lastUpdateTime = ref('-')
const cpuData = ref({})
const memoryData = ref({})
const diskData = ref({})
const networkData = ref({})
const systemData = ref({})
let refreshTimer = null
let cpuChart = null, memoryChart = null, diskChart = null
const cpuChartRef = ref(null)
const memoryChartRef = ref(null)
const diskChartRef = ref(null)

const loadData = async () => {
  try {
    loading.value = true
    const response = await monitorApi.getAllInfo()
    if (response.code === 200) {
      const data = response.data || {}
      cpuData.value = data.cpu || {}
      memoryData.value = data.memory || {}
      // 磁盘是数组，汇总所有磁盘数据
      const disks = data.disks || []
      if (disks.length > 0) {
        const totalSpace = disks.reduce((sum, d) => sum + (d.total || 0), 0)
        const usedSpace = disks.reduce((sum, d) => sum + (d.used || 0), 0)
        const freeSpace = disks.reduce((sum, d) => sum + (d.free || 0), 0)
        diskData.value = {
          total: totalSpace,
          used: usedSpace,
          free: freeSpace,
          usage: totalSpace > 0 ? (usedSpace / totalSpace * 100) : 0
        }
      }
      // 网络是数组，汇总所有网卡数据
      const networks = data.networks || []
      if (networks.length > 0) {
        networkData.value = {
          totalUpload: networks.reduce((sum, n) => sum + (n.bytesSent || 0), 0),
          totalDownload: networks.reduce((sum, n) => sum + (n.bytesRecv || 0), 0),
          uploadSpeed: 0,
          downloadSpeed: 0
        }
      }
      // 系统信息字段映射
      const sys = data.system || {}
      systemData.value = {
        osName: sys.osName,
        osVersion: sys.osVersion,
        osArch: sys.arch,
        hostName: sys.hostName,
        uptime: sys.uptime,
        javaVersion: sys.javaVersion || '-',
        jvmTotalMemory: sys.jvmTotalMemory || 0
      }
      lastUpdateTime.value = new Date().toLocaleString('zh-CN')
      nextTick(() => {
        initCharts()
      })
    }
  } catch (error) {
    console.error('获取系统监控数据失败:', error)
    ElMessage.error('获取系统监控数据失败')
  } finally {
    loading.value = false
  }
}

const initCharts = () => {
  initPieChart(cpuChartRef.value, cpuChart, cpuData.value.usage || 0, '#F59E0B', 'CPU')
  initPieChart(memoryChartRef.value, memoryChart, memoryData.value.usage || 0, '#3B82F6', '内存')
  initPieChart(diskChartRef.value, diskChart, diskData.value.usage || 0, '#10B981', '磁盘')
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
        formatter: () => `${usageRate.toFixed(1)}%`,
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
  
  // 保存chart实例以便后续销毁
  if (name === 'CPU') cpuChart = chart
  else if (name === '内存') memoryChart = chart
  else if (name === '磁盘') diskChart = chart
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

const formatSpeed = (bytesPerSecond) => {
  if (!bytesPerSecond || bytesPerSecond === 0) return '0 B/s'
  const k = 1024
  const sizes = ['B/s', 'KB/s', 'MB/s', 'GB/s']
  const i = Math.floor(Math.log(bytesPerSecond) / Math.log(k))
  return parseFloat((bytesPerSecond / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
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
    cpuChart?.resize()
    memoryChart?.resize()
    diskChart?.resize()
  })
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (cpuChart) cpuChart.dispose()
  if (memoryChart) memoryChart.dispose()
  if (diskChart) diskChart.dispose()
})
</script>

<style scoped>
.server-monitor {
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
