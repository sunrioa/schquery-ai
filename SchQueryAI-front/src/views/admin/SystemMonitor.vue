<template>
  <div class="admin-page system-monitor">
    <div class="page-header">
      <div class="header-left">
        <h2>系统监控</h2>
        <p class="sub">CPU / 内存 / 磁盘 / 网络 / MySQL / Redis</p>
      </div>
      <div class="header-actions">
        <span class="last-update">最后更新: {{ lastUpdateTime }}</span>
        <el-button type="primary" size="small" @click="refreshData" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-switch v-model="autoRefresh" active-text="自动刷新" @change="toggleAutoRefresh" />
      </div>
    </div>

    <!-- 系统基本信息 -->
    <el-card class="info-card" shadow="never">
      <template #header>
        <span>系统信息</span>
      </template>
      <div class="system-basic-info" v-if="monitorData.system">
        <div class="info-item">
          <span class="label">操作系统</span>
          <span class="value">{{ monitorData.system.osName }} {{ monitorData.system.osVersion }}</span>
        </div>
        <div class="info-item">
          <span class="label">主机名</span>
          <span class="value">{{ monitorData.system.hostName }}</span>
        </div>
        <div class="info-item">
          <span class="label">系统架构</span>
          <span class="value">{{ monitorData.system.arch }}</span>
        </div>
        <div class="info-item">
          <span class="label">运行时间</span>
          <span class="value">{{ formatUptime(monitorData.system.uptime) }}</span>
        </div>
      </div>
    </el-card>

    <!-- CPU和内存 -->
    <el-row :gutter="20" class="monitor-row">
      <el-col :xs="24" :lg="12">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>CPU 使用率</span>
              <el-tag :type="getCpuStatusType(monitorData.cpu?.usage)">
                {{ monitorData.cpu?.usage?.toFixed(1) || 0 }}%
              </el-tag>
            </div>
          </template>
          <div class="cpu-info" v-if="monitorData.cpu">
            <el-progress 
              :percentage="monitorData.cpu.usage" 
              :color="getProgressColor(monitorData.cpu.usage)"
              :stroke-width="20"
            />
            <div class="cpu-details">
              <div class="detail-item">
                <span class="label">处理器</span>
                <span class="value">{{ monitorData.cpu.name }}</span>
              </div>
              <div class="detail-item">
                <span class="label">核心数</span>
                <span class="value">{{ monitorData.cpu.cores }} 核</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>内存使用</span>
              <el-tag :type="getMemoryStatusType(monitorData.memory?.usage)">
                {{ monitorData.memory?.usage?.toFixed(1) || 0 }}%
              </el-tag>
            </div>
          </template>
          <div class="memory-info" v-if="monitorData.memory">
            <el-progress 
              :percentage="monitorData.memory.usage" 
              :color="getProgressColor(monitorData.memory.usage)"
              :stroke-width="20"
            />
            <div class="memory-details">
              <div class="detail-item">
                <span class="label">已使用</span>
                <span class="value">{{ formatBytes(monitorData.memory.used) }}</span>
              </div>
              <div class="detail-item">
                <span class="label">总内存</span>
                <span class="value">{{ formatBytes(monitorData.memory.total) }}</span>
              </div>
              <div class="detail-item">
                <span class="label">空闲</span>
                <span class="value">{{ formatBytes(monitorData.memory.free) }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- MySQL和Redis -->
    <el-row :gutter="20" class="monitor-row">
      <el-col :xs="24" :lg="12">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>MySQL 状态</span>
              <el-tag :type="monitorData.mysql?.connected ? 'success' : 'danger'">
                {{ monitorData.mysql?.connected ? '已连接' : '未连接' }}
              </el-tag>
            </div>
          </template>
          <div class="mysql-info" v-if="monitorData.mysql">
            <div class="status-grid">
              <div class="status-item">
                <div class="status-value">{{ monitorData.mysql.version || '-' }}</div>
                <div class="status-label">版本</div>
              </div>
              <div class="status-item">
                <div class="status-value">{{ monitorData.mysql.threadsConnected || 0 }}</div>
                <div class="status-label">当前连接</div>
              </div>
              <div class="status-item">
                <div class="status-value">{{ monitorData.mysql.maxConnections || 0 }}</div>
                <div class="status-label">最大连接</div>
              </div>
              <div class="status-item">
                <div class="status-value">{{ formatUptime(monitorData.mysql.uptime) }}</div>
                <div class="status-label">运行时间</div>
              </div>
              <div class="status-item">
                <div class="status-value">{{ formatNumber(monitorData.mysql.questions) }}</div>
                <div class="status-label">查询次数</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>Redis 状态</span>
              <el-tag :type="monitorData.redis?.connected ? 'success' : 'danger'">
                {{ monitorData.redis?.connected ? '已连接' : '未连接' }}
              </el-tag>
            </div>
          </template>
          <div class="redis-info" v-if="monitorData.redis">
            <div class="status-grid">
              <div class="status-item">
                <div class="status-value">{{ monitorData.redis.version || '-' }}</div>
                <div class="status-label">版本</div>
              </div>
              <div class="status-item">
                <div class="status-value">{{ monitorData.redis.connectedClients || 0 }}</div>
                <div class="status-label">客户端数</div>
              </div>
              <div class="status-item">
                <div class="status-value">{{ formatBytes(monitorData.redis.usedMemory) }}</div>
                <div class="status-label">已用内存</div>
              </div>
              <div class="status-item">
                <div class="status-value">{{ formatUptime(monitorData.redis.uptime) }}</div>
                <div class="status-label">运行时间</div>
              </div>
              <div class="status-item">
                <div class="status-value">{{ monitorData.redis.keyCount || 0 }}</div>
                <div class="status-label">Key数量</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 磁盘信息 -->
    <el-card class="monitor-card disk-card" shadow="never">
      <template #header>
        <span>磁盘使用</span>
      </template>
      <div class="disk-list" v-if="monitorData.disks?.length">
        <div class="disk-item" v-for="disk in monitorData.disks" :key="disk.mount">
          <div class="disk-header">
            <span class="disk-name">{{ disk.mount }} ({{ disk.name }})</span>
            <span class="disk-usage">{{ disk.usage?.toFixed(1) }}%</span>
          </div>
          <el-progress 
            :percentage="disk.usage" 
            :color="getProgressColor(disk.usage)"
            :stroke-width="12"
            :show-text="false"
          />
          <div class="disk-details">
            <span>已用: {{ formatBytes(disk.used) }}</span>
            <span>总量: {{ formatBytes(disk.total) }}</span>
            <span>可用: {{ formatBytes(disk.free) }}</span>
          </div>
        </div>
      </div>
      <div v-else class="empty-data">暂无磁盘信息</div>
    </el-card>

    <!-- 网络信息 -->
    <el-card class="monitor-card network-card" shadow="never">
      <template #header>
        <span>网络接口</span>
      </template>
      <el-table :data="monitorData.networks" style="width: 100%" v-if="monitorData.networks?.length">
        <el-table-column prop="name" label="接口名称" width="150" />
        <el-table-column prop="ipv4" label="IPv4地址" width="160" />
        <el-table-column label="接收数据">
          <template #default="{ row }">
            {{ formatBytes(row.bytesRecv) }}
          </template>
        </el-table-column>
        <el-table-column label="发送数据">
          <template #default="{ row }">
            {{ formatBytes(row.bytesSent) }}
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-data">暂无网络信息</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import monitorApi from '@/api/monitor'

const loading = ref(false)
const autoRefresh = ref(false)
const lastUpdateTime = ref('-')
const monitorData = ref({})
let refreshTimer = null

// 加载监控数据
const loadMonitorData = async () => {
  try {
    loading.value = true
    const response = await monitorApi.getSystemMonitorData()
    if (response.code === 200) {
      monitorData.value = response.data || {}
      lastUpdateTime.value = new Date().toLocaleString('zh-CN')
    }
  } catch (error) {
    console.error('获取监控数据失败:', error)
    ElMessage.error('获取监控数据失败')
  } finally {
    loading.value = false
  }
}

// 刷新数据
const refreshData = () => {
  loadMonitorData()
}

// 切换自动刷新
const toggleAutoRefresh = (enabled) => {
  if (enabled) {
    refreshTimer = setInterval(loadMonitorData, 5000)
    ElMessage.success('已开启自动刷新 (5秒)')
  } else {
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
    ElMessage.info('已关闭自动刷新')
  }
}

// 格式化字节
const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 格式化运行时间
const formatUptime = (seconds) => {
  if (!seconds) return '-'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  
  if (days > 0) {
    return `${days}天${hours}小时`
  } else if (hours > 0) {
    return `${hours}小时${minutes}分钟`
  } else {
    return `${minutes}分钟`
  }
}

// 格式化数字
const formatNumber = (num) => {
  if (!num) return '0'
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + 'M'
  } else if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toString()
}

