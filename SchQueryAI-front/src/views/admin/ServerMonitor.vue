<template>
  <div class="server-monitor">
    <div class="monitor-header">
      <h3>系统监控</h3>
      <div class="header-actions">
        <span class="last-update">最后更新: {{ lastUpdateTime }}</span>
        <el-button type="primary" size="small" @click="loadData" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-switch v-model="autoRefresh" active-text="自动刷新" @change="toggleAutoRefresh" />
      </div>
    </div>

    <el-row :gutter="16">
      <!-- CPU使用率饼状图 -->
      <el-col :xs="24" :lg="8">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <span>CPU 使用率</span>
          </template>
          <div ref="cpuChartRef" class="chart-container"></div>
          <div class="chart-info">
            <el-table :data="cpuTableData" size="small">
              <el-table-column prop="name" label="信息" width="100" />
              <el-table-column prop="value" label="值" />
            </el-table>
          </div>
        </el-card>
      </el-col>

      <!-- 内存使用率饼状图 -->
      <el-col :xs="24" :lg="8">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <span>内存使用率</span>
          </template>
          <div ref="memoryChartRef" class="chart-container"></div>
          <div class="chart-info">
            <el-table :data="memoryTableData" size="small">
              <el-table-column prop="name" label="信息" width="100" />
              <el-table-column prop="value" label="值" />
            </el-table>
          </div>
        </el-card>
      </el-col>

      <!-- 磁盘使用率饼状图 -->
      <el-col :xs="24" :lg="8">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <span>磁盘使用率</span>
          </template>
          <div ref="diskChartRef" class="chart-container"></div>
          <div class="chart-info">
            <el-table :data="diskTableData" size="small">
              <el-table-column prop="name" label="信息" width="100" />
              <el-table-column prop="value" label="值" />
            </el-table>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 系统基本信息和网络流量合并为一行 -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <span>系统信息</span>
          </template>
          <div class="info-grid-compact">
            <div class="info-item-compact">
              <span class="label">操作系统</span>
              <span class="value">{{ systemData.osName || '-' }}</span>
            </div>
            <div class="info-item-compact">
              <span class="label">系统版本</span>
              <span class="value">{{ systemData.osVersion || '-' }}</span>
            </div>
            <div class="info-item-compact">
              <span class="label">系统架构</span>
              <span class="value">{{ systemData.osArch || '-' }}</span>
            </div>
            <div class="info-item-compact">
              <span class="label">主机名</span>
              <span class="value">{{ systemData.hostName || '-' }}</span>
            </div>
            <div class="info-item-compact">
              <span class="label">JDK版本</span>
              <span class="value">{{ systemData.javaVersion || '-' }}</span>
            </div>
            <div class="info-item-compact">
              <span class="label">JVM内存</span>
              <span class="value">{{ formatBytes(systemData.jvmTotalMemory) }}</span>
            </div>
            <div class="info-item-compact">
              <span class="label">CPU核心数</span>
              <span class="value">{{ cpuData.cores || '-' }}</span>
            </div>
            <div class="info-item-compact">
              <span class="label">运行时间</span>
              <span class="value">{{ formatUptime(systemData.uptime) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <span>网络流量</span>
          </template>
          <el-row :gutter="12">
            <el-col :span="12">
              <div class="stat-card-compact">
                <div class="stat-icon-small upload">
                  <el-icon size="18"><Upload /></el-icon>
                </div>
                <div class="stat-info">
                  <span class="stat-label">上传速率</span>
                  <span class="stat-value-small">{{ formatSpeed(networkData.uploadSpeed) }}</span>
                </div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="stat-card-compact">
                <div class="stat-icon-small download">
                  <el-icon size="18"><Download /></el-icon>
                </div>
                <div class="stat-info">
                  <span class="stat-label">下载速率</span>
                  <span class="stat-value-small">{{ formatSpeed(networkData.downloadSpeed) }}</span>
                </div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="stat-card-compact">
                <div class="stat-icon-small total-upload">
                  <el-icon size="18"><TopRight /></el-icon>
                </div>
                <div class="stat-info">
                  <span class="stat-label">总上传</span>
                  <span class="stat-value-small">{{ formatBytes(networkData.totalUpload) }}</span>
                </div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="stat-card-compact">
                <div class="stat-icon-small total-download">
                  <el-icon size="18"><BottomRight /></el-icon>
                </div>
                <div class="stat-info">
                  <span class="stat-label">总下载</span>
                  <span class="stat-value-small">{{ formatBytes(networkData.totalDownload) }}</span>
                </div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { Refresh, Upload, Download, TopRight, BottomRight } from '@element-plus/icons-vue'
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

// CPU表格数据
const cpuTableData = computed(() => [
  { name: '核心数', value: cpuData.value.cores || '-' },
  { name: '使用率', value: (cpuData.value.usage || 0).toFixed(1) + '%' }
])

// 内存表格数据
const memoryTableData = computed(() => [
  { name: '总内存', value: formatBytes(memoryData.value.total) },
  { name: '已使用', value: formatBytes(memoryData.value.used) }
])

// 磁盘表格数据
const diskTableData = computed(() => [
  { name: '总容量', value: formatBytes(diskData.value.total) },
  { name: '已使用', value: formatBytes(diskData.value.used) }
])

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
        diskData.value = {
          total: totalSpace,
          used: usedSpace,
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
  margin-bottom: 12px;
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

.monitor-card {
  margin-bottom: 12px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}

.monitor-card :deep(.el-card__header) {
  padding: 10px 16px;
  font-size: 14px;
}

.monitor-card :deep(.el-card__body) {
  padding: 12px 16px;
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

.chart-container {
  width: 100%;
  height: 130px;
}

.chart-info {
  margin-top: 6px;
}

.info-grid-compact {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px 16px;
}

.info-item-compact {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
  border-bottom: 1px dashed #e5e7eb;
}

.info-item-compact:nth-last-child(-n+2) {
  border-bottom: none;
}

[data-theme="dark"] .info-item-compact {
  border-bottom-color: #374151;
}

.info-item-compact .label {
  font-size: 12px;
  color: #6b7280;
}

[data-theme="dark"] .info-item-compact .label {
  color: #9ca3af;
}

.info-item-compact .value {
  font-size: 12px;
  font-weight: 500;
  color: #1f2937;
}

[data-theme="dark"] .info-item-compact .value {
  color: #e5e7eb;
}

.stat-card-compact {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: #f9fafb;
  border-radius: 8px;
  margin-bottom: 8px;
}

[data-theme="dark"] .stat-card-compact {
  background: #111827;
}

.stat-icon-small {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #fff;
}

.stat-icon-small.upload {
  background: linear-gradient(135deg, #3B82F6 0%, #1D4ED8 100%);
}

.stat-icon-small.download {
  background: linear-gradient(135deg, #10B981 0%, #059669 100%);
}

.stat-icon-small.total-upload {
  background: linear-gradient(135deg, #8B5CF6 0%, #6D28D9 100%);
}

.stat-icon-small.total-download {
  background: linear-gradient(135deg, #F59E0B 0%, #D97706 100%);
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 11px;
  color: #6b7280;
}

[data-theme="dark"] .stat-label {
  color: #9ca3af;
}

.stat-value-small {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

[data-theme="dark"] .stat-value-small {
  color: #f3f4f6;
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
