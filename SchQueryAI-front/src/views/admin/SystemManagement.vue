<template>
  <div class="ruoyi-shell">
    <aside class="ruoyi-shell__sidebar">
      <div class="sidebar-brand">
        <div class="brand-icon">AI</div>
        <div>
          <p class="brand-name">SchQueryAI 控制台</p>
          <span class="brand-version">{{ versionInfo.version }}</span>
        </div>
      </div>
      <el-menu
        class="sidebar-menu"
        :default-active="activeMenu"
        background-color="transparent"
        text-color="#6b7280"
        active-text-color="#111827"
        @select="handleMenuSelect"
      >
        <el-menu-item index="overview">
          <el-icon><House /></el-icon>
          <span>首页总览</span>
        </el-menu-item>
        <el-menu-item index="user">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="sensitive">
          <el-icon><Warning /></el-icon>
          <span>敏感词管理</span>
        </el-menu-item>
        <el-menu-item index="segmentation">
          <el-icon><Collection /></el-icon>
          <span>分词管理</span>
        </el-menu-item>
        <el-menu-item index="customer">
          <el-icon><Service /></el-icon>
          <span>客服管理</span>
        </el-menu-item>
        <el-menu-item index="logs">
          <el-icon><Document /></el-icon>
          <span>系统日志</span>
        </el-menu-item>
        <el-sub-menu index="monitor">
          <template #title>
            <el-icon><Monitor /></el-icon>
            <span>系统监控</span>
          </template>
          <el-menu-item index="server-monitor">
            <el-icon><Cpu /></el-icon>
            <span>服务器监控</span>
          </el-menu-item>
          <el-menu-item index="data-service-monitor">
            <el-icon><Coin /></el-icon>
            <span>数据服务监控</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </aside>

    <div class="ruoyi-shell__main">
      <header class="shell-toolbar">
        <div class="toolbar-info">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item>系统管理</el-breadcrumb-item>
            <el-breadcrumb-item>{{ contentView === 'overview' ? '工作台' : panelTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
          <h2>{{ contentView === 'overview' ? '系统管理工作台' : panelTitle }}</h2>
        </div>
        <div class="toolbar-actions">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索操作或模块"
            size="small"
            class="toolbar-search"
            clearable
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button text class="toolbar-icon" @click="toggleDarkMode" :title="isDarkMode ? '切换浅色模式' : '切换暗夜模式'">
            <el-icon><Moon v-if="!isDarkMode" /><Sunny v-else /></el-icon>
          </el-button>
          <el-badge :value="unreadMessageCount" :hidden="unreadMessageCount === 0" :max="99">
            <el-button text class="toolbar-icon" @click="goToCustomerServiceManagement">
              <el-icon><Bell /></el-icon>
            </el-button>
          </el-badge>
          <el-button text class="toolbar-icon" @click="refreshSystemStatus">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </header>

      <div v-if="contentView === 'overview'" class="shell-content">
        <section class="hero-card" ref="overviewSection">
          <div class="hero-card__main">
            <p class="hero-tag">SchQueryAI 管理台</p>
            <h3>对话与治理的统一中枢</h3>
            <p class="hero-desc">
              轻量的管理视图，与聊天端保持一致的观感。聚焦用户、内容和客服三大核心，随时掌握运行态势。
            </p>
            <div class="hero-meta">
              <span class="version">当前版本：{{ versionInfo.version }}</span>
              <el-tag type="success" size="small">在线</el-tag>
              <el-tag type="info" size="small">{{ versionInfo.releaseDate }}</el-tag>
            </div>
            <div class="hero-actions">
              <el-button type="primary" size="small" @click="refreshSystemStatus">
                <el-icon><Refresh /></el-icon>
                更新状态
              </el-button>
              <el-button size="small" @click="goBack">
                <el-icon><ArrowLeft /></el-icon>
                返回聊天
              </el-button>
            </div>
          </div>
          <div class="hero-card__tech">
            <h4>核心能力</h4>
            <div class="tech-columns">
              <div v-for="block in capabilityHighlights" :key="block.title">
                <p class="tech-title">{{ block.title }}</p>
                <ul>
                  <li v-for="item in block.items" :key="item">{{ item }}</li>
                </ul>
              </div>
            </div>
          </div>
        </section>

        <section class="panel-group">
          <el-row :gutter="20">
            <el-col :xs="24" :sm="12" :lg="6">
              <el-card class="stat-card" shadow="hover">
                <div class="stat-card__icon primary">
                  <el-icon><User /></el-icon>
                </div>
                <div class="stat-card__body">
                  <p class="stat-card__label">总用户数</p>
                  <p class="stat-card__value">{{ totalUsers }}</p>
                  <p class="stat-card__desc">活跃 {{ activeUsers }} · 管理员 {{ adminCount }}</p>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="24" :sm="12" :lg="6">
              <el-card class="stat-card" shadow="hover">
                <div class="stat-card__icon success">
                  <el-icon><UserFilled /></el-icon>
                </div>
                <div class="stat-card__body">
                  <p class="stat-card__label">今日新增用户</p>
                  <p class="stat-card__value">{{ todayNewUsers }}</p>
                  <p class="stat-card__desc">累计 {{ totalUsers }}</p>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="24" :sm="12" :lg="6">
              <el-card class="stat-card" shadow="hover">
                <div class="stat-card__icon warning">
                  <el-icon><Warning /></el-icon>
                </div>
                <div class="stat-card__body">
                  <p class="stat-card__label">敏感词库</p>
                  <p class="stat-card__value">{{ totalSensitiveWords }}</p>
                  <p class="stat-card__desc">今日新增 {{ todayNewSensitiveWords }}</p>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="24" :sm="12" :lg="6">
              <el-card class="stat-card" shadow="hover">
                <div class="stat-card__icon info">
                  <el-icon><Collection /></el-icon>
                </div>
                <div class="stat-card__body">
                  <p class="stat-card__label">分词词库</p>
                  <p class="stat-card__value">{{ totalSegmentations }}</p>
                  <p class="stat-card__desc">今日新增 {{ todayNewSegmentations }}</p>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </section>

        <!-- 访问量统计图表 -->
        <section class="chart-row">
          <el-card class="ruoyi-card chart-card" shadow="never">
            <template #header>
              <div class="ruoyi-card__header">
                <span>流量统计</span>
                <div class="chart-actions">
                  <el-radio-group v-model="chartTimeRange" size="small" @change="onChartTimeRangeChange">
                    <el-radio-button value="12h">近12小时</el-radio-button>
                    <el-radio-button value="15d">近15天</el-radio-button>
                    <el-radio-button value="30d">近30天</el-radio-button>
                  </el-radio-group>
                </div>
              </div>
            </template>
            <div ref="visitChartRef" class="visit-chart"></div>
          </el-card>
        </section>

        <section class="monitor-row">
          <el-row :gutter="20">
            <el-col :xs="24" :lg="12">
              <el-card class="ruoyi-card monitor-card" shadow="never">
                <template #header>
                  <div class="ruoyi-card__header">
                    <span>系统状态</span>
                    <el-button text size="small" @click="refreshSystemStatus">
                      <el-icon><Refresh /></el-icon>
                      刷新
                    </el-button>
                  </div>
                </template>

                <div class="system-status">
                  <div class="status-item">
                    <span class="status-label">API服务状态</span>
                    <el-tag :type="apiStatus.type">{{ apiStatus.text }}</el-tag>
                  </div>
                  <div class="status-item">
                    <span class="status-label">数据库连接</span>
                    <el-tag :type="dbStatus.type">{{ dbStatus.text }}</el-tag>
                  </div>
                  <div class="status-item">
                    <span class="status-label">Redis缓存</span>
                    <el-tag :type="redisStatus.type">{{ redisStatus.text }}</el-tag>
                  </div>
                  <div class="status-item">
                    <span class="status-label">系统负载</span>
                    <el-progress :percentage="systemLoad" :color="getLoadColor(systemLoad)" />
                  </div>
                </div>
              </el-card>
            </el-col>

            <el-col :xs="24" :lg="12">
              <el-card class="ruoyi-card logs-card" shadow="never" ref="logsSection">
                <template #header>
                  <span>最近操作日志</span>
                </template>
                <div class="operation-logs">
                  <div v-if="recentLogs.length === 0" class="no-logs">暂无操作日志</div>
                  <div v-for="log in recentLogs" :key="log.id" class="log-item">
                    <div class="log-item__header">
                      <span class="log-operator">{{ log.operator }}</span>
                      <span class="log-action">{{ log.action }}</span>
                      <span class="log-time">{{ formatTime(log.timestamp) }}</span>
                    </div>
                    <p class="log-detail">{{ log.detail }}</p>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </section>

        <section class="operations-row">
          <el-row :gutter="20">
            <!-- 最近访问IP -->
            <el-col :xs="24" :lg="12">
              <el-card class="ruoyi-card recent-ip-card" shadow="never">
                <template #header>
                  <div class="ruoyi-card__header">
                    <span>最近访问IP</span>
                    <el-button text size="small" @click="loadRecentLogins">
                      <el-icon><Refresh /></el-icon>
                      刷新
                    </el-button>
                  </div>
                </template>
                <div class="recent-ip-list">
                  <div v-if="recentLogins.length === 0" class="empty-data">暂无访问记录</div>
                  <div v-for="login in recentLogins" :key="login.id" class="ip-item">
                    <div class="ip-info">
                      <span class="ip-address">{{ login.loginIp }}</span>
                      <span class="ip-location">{{ login.location }}</span>
                    </div>
                    <div class="ip-meta">
                      <span class="ip-user">{{ login.userName }}</span>
                      <span class="ip-time">{{ formatTime(login.loginTime) }}</span>
                    </div>
                  </div>
                </div>
              </el-card>
            </el-col>

            <el-col :xs="24" :lg="12">
              <el-card class="ruoyi-card customer-card" shadow="never">
                <template #header>
                  <div class="ruoyi-card__header">
                    <div>
                      <span>客服消息</span>
<!--                      <p class="meta">同步客服渠道待处理会话</p>-->
                    </div>
                    <div class="header-action-group">
                      <el-tag v-if="unreadMessageCount > 0" type="danger" size="small">未读 {{ unreadMessageCount }}</el-tag>
                      <el-button text size="small" @click="loadCustomerServiceData">
                        <el-icon><Refresh /></el-icon>
                        重新获取
                      </el-button>
                    </div>
                  </div>
                </template>

                <div class="service-stats">
                  <div class="stat-box">
                    <div class="stat-number">{{ pendingSessionCount }}</div>
                    <div class="stat-name">待处理</div>
                  </div>
                  <div class="stat-box">
                    <div class="stat-number">{{ unreadMessageCount }}</div>
                    <div class="stat-name">未读消息</div>
                  </div>
                </div>

                <div class="sessions-wrapper">
                  <div v-if="customerServiceSessions.length === 0" class="empty-sessions">
                    暂无客服消息
                  </div>
                  <div v-else class="sessions-list">
                    <div
                      v-for="session in customerServiceSessions.slice(0, 3)"
                      :key="session.id"
                      class="session-item"
                      @click="openSession(session)"
                    >
                      <div class="session-item__header">
                        <span class="user-name">{{ session.userName }}</span>
                        <span class="session-time">{{ formatTime(session.lastMessageTime) }}</span>
                      </div>
                      <p class="session-preview" v-if="session.lastMessage">{{ session.lastMessage }}</p>
                      <el-tag v-if="session.unreadCount > 0" type="danger" size="small" class="unread-tag">
                        {{ session.unreadCount }}条未读
                      </el-tag>
                    </div>
                  </div>
                </div>

                <div class="customer-service-actions">
                  <el-button type="primary" size="small" @click="loadCustomerServiceData">刷新</el-button>
                  <el-button type="default" size="small" @click="goToCustomerServiceManagement">查看全部</el-button>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </section>
      </div>

      <div v-else class="shell-content panel-mode">
        <div class="panel-body">
          <component
            :is="activePanelComponent"
            v-if="activePanelComponent"
            :key="contentView"
            embedded
            @back="backToOverview"
          />
        </div>
      </div>
    </div>

    <el-dialog
      v-model="showCustomerServiceModal"
      title="处理客服消息"
      width="600px"
      v-if="selectedSession"
    >
      <div class="modal-content">
        <div class="session-header">
          <span>用户: {{ selectedSession.userName }}</span>
          <span>主题: {{ selectedSession.topic }}</span>
        </div>
        <div class="session-messages">
          <div
            v-for="msg in selectedSession.messages"
            :key="msg.id"
            class="message"
            :class="{ 'user-message': msg.senderType === 1, 'admin-message': msg.senderType === 2 }"
          >
            <span class="message-content">{{ msg.messageContent }}</span>
            <span class="message-time">{{ formatTime(msg.createTime) }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showCustomerServiceModal = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import {
  ArrowLeft,
  User,
  UserFilled,
  Warning,
  Collection,
  Refresh,
  Delete,
  Download,
  FolderOpened,
  Document,
  Bell,
  House,
  Search,
  Service,
  Moon,
  Sunny,
  Monitor,
  Cpu,
  Coin
} from '@element-plus/icons-vue'
import { userApi } from '../../api/user'
import { operationLogApi } from '../../api/operationLog'
import request from '../../api/request'
import UserManagement from './UserManagement.vue'
import SensitiveWordsManagement from './sensitive-words.vue'
import SegmentationWordsManagement from './segmentation-words.vue'
import SystemLogsManagement from './system-logs.vue'
import ServerMonitor from './ServerMonitor.vue'
import DataServiceMonitor from './MySQLMonitor.vue'

const router = useRouter()

// 响应式数据
const totalUsers = ref(0)
const todayNewUsers = ref(0)
const activeUsers = ref(0)
const adminCount = ref(0)
const totalSensitiveWords = ref(0)
const todayNewSensitiveWords = ref(0)
const totalSegmentations = ref(0)
const todayNewSegmentations = ref(0)

// 暗夜模式
const isDarkMode = ref(false)

// 侧栏 & 顶部
const activeMenu = ref('overview')
const contentView = ref('overview')
const searchKeyword = ref('')
const overviewSection = ref(null)
const logsSection = ref(null)
const panelTitleMap = {
  user: '用户管理',
  sensitive: '敏感词管理',
  segmentation: '分词管理',
  logs: '系统日志',
  'server-monitor': '服务器监控',
  'data-service-monitor': '数据服务监控'
}
const panelComponents = {
  user: UserManagement,
  sensitive: SensitiveWordsManagement,
  segmentation: SegmentationWordsManagement,
  logs: SystemLogsManagement,
  'server-monitor': ServerMonitor,
  'data-service-monitor': DataServiceMonitor
}
const activePanelComponent = computed(() => panelComponents[contentView.value] || null)
const panelTitle = computed(() => panelTitleMap[contentView.value] || '系统管理')

const versionInfo = ref({
  version: 'v1.0.0',
  releaseDate: '2025-12-05',
  description: 'SchQueryAI 管理台聚焦对话、治理与客服运营。'
})

const capabilityHighlights = [
  {
    title: '对话引擎',
    items: ['多轮上下文', '知识检索', '安全审查']
  },
  {
    title: '运营治理',
    items: ['用户与权限', '敏感词与分词', '客服协同']
  },
  {
    title: '数据洞察',
    items: ['实时指标', '会话记录', '操作审计']
  }
]

// 系统状态
const apiStatus = ref({ type: 'success', text: '正常' })
const dbStatus = ref({ type: 'success', text: '正常' })
const redisStatus = ref({ type: 'warning', text: '延迟较高' })
const systemLoad = ref(45)

// 操作日志
const recentLogs = ref([])

// 访问量统计和最近登录
const visitChartRef = ref(null)
const dailyVisitStats = ref([])
const recentLogins = ref([])
let visitChart = null

// 客服消息
const customerServiceSessions = ref([])
const pendingSessionCount = ref(0)
const unreadMessageCount = ref(0)
const showCustomerServiceModal = ref(false)
const selectedSession = ref(null)
const customerServiceLoading = ref(false)

const scrollToSection = (sectionRef) => {
  nextTick(() => {
    sectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

const handleMenuSelect = (key) => {
  activeMenu.value = key
  // 监控子菜单项在右侧展示
  if (['user', 'sensitive', 'segmentation', 'logs', 'server-monitor', 'data-service-monitor'].includes(key)) {
    contentView.value = key
    return
  }
  contentView.value = 'overview'
  switch (key) {
    case 'overview':
      scrollToSection(overviewSection)
      break
    case 'customer':
      goToCustomerServiceManagement()
      break
    default:
      break
  }
}

// 返回聊天界面
const goBack = () => {
  router.push('/chat')
}

// 切换暗夜模式
const toggleDarkMode = () => {
  isDarkMode.value = !isDarkMode.value
  const html = document.documentElement
  if (isDarkMode.value) {
    html.setAttribute('data-theme', 'dark')
    localStorage.setItem('theme', 'dark')
  } else {
    html.removeAttribute('data-theme')
    localStorage.setItem('theme', 'light')
  }
  // 发布全局事件，让其他页面也能冬撕
  window.dispatchEvent(new CustomEvent('theme-change', { detail: { isDark: isDarkMode.value } }))
}

// 导航到各个管理页面
const goToUserManagement = () => {
  contentView.value = 'user'
  activeMenu.value = 'user'
}

const goToSensitiveWords = () => {
  contentView.value = 'sensitive'
  activeMenu.value = 'sensitive'
}

const goToSegmentation = () => {
  contentView.value = 'segmentation'
  activeMenu.value = 'segmentation'
}

const backToOverview = () => {
  contentView.value = 'overview'
  activeMenu.value = 'overview'
  scrollToSection(overviewSection)
}

// 刷新系统状态
const refreshSystemStatus = async () => {
  try {
    // TODO: 调用实际的系统状态API
    await new Promise(resolve => setTimeout(resolve, 1000))

    // 模拟更新状态
    apiStatus.value = { type: 'success', text: '正常' }
    dbStatus.value = { type: 'success', text: '正常' }
    redisStatus.value = { type: 'success', text: '正常' }
    systemLoad.value = Math.floor(Math.random() * 30 + 40)

    ElMessage.success('系统状态已更新')
  } catch (error) {
    console.error('刷新系统状态失败:', error)
    ElMessage.error('刷新系统状态失败')
  }
}

// 获取负载颜色
const getLoadColor = (load) => {
  if (load < 50) return '#67C23A'
  if (load < 80) return '#E6A23C'
  return '#F56C6C'
}

// 时间格式化
const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

// 清除系统缓存
const clearCache = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要清除系统缓存吗？这可能会影响系统性能。',
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // TODO: 调用清除缓存API
    await new Promise(resolve => setTimeout(resolve, 1000))

    ElMessage.success('系统缓存已清除')
    // 成功时记录操作日志并刷新统计数据
    console.log('[操作日志] 管理员清除系统缓存')
    loadDashboardStats()
    loadRecentLogs()
  } catch {
    // 用户取消操作
  }
}

// 导出系统数据
const exportData = async () => {
  try {
    ElMessage.info('数据导出功能开发中...')
    // TODO: 实现数据导出功能
    console.log('[操作日志] 管理员导出系统数据')
    // 同时刷新统计数据
    loadDashboardStats()
    loadRecentLogs()
  } catch (error) {
    console.error('数据导出失败:', error)
    ElMessage.error('数据导出失败')
  }
}

// 备份数据库
const backupData = async () => {
  try {
    ElMessage.info('数据库备份功能开发中...')
    // TODO: 实现数据库备份功能
    console.log('[操作日志] 管理员执行数据库备份')
    // 同时刷新统计数据
    loadDashboardStats()
    loadRecentLogs()
  } catch (error) {
    console.error('数据库备份失败:', error)
    ElMessage.error('数据库备份失败')
  }
}

// 查看系统日志
const showSystemLogs = () => {
  ElMessage.info('系统日志功能开发中...')
  // TODO: 实现系统日志页面
}

// 获取最近操作日志
const loadRecentLogs = async () => {
  try {
    const response = await operationLogApi.getRecentLogs(20)
    console.log('操作日志响应:', response)
    if (response.code === 200) {
      recentLogs.value = response.data || []
      console.log('操作日志数据:', recentLogs.value)
    } else {
      console.warn('获取操作日志失败', response.message)
    }
  } catch (error) {
    console.error('获取操作日志失败:', error)
  }
}

// 页面加载时获取数据
onMounted(() => {
  console.log('[SystemManagement] 页面已加载，开始初始化...')
  
  // 初始化主题
const savedTheme = localStorage.getItem('theme')
  if (savedTheme === 'dark') {
    isDarkMode.value = true
    document.documentElement.setAttribute('data-theme', 'dark')
  }
  
  // 检查登录状态
  const token = localStorage.getItem('token')
  if (!token) {
    console.error('[SystemManagement] 未找到token，请先登录')
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  
  console.log('[SystemManagement] Token存在，开始加载数据')
  
  loadDashboardStats()
  loadRecentLogs()
  loadCustomerServiceData()
  refreshSystemStatus()
  loadVisitStats()
  loadRecentLogins()
  
  console.log('[SystemManagement] 所有加载函数已调用')
})

// 组件卸载时销毁图表
onUnmounted(() => {
  if (visitChart) {
    visitChart.dispose()
    visitChart = null
  }
})

// 获取仪表板统计数据
const loadDashboardStats = async () => {
  try {
    const response = await userApi.getDashboardStats()
    if (response.code === 200) {
      totalUsers.value = response.data.totalUsers
      todayNewUsers.value = response.data.todayNewUsers
      activeUsers.value = response.data.activeUsers
      adminCount.value = response.data.adminCount
    }
    
    // 获取敏感词统计数据
    await loadSensitiveWordsStats()
    // 获取分词统计数据
    await loadSegmentationWordsStats()
  } catch (error) {
    console.error('获取仪表板统计数据失败:', error)
  }
}

// 获取敏感词统计数据
const loadSensitiveWordsStats = async () => {
  try {
    const response = await request.get('/admin/UGC/sensitive/stats')
    if (response.code === 200) {
      totalSensitiveWords.value = response.data.total || 0
      todayNewSensitiveWords.value = response.data.todayNew || 0
    }
  } catch (error) {
    console.error('获取敏感词统计数据失败:', error)
  }
}

// 获取分词统计数据
const loadSegmentationWordsStats = async () => {
  try {
    const response = await request.get('/admin/UGC/segmentation/stats')
    if (response.code === 200) {
      totalSegmentations.value = response.data.total || 0
      todayNewSegmentations.value = response.data.todayNew || 0
    }
  } catch (error) {
    console.error('获取分词统计数据失败:', error)
  }
}

// 加载客服消息数据
const loadCustomerServiceData = async () => {
  try {
    customerServiceLoading.value = true
    const token = localStorage.getItem('token')
    
    console.log('[SystemManagement] 开始加载客服消息数据...')
    
    // 调用后端 API 获取客服统计信息
    const response = await fetch('/api/customer-service/stats', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })

    console.log('[SystemManagement] stats API 响应状态:', response.status)
    
    if (response.ok) {
      const result = await response.json()
      console.log('[SystemManagement] stats API 返回数据:', result)
      
      if (result.code === 200 && result.data) {
        // 使用返回的数据
        pendingSessionCount.value = result.data.pendingCount || 0
        // 不直接使用后端的unreadCount，等获取会话列表后再计算
        
        console.log('[SystemManagement] 待处理:', pendingSessionCount.value)
        
        // 获取会话列表（使用userSessions字段）
        if (result.data.userSessions && result.data.userSessions.length > 0) {
          customerServiceSessions.value = result.data.userSessions
          console.log('[SystemManagement] 会话数量:', customerServiceSessions.value.length)
          // 从会话列表计算总未读数
          unreadMessageCount.value = customerServiceSessions.value.reduce((total, session) => {
            return total + (session.unreadCount || 0)
          }, 0)
          console.log('[SystemManagement] 计算未读数:', unreadMessageCount.value)
        } else {
          // 如果stats接口没有返回userSessions，再调用pending-sessions接口
          console.log('[SystemManagement] userSessions为空，调用pending-sessions接口...')
          const sessionsResponse = await fetch('/api/customer-service/pending-sessions', {
            method: 'GET',
            headers: {
              'Authorization': `Bearer ${token}`,
              'Content-Type': 'application/json'
            }
          })

          console.log('[SystemManagement] pending-sessions API 响应状态:', sessionsResponse.status)
          
          if (sessionsResponse.ok) {
            const sessionsResult = await sessionsResponse.json()
            console.log('[SystemManagement] pending-sessions API 返回数据:', sessionsResult)
            
            if (sessionsResult.code === 200) {
              customerServiceSessions.value = sessionsResult.data || []
              console.log('[SystemManagement] 会话数量:', customerServiceSessions.value.length)
              // 从会话列表计算总未读数
              unreadMessageCount.value = customerServiceSessions.value.reduce((total, session) => {
                return total + (session.unreadCount || 0)
              }, 0)
              console.log('[SystemManagement] 计算未读数:', unreadMessageCount.value)
            } else {
              console.warn('[SystemManagement] pending-sessions API 返回错误:', sessionsResult.msg)
              unreadMessageCount.value = 0
            }
          } else {
            console.error('[SystemManagement] pending-sessions API 请求失败:', sessionsResponse.statusText)
            unreadMessageCount.value = 0
          }
        }
      } else {
        console.warn('[SystemManagement] stats API 返回错误:', result.msg)
      }
    } else {
      console.error('[SystemManagement] stats API 请求失败:', response.statusText)
    }
  } catch (error) {
    console.error('[SystemManagement] 加载客服消息数据失败:', error)
    ElMessage.error('加载客服数据失败，请检查后端服务')
  } finally {
    customerServiceLoading.value = false
  }
}

const openSession = async (session) => {
  try {
    // 先标记为已读
    const token = localStorage.getItem('token')
    if (session.unreadCount > 0) {
      const response = await fetch(`/api/customer-service/mark-read-by-user?userId=${session.userId}&senderType=1`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      })
      
      if (response.ok) {
        // 本地更新会话未读数
        const unreadDelta = session.unreadCount
        session.unreadCount = 0
        unreadMessageCount.value = Math.max(0, unreadMessageCount.value - unreadDelta)
        console.log('[SystemManagement] 已标记为已读, 未读数:', unreadMessageCount.value)
      }
    }
    
    // 跳转到客服管理页面
    router.push('/admin/customer-service').then(() => {
      setTimeout(() => {
        window.dispatchEvent(new CustomEvent('select-customer-session', {
          detail: { sessionId: session.id, userId: session.userId }
        }))
      }, 200)
    })
  } catch (error) {
    console.error('[SystemManagement] 打开会话失败:', error)
    ElMessage.error('打开会话失败')
  }
}

// 导航到客服消息管理页面
const goToCustomerServiceManagement = () => {
  router.push('/admin/customer-service')
}

// 图表时间范围
const chartTimeRange = ref('15d')

// 时间范围切换处理
const onChartTimeRangeChange = () => {
  loadVisitStats()
}

// 加载访问量统计
const loadVisitStats = async () => {
  try {
    let response
    if (chartTimeRange.value === '12h') {
      response = await userApi.getHourlyVisitStats(12)
    } else if (chartTimeRange.value === '15d') {
      response = await userApi.getDailyVisitStats(15)
    } else {
      response = await userApi.getDailyVisitStats(30)
    }
    
    if (response.code === 200) {
      dailyVisitStats.value = response.data || []
      // 初始化图表
      nextTick(() => {
        initVisitChart()
      })
    }
  } catch (error) {
    console.error('获取访问量统计失败:', error)
  }
}

// 初始化访问量图表
const initVisitChart = () => {
  if (!visitChartRef.value) return
  
  if (visitChart) {
    visitChart.dispose()
  }
  
  visitChart = echarts.init(visitChartRef.value)
  
  const dates = dailyVisitStats.value.map(item => item.date)
  const counts = dailyVisitStats.value.map(item => item.count)
  
  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDarkMode.value ? '#1f2937' : '#fff',
      borderColor: isDarkMode.value ? '#374151' : '#e5e7eb',
      textStyle: {
        color: isDarkMode.value ? '#e5e7eb' : '#333'
      }
    },
    grid: {
      left: '1%',
      right: '2%',
      bottom: '1%',
      top: '5%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: {
        show: false
      },
      axisTick: {
        show: false
      },
      axisLabel: {
        color: isDarkMode.value ? '#9ca3af' : '#999',
        margin: 12,
        interval: 0,
        rotate: 45,
        fontSize: 11
      },
      splitLine: {
        show: true,
        interval: 0,
        lineStyle: {
          color: isDarkMode.value ? '#374151' : '#e8e8e8',
          type: 'solid'
        }
      }
    },
    yAxis: {
      type: 'value',
      axisLine: {
        show: false
      },
      axisTick: {
        show: false
      },
      axisLabel: {
        color: isDarkMode.value ? '#9ca3af' : '#999'
      },
      splitLine: {
        show: true,
        lineStyle: {
          color: isDarkMode.value ? '#374151' : '#e8e8e8',
          type: 'solid'
        }
      }
    },
    series: [{
      name: '访问量',
      type: 'line',
      smooth: true,
      data: counts,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(59, 130, 246, 0.3)' },
          { offset: 1, color: 'rgba(59, 130, 246, 0.05)' }
        ])
      },
      lineStyle: {
        color: '#3B82F6',
        width: 2
      },
      itemStyle: {
        color: '#3B82F6'
      }
    }]
  }
  
  visitChart.setOption(option)
  
  // 窗口大小变化时重新调整图表
  window.addEventListener('resize', () => {
    visitChart?.resize()
  })
}

