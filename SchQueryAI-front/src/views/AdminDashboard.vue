<template>
  <div class="admin-page admin-dashboard">
    <div class="page-header">
      <div class="header-left">
        <h2>控制台</h2>
        <p class="sub">最近访问量、系统概览与 AI 配置入口</p>
      </div>
      <div class="header-actions">
        <span class="last-update">最后更新: {{ lastUpdated }}</span>
        <el-button type="primary" :loading="loading" @click="loadAll">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <el-card v-if="!isAdminUser" shadow="never">
      <el-result icon="warning" title="无权限" sub-title="该页面仅管理员可见">
        <template #extra>
          <el-button type="primary" @click="goToChat">返回聊天</el-button>
        </template>
      </el-result>
    </el-card>

    <template v-else>
      <el-row :gutter="12" class="stats-row">
        <el-col :xs="24" :sm="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-title">总用户</div>
            <div class="stat-value">{{ dashboardStats.totalUsers ?? 0 }}</div>
            <div class="stat-sub">今日新增 {{ dashboardStats.todayNewUsers ?? 0 }}</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-title">活跃用户</div>
            <div class="stat-value">{{ dashboardStats.activeUsers ?? 0 }}</div>
            <div class="stat-sub">24小时内登录</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-title">管理员</div>
            <div class="stat-value">{{ dashboardStats.adminCount ?? 0 }}</div>
            <div class="stat-sub">拥有后台权限</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-title">客服待处理</div>
            <div class="stat-value">{{ customerPendingSessions }}</div>
            <div class="stat-sub">未读会话 {{ customerUnreadTotal }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="12" class="stats-row">
        <el-col :xs="24" :sm="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-title">知识库</div>
            <div class="stat-value">{{ knowledgeTotal }}</div>
            <div class="stat-sub">已配置知识库</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-title">模型</div>
            <div class="stat-value">{{ modelTotal }}</div>
            <div class="stat-sub">chat/vector/rerank</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-title">CPU 使用率</div>
            <div class="stat-value">{{ cpuUsageText }}</div>
            <div class="stat-sub">来自系统监控</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-title">网络流量(累计)</div>
            <div class="stat-value">{{ formatBytes(networkTotalRecv + networkTotalSent) }}</div>
            <div class="stat-sub">收 {{ formatBytes(networkTotalRecv) }} · 发 {{ formatBytes(networkTotalSent) }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="12" class="charts-row">
        <el-col :xs="24" :lg="16">
          <el-card shadow="never" class="chart-card">
            <template #header>
              <div class="card-header">
                <span>最近 7 天访问量</span>
                <el-button text @click="goToSystemLogs">查看系统日志</el-button>
              </div>
            </template>
            <div ref="dailyChartRef" class="chart" />
          </el-card>
        </el-col>
        <el-col :xs="24" :lg="8">
          <el-card shadow="never" class="chart-card">
            <template #header>
              <div class="card-header">
                <span>最近 12 小时访问量</span>
                <el-button text @click="goToUserManagement">用户管理</el-button>
              </div>
            </template>
            <div ref="hourlyChartRef" class="chart" />
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="12" class="tables-row">
        <el-col :xs="24" :lg="12">
          <el-card shadow="never" class="table-card">
            <template #header>
              <div class="card-header">
                <span>最近登录</span>
                <el-button text @click="loadAll">刷新</el-button>
              </div>
            </template>
            <el-table :data="recentLogins" size="small" stripe style="width: 100%">
              <el-table-column prop="userName" label="用户" width="140" />
              <el-table-column prop="loginIp" label="IP" width="140" />
              <el-table-column prop="location" label="地点" min-width="160" show-overflow-tooltip />
              <el-table-column prop="loginTime" label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.loginTime) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="12">
          <el-card shadow="never" class="table-card">
            <template #header>
              <div class="card-header">
                <span>最近操作日志</span>
                <el-button text @click="goToSystemLogs">进入日志页</el-button>
              </div>
            </template>
            <el-table :data="recentLogs" size="small" stripe style="width: 100%">
              <el-table-column prop="operator" label="操作人" width="120" />
              <el-table-column prop="action" label="操作" width="140" show-overflow-tooltip />
              <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
                    {{ row.status === 1 ? '成功' : '失败' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="timestamp" label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.timestamp) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="12" class="bottom-row">
        <el-col :xs="24" :lg="12">
          <el-card shadow="never" class="card">
            <template #header>
              <div class="card-header">
                <span>AI 默认配置</span>
                <el-button text @click="goToAiChatConfig">去配置</el-button>
              </div>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="默认模型">{{ chatDefaultConfig.model || '-' }}</el-descriptions-item>
              <el-descriptions-item label="默认知识库">
                {{ chatDefaultConfig.kName ? `${chatDefaultConfig.kName}（${chatDefaultConfig.kid}）` : (chatDefaultConfig.kid || '-') }}
              </el-descriptions-item>
              <el-descriptions-item label="mcpMode">{{ chatDefaultConfig.mcpMode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="mcpServers">
                <span class="mono">{{ (chatDefaultConfig.mcpServers || '').trim() || '-' }}</span>
              </el-descriptions-item>
            </el-descriptions>

            <div class="quick-actions">
              <el-button type="primary" @click="goToAiKnowledge">知识库管理</el-button>
              <el-button @click="goToAiChatModel">模型管理</el-button>
              <el-button @click="goToAiMcp">MCP 管理</el-button>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="12">
          <el-card shadow="never" class="card">
            <template #header>
              <div class="card-header">
                <span>系统快捷入口</span>
                <el-button text @click="goToCustomerService">客服消息</el-button>
              </div>
            </template>
            <div class="quick-actions">
              <el-button type="primary" @click="goToUserManagement">用户管理</el-button>
              <el-button type="warning" @click="goToSensitiveWords">敏感词</el-button>
              <el-button type="success" @click="goToSegmentation">分词词库</el-button>
              <el-button @click="goToSystemMonitor">系统监控</el-button>
              <el-button @click="goToServerMonitor">服务器监控</el-button>
              <el-button @click="goToMySQLMonitor">MySQL 监控</el-button>
              <el-button @click="goToRedisMonitor">Redis 监控</el-button>
              <el-button @click="goToSystemLogs">系统日志</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  Refresh
} from '@element-plus/icons-vue'
import {
  isAdmin,
  getRoleDisplayName
} from '../utils/auth'
import { userApi } from '../api/user'
import { operationLogApi } from '../api/operationLog'
import monitorApi from '../api/monitor'
import { getChatDefaultConfig } from '../api/ai/chatConfig'
import { getKnowledgeList } from '../api/ai/knowledge'
import { getChatModelList } from '../api/ai/chatModel'

const router = useRouter()

const isAdminUser = computed(() => isAdmin())
const loading = ref(false)
const lastUpdated = ref('-')

const dashboardStats = ref({
  totalUsers: 0,
  todayNewUsers: 0,
  activeUsers: 0,
  adminCount: 0
})

const dailyVisits = ref([])
const hourlyVisits = ref([])
const recentLogins = ref([])
const recentLogs = ref([])

const knowledgeTotal = ref(0)
const modelTotal = ref(0)
const chatDefaultConfig = ref({
  model: '',
  kid: '',
  kName: '',
  mcpMode: '',
  mcpServers: ''
})

const customerPendingSessions = ref(0)
const customerUnreadTotal = ref(0)

const cpuUsage = ref(null)
const networkTotalRecv = ref(0)
const networkTotalSent = ref(0)

const cpuUsageText = computed(() => {
  if (cpuUsage.value == null) return '-'
  return `${Number(cpuUsage.value).toFixed(1)}%`
})

const dailyChartRef = ref(null)
const hourlyChartRef = ref(null)
let dailyChart = null
let hourlyChart = null

const formatBytes = (bytes) => {
  const b = Number(bytes || 0)
  if (!b) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(b) / Math.log(k))
  return `${(b / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
}

const formatDateTime = (val) => {
  if (!val) return '-'
  const d = new Date(val)
  if (!Number.isNaN(d.getTime())) return d.toLocaleString('zh-CN')
  return String(val)
}

const renderCharts = () => {
  const textColor = document.documentElement.getAttribute('data-theme') === 'dark' ? '#e5e7eb' : '#111827'
  const mutedColor = document.documentElement.getAttribute('data-theme') === 'dark' ? '#9ca3af' : '#6b7280'

  const dailyX = dailyVisits.value.map((i) => i.date)
  const dailyY = dailyVisits.value.map((i) => i.count)

  if (dailyChartRef.value) {
    if (!dailyChart) dailyChart = echarts.init(dailyChartRef.value)
    dailyChart.setOption({
      grid: { left: 36, right: 18, top: 28, bottom: 28 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: dailyX,
        axisLabel: { color: mutedColor },
        axisLine: { lineStyle: { color: mutedColor } }
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: mutedColor },
        splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.18)' } }
      },
      series: [
        {
          name: '访问量',
          type: 'line',
          data: dailyY,
          smooth: true,
          showSymbol: false,
          areaStyle: { opacity: 0.12 },
          lineStyle: { width: 2, color: '#3b82f6' },
          itemStyle: { color: '#3b82f6' }
        }
      ],
      textStyle: { color: textColor }
    })
  }

  const hourlyX = hourlyVisits.value.map((i) => i.date)
  const hourlyY = hourlyVisits.value.map((i) => i.count)

  if (hourlyChartRef.value) {
    if (!hourlyChart) hourlyChart = echarts.init(hourlyChartRef.value)
    hourlyChart.setOption({
      grid: { left: 36, right: 18, top: 28, bottom: 28 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: hourlyX,
        axisLabel: { color: mutedColor, rotate: 0 },
        axisLine: { lineStyle: { color: mutedColor } }
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: mutedColor },
        splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.18)' } }
      },
      series: [
        {
          name: '访问量',
          type: 'bar',
          data: hourlyY,
          barMaxWidth: 18,
          itemStyle: { color: '#10b981', borderRadius: [4, 4, 0, 0] }
        }
      ],
      textStyle: { color: textColor }
    })
  }
}

const resizeCharts = () => {
  dailyChart?.resize()
  hourlyChart?.resize()
}

const handleThemeChange = () => nextTick(() => renderCharts())

const loadCustomerServiceStats = async () => {
  try {
    const token = localStorage.getItem('token')
    if (!token) return
    const response = await fetch('/api/customer-service/pending-sessions', {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
    })
    if (!response.ok) return
    const result = await response.json()
    if (result.code !== 200) return
    const list = result.data || []
    customerPendingSessions.value = list.filter((s) => (s.unreadCount || 0) > 0).length
    customerUnreadTotal.value = list.reduce((sum, s) => sum + (s.unreadCount || 0), 0)
  } catch (e) {
    // 忽略：不阻塞控制台
  }
}

const loadMonitorSummary = async () => {
  try {
    const res = await monitorApi.getSystemMonitorData()
    if (res.code !== 200) return
    const data = res.data || {}
    cpuUsage.value = data.cpu?.usage ?? null
    const networks = data.networks || []
    networkTotalRecv.value = networks.reduce((sum, n) => sum + (Number(n.bytesRecv) || 0), 0)
    networkTotalSent.value = networks.reduce((sum, n) => sum + (Number(n.bytesSent) || 0), 0)
  } catch (e) {
    // 忽略：不阻塞控制台
  }
}

const loadAll = async () => {
  if (!isAdminUser.value) return
  loading.value = true
  try {
    const [
      statsRes,
      dailyRes,
      hourlyRes,
      recentLoginsRes,
      recentLogsRes,
      knowledgeRes,
      modelRes,
      chatConfigRes
    ] = await Promise.all([
      userApi.getDashboardStats(),
      userApi.getDailyVisitStats(7),
      userApi.getHourlyVisitStats(12),
      userApi.getRecentLogins(10),
      operationLogApi.getRecentLogs(10),
      getKnowledgeList({ pageNum: 1, pageSize: 1 }),
      getChatModelList({ pageNum: 1, pageSize: 1 }),
      getChatDefaultConfig()
    ])

    if (statsRes?.code === 200 && statsRes.data) dashboardStats.value = { ...dashboardStats.value, ...statsRes.data }
    if (dailyRes?.code === 200) dailyVisits.value = dailyRes.data || []
    if (hourlyRes?.code === 200) hourlyVisits.value = hourlyRes.data || []
    if (recentLoginsRes?.code === 200) recentLogins.value = recentLoginsRes.data || []
    if (recentLogsRes?.code === 200) recentLogs.value = recentLogsRes.data || []
    knowledgeTotal.value = knowledgeRes?.data?.total ?? knowledgeTotal.value
    modelTotal.value = modelRes?.data?.total ?? modelTotal.value
    if (chatConfigRes?.code === 200 && chatConfigRes.data) {
      chatDefaultConfig.value = { ...chatDefaultConfig.value, ...chatConfigRes.data }
    }

    await Promise.all([loadCustomerServiceStats(), loadMonitorSummary()])

    lastUpdated.value = new Date().toLocaleString('zh-CN')
    nextTick(() => renderCharts())
  } catch (e) {
    console.error('加载控制台失败:', e)
    ElMessage.error('加载控制台失败')
  } finally {
    loading.value = false
  }
}

// 导航方法
const goToSensitiveWords = () => {
  // 跳转到敏感词管理页面
  router.push('/admin/sensitive-words')
}

const goToSegmentation = () => {
  // 跳转到分词管理页面
  router.push('/admin/segmentation-words')
}

const goToUserManagement = () => {
  // 跳转到用户管理页面
  router.push('/admin/user-management')
}

const goToAiKnowledge = () => {
  router.push('/admin/ai/knowledge')
}

const goToAiChatConfig = () => {
  router.push('/admin/ai/chat-config')
}

const goToAiChatModel = () => {
  router.push('/admin/ai/chat-model')
}

const goToAiMcp = () => {
  router.push('/admin/ai/mcp')
}

const goToChat = () => {
  // 跳转到聊天页面
  router.push('/chat')
}

const goToCustomerService = () => {
  router.push('/admin/customer-service')
}

const goToSystemLogs = () => {
  router.push('/admin/system-logs')
}

const goToSystemMonitor = () => {
  router.push('/admin/system-monitor')
}

const goToServerMonitor = () => {
  router.push('/admin/server-monitor')
}

const goToMySQLMonitor = () => {
  router.push('/admin/mysql-monitor')
}

const goToRedisMonitor = () => {
  router.push('/admin/redis-monitor')
}

onMounted(() => {
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('您尚未登录，请先登录')
    router.push('/login')
    return
  }
  loadAll()
  window.addEventListener('resize', resizeCharts)
  window.addEventListener('theme-change', handleThemeChange)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts)
  window.removeEventListener('theme-change', handleThemeChange)
  dailyChart?.dispose()
  hourlyChart?.dispose()
  dailyChart = null
  hourlyChart = null
})
</script>

<style scoped>
.stats-row,
.charts-row,
.tables-row,
.bottom-row {
  margin-bottom: 12px;
}

.last-update {
  font-size: 13px;
  color: var(--admin-muted, #6b7280);
}

.stat-card {
  min-height: 92px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
}

.stat-title {
  font-size: 13px;
  color: var(--admin-muted, #6b7280);
}

.stat-value {
  font-size: 22px;
  font-weight: 800;
  color: var(--admin-text, #111827);
}

.stat-sub {
  font-size: 12px;
  color: var(--admin-muted, #6b7280);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.chart {
  height: 280px;
  width: 100%;
}

.quick-actions {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  word-break: break-all;
}
</style>