// 获取进度条颜色
const getProgressColor = (percentage) => {
  if (percentage < 50) return '#67C23A'
  if (percentage < 80) return '#E6A23C'
  return '#F56C6C'
}

// 获取CPU状态类型
const getCpuStatusType = (usage) => {
  if (!usage) return 'info'
  if (usage < 50) return 'success'
  if (usage < 80) return 'warning'
  return 'danger'
}

// 获取内存状态类型
const getMemoryStatusType = (usage) => {
  if (!usage) return 'info'
  if (usage < 60) return 'success'
  if (usage < 85) return 'warning'
  return 'danger'
}

onMounted(() => {
  loadMonitorData()
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<style scoped>
.last-update {
  font-size: 13px;
  color: #6b7280;
}

.info-card,
.monitor-card {
  margin-bottom: 16px;
}

.monitor-row {
  margin-bottom: 0;
}

.system-basic-info {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item .label,
.detail-item .label {
  font-size: 12px;
  color: #6b7280;
}

[data-theme="dark"] .info-item .label,
[data-theme="dark"] .detail-item .label {
  color: #9ca3af;
}

.info-item .value,
.detail-item .value {
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
}

[data-theme="dark"] .info-item .value,
[data-theme="dark"] .detail-item .value {
  color: #e5e7eb;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.cpu-info,
.memory-info {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cpu-details,
.memory-details {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  text-align: center;
}

.status-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-value {
  font-size: 18px;
  font-weight: 600;
  color: #3B82F6;
}

[data-theme="dark"] .status-value {
  color: #60a5fa;
}

.status-label {
  font-size: 12px;
  color: #6b7280;
}

[data-theme="dark"] .status-label {
  color: #9ca3af;
}

.disk-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.disk-item {
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
}

[data-theme="dark"] .disk-item {
  background: #111827;
}

.disk-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.disk-name {
  font-weight: 500;
  color: #1f2937;
}

[data-theme="dark"] .disk-name {
  color: #e5e7eb;
}

.disk-usage {
  font-weight: 600;
  color: #3B82F6;
}

.disk-details {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: #6b7280;
}

[data-theme="dark"] .disk-details {
  color: #9ca3af;
}

.empty-data {
  text-align: center;
  color: #6b7280;
  padding: 20px;
}

[data-theme="dark"] .empty-data {
  color: #9ca3af;
}

@media (max-width: 768px) {
  .system-basic-info {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .status-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  
  .cpu-details,
  .memory-details {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