// 加载最近登录记录
const loadRecentLogins = async () => {
  try {
    const response = await userApi.getRecentLogins(8)
    if (response.code === 200) {
      recentLogins.value = response.data || []
    }
  } catch (error) {
    console.error('获取最近登录记录失败:', error)
  }
}

</script>


<style scoped>
.ruoyi-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: #f5f7fa;
  color: #111827;
}

[data-theme="dark"] .ruoyi-shell {
  background: #0f172a;
  color: #e5e7eb;
}

.ruoyi-shell__sidebar {
  width: 230px;
  background: #ffffff;
  border-right: 1px solid #e5e7eb;
  color: #1f2937;
  padding: 16px 12px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}

[data-theme="dark"] .ruoyi-shell__sidebar {
  background: #111827;
  border-right-color: #1f2937;
  color: #e5e7eb;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 4px 12px;
  border-bottom: 1px solid #e5e7eb;
}

[data-theme="dark"] .sidebar-brand {
  border-bottom-color: #1f2937;
}

.brand-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1 0%, #0ea5e9 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: white;
  letter-spacing: 1px;
}

.brand-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.brand-version {
  font-size: 12px;
  color: #6b7280;
}

.sidebar-menu {
  border-right: none;
  background: transparent;
  flex-grow: 1;
}

.sidebar-menu :deep(.el-menu-item) {
  border-radius: 10px;
  margin-bottom: 6px;
  color: #4b5563;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: #eef2ff;
  color: #111827;
}

[data-theme="dark"] .sidebar-menu :deep(.el-menu-item) {
  color: #e5e7eb;
}

[data-theme="dark"] .sidebar-menu :deep(.el-menu-item.is-active) {
  background: #1e293b;
  color: #f9fafb;
}

[data-theme="dark"] .brand-name {
  color: #f9fafb;
}

[data-theme="dark"] .brand-version {
  color: #9ca3af;
}

.ruoyi-shell__main {
  flex: 1;
  height: 100vh;
  padding: 16px;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  overflow-y: auto;
}

[data-theme="dark"] .ruoyi-shell__main {
  background: #0f172a;
}

.shell-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.shell-toolbar h2 {
  margin: 6px 0 0;
  font-size: 20px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-search {
  width: 240px;
}

.toolbar-icon {
  padding: 6px;
}

.shell-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.shell-content.panel-mode {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0;
  overflow: hidden;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 10px 24px rgba(31, 45, 61, 0.06);
}

[data-theme="dark"] .panel-header {
  background: #111827;
  border-color: #1f2937;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.45);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #111827;
}

[data-theme="dark"] .panel-title {
  color: #e5e7eb;
}

.panel-body {
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 10px 24px rgba(31, 45, 61, 0.06);
  padding: 10px;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

[data-theme="dark"] .panel-body {
  background: #111827;
  border-color: #1f2937;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.45);
}

.hero-card {
  display: flex;
  gap: 18px;
  padding: 18px;
  border-radius: 14px;
  background: linear-gradient(135deg, #0ea5e9 0%, #6366f1 100%);
  box-shadow: 0 12px 30px rgba(99, 102, 241, 0.2);
  color: #f8fafc;
  overflow: hidden;
  align-items: center;
}

[data-theme="dark"] .hero-card {
  background: linear-gradient(135deg, #1f2937 0%, #0b1224 100%);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.6);
}

.hero-card__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 60%;
}

.hero-card__tech {
  width: 280px;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
}

.hero-tag {
  margin: 0;
  font-size: 14px;
  color: #dbeafe;
}

.hero-desc {
  margin: 12px 0 16px;
  line-height: 1.6;
  color: #e5e7eb;
  max-width: 640px;
}

[data-theme="dark"] .hero-desc {
  color: #c4c4c4;
}

.hero-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.hero-meta .version {
  font-weight: 600;
  color: #e0f2fe;
}

.hero-actions {
  display: flex;
  gap: 12px;
}

.hero-card__main h3 {
  margin: 6px 0 4px;
  color: #fff;
  letter-spacing: 0.3px;
}

.hero-card__tech h4 {
  margin: 0 0 10px;
  color: #fff;
}

.hero-card__tech ul {
  padding-left: 16px;
  margin: 0;
  color: #e5e7eb;
  line-height: 1.6;
}

.hero-card__tech .tech-title {
  color: #f8fafc;
}

:deep(.hero-card .el-tag) {
  background: rgba(255, 255, 255, 0.12);
  border: none;
  color: #e5e7eb;
}

.tech-columns {
  display: flex;
  gap: 20px;
}

.tech-columns > div {
  flex: 1;
  min-width: 0;
}

.tech-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.tech-columns ul {
  padding-left: 16px;
  margin: 0;
  color: #e5e7eb;
  line-height: 1.6;
}

.panel-group {
  margin-top: 6px;
}

.panel-group :deep(.el-col) {
  display: flex;
}

.stat-card {
  border-radius: 12px;
  border: none;
  background: #fff;
  flex: 1;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
}

[data-theme="dark"] .stat-card {
  background: #1f2025;
  border: 1px solid #2f2f2f;
  color: #e9e9e9;
}

.stat-card__icon {
  width: 60px;
  height: 60px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.stat-card__icon.primary {
  background: rgba(64, 158, 255, 0.15);
  color: #409EFF;
}

.stat-card__icon.success {
  background: rgba(103, 194, 58, 0.15);
  color: #67C23A;
}

.stat-card__icon.warning {
  background: rgba(230, 162, 60, 0.2);
  color: #E6A23C;
}

.stat-card__icon.info {
  background: rgba(64, 158, 255, 0.1);
  color: #409EFF;
}

.stat-card__label {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.stat-card__value {
  margin: 0;
  font-size: 30px;
  font-weight: 600;
}

.stat-card__desc {
  margin: 4px 0 0;
  color: #8c8c8c;
  font-size: 12px;
}
.monitor-row .ruoyi-card,
.operations-row .ruoyi-card {
  margin-bottom: 0;
}

.monitor-row :deep(.el-col),
.operations-row :deep(.el-col) {
  display: flex;
}

.monitor-row .ruoyi-card,
.operations-row .ruoyi-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.ruoyi-card {
  border-radius: 12px;
  border: none;
  background: #fff;
  box-shadow: 0 8px 20px rgba(31, 45, 61, 0.08);
}

.ruoyi-card :deep(.el-card__header) {
  border-bottom: 1px solid #f0f0f0;
  padding: 14px 16px;
}

.ruoyi-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 100%;
}

[data-theme="dark"] .ruoyi-card {
  background: #1f2025;
  border: 1px solid #2c2c2c;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
}

[data-theme="dark"] .ruoyi-card :deep(.el-card__header) {
  border-bottom-color: #2f2f2f;
}

.monitor-row .system-status {
  padding: 10px 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-label {
  font-weight: 600;
  color: #3a3a3a;
}

[data-theme="dark"] .status-label {
  color: #e0e0e0;
}

.operation-logs {
  max-height: 320px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.log-item {
  padding: 12px 14px;
  border-radius: 12px;
  background: #f9fbff;
  border-left: 3px solid #409EFF;
  border: 1px solid #eef3ff;
}

.log-item__header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  font-size: 12px;
}

.log-operator {
  font-weight: 600;
  color: #409EFF;
}

.log-action {
  color: #67C23A;
}

.log-time {
  margin-left: auto;
  color: #909399;
}

.log-detail {
  margin: 0;
  font-size: 12px;
  color: #606266;
}

[data-theme="dark"] .log-item {
  background: #24252a;
  border-color: #323339;
}

[data-theme="dark"] .log-detail {
  color: #d0d0d0;
}

.customer-card .service-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-box {
  padding: 16px;
  border-radius: 12px;
  background: #f5f7fa;
  text-align: center;
}

[data-theme="dark"] .stat-box {
  background: #2a2c33;
}

.stat-number {
  font-size: 26px;
  font-weight: 600;
  color: #409EFF;
}

.stat-name {
  font-size: 12px;
  color: #909399;
}

.sessions-wrapper {
  border: 1px dashed #e4e7ed;
  border-radius: 12px;
  padding: 12px;
  min-height: 140px;
}

[data-theme="dark"] .sessions-wrapper {
  border-color: #3c3c3c;
}

.sessions-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.session-item {
  padding: 12px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #eef2f8;
  cursor: pointer;
  transition: all 0.2s ease;
}

.session-item:hover {
  border-color: #409EFF;
  background: #f0f6ff;
}

[data-theme="dark"] .session-item {
  background: #1f2025;
  border-color: #31323b;
}

.session-item__header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
  font-size: 13px;
}

.customer-service-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}

[data-theme="dark"] .customer-service-actions {
  border-top-color: #2f2f2f;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.modal-content {
  padding: 20px 0;
}

.modal-content .session-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 12px;
}

.session-messages {
  max-height: 400px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message {
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
}

.message.user-message {
  background: #e6f7ff;
  align-self: flex-end;
  max-width: 80%;
}

.message.admin-message {
  background: #f6ffed;
  align-self: flex-start;
  max-width: 80%;
}

[data-theme="dark"] .message.user-message {
  background: rgba(64, 158, 255, 0.2);
  color: #d1e8ff;
}

[data-theme="dark"] .message.admin-message {
  background: rgba(103, 194, 58, 0.2);
  color: #def7d8;
}

.message-time {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: #a0a0a0;
}

:deep(.el-dialog) {
  border-radius: 12px;
}

[data-theme="dark"] :deep(.el-dialog) {
  background: #1f1f1f;
  color: #f2f2f2;
}

@media (max-width: 992px) {
  .ruoyi-shell {
    flex-direction: column;
  }

  .ruoyi-shell__sidebar {
    width: 100%;
    flex-direction: row;
    flex-wrap: wrap;
    gap: 10px;
  }

  .sidebar-menu {
    width: 100%;
  }

  .hero-card {
    flex-direction: column;
  }

  .hero-card__tech {
    width: 100%;
  }

  .hero-card__main {
    max-width: 100%;
  }

  .toolbar-search {
    width: 160px;
  }
}

@media (max-width: 768px) {
  .shell-toolbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .toolbar-actions {
    width: 100%;
  }

  .toolbar-search {
    flex: 1;
  }

  .operations-row .ruoyi-card,
  .monitor-row .ruoyi-card {
    margin-bottom: 16px;
  }

  .quick-actions {
    flex-direction: column;
  }
}

/* 访问量图表 */
.chart-row {
  margin-bottom: 20px;
}

.chart-card {
  border-radius: 12px;
}

.chart-card :deep(.el-card__header) {
  padding: 14px 20px;
  border-bottom: 1px solid #f0f0f0;
}

[data-theme="dark"] .chart-card :deep(.el-card__header) {
  border-bottom-color: #1f2937;
}

.chart-card .ruoyi-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-card .ruoyi-card__header > span {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

[data-theme="dark"] .chart-card .ruoyi-card__header > span {
  color: #f3f4f6;
}

.chart-actions {
  display: flex;
  align-items: center;
}

.chart-actions .el-radio-group {
  --el-radio-button-checked-bg-color: #6366f1;
  --el-radio-button-checked-border-color: #6366f1;
  --el-radio-button-checked-text-color: #fff;
}

.chart-actions :deep(.el-radio-button__inner) {
  padding: 6px 14px;
  font-size: 12px;
  border-radius: 0;
}

.chart-actions :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-radius: 6px 0 0 6px;
}

.chart-actions :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 0 6px 6px 0;
}

[data-theme="dark"] .chart-actions .el-radio-group {
  --el-fill-color-blank: #1f2937;
  --el-border-color: #374151;
  --el-text-color-regular: #9ca3af;
}

.visit-chart {
  width: 100%;
  height: 280px;
}

/* 最近访问IP */
.recent-ip-card {
  border-radius: 12px;
}

.recent-ip-list {
  max-height: 320px;
  overflow-y: auto;
}

.ip-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.ip-item:last-child {
  border-bottom: none;
}

[data-theme="dark"] .ip-item {
  border-bottom-color: #1f2937;
}

.ip-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ip-address {
  font-family: 'Monaco', 'Consolas', monospace;
  font-size: 13px;
  color: #333;
  font-weight: 500;
}

[data-theme="dark"] .ip-address {
  color: #e5e7eb;
}

.ip-location {
  font-size: 12px;
  color: #999;
}

[data-theme="dark"] .ip-location {
  color: #6b7280;
}

.ip-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.ip-user {
  font-size: 12px;
  color: #6366f1;
  font-weight: 500;
}

.ip-time {
  font-size: 11px;
  color: #999;
}

[data-theme="dark"] .ip-time {
  color: #6b7280;
}

.empty-data {
  text-align: center;
  color: #999;
  padding: 40px 0;
}

[data-theme="dark"] .empty-data {
  color: #6b7280;
}
</style>
