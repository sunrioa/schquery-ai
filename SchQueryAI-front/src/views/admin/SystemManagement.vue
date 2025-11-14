<template>
  <div class="system-management">
    <div class="management-header">
      <h1>系统管理</h1>
      <el-button @click="goBack" type="default">
        <el-icon><ArrowLeft /></el-icon>
        返回聊天
      </el-button>
    </div>

    <div class="management-content">
      <!-- 系统概览统计 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-number">{{ totalUsers }}</div>
              <div class="stat-label">总用户数</div>
            </div>
            <el-icon class="stat-icon"><User /></el-icon>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-number">{{ todayNewUsers }}</div>
              <div class="stat-label">今日新增用户</div>
            </div>
            <el-icon class="stat-icon"><UserFilled /></el-icon>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-number">{{ totalSensitiveWords }}</div>
              <div class="stat-label">敏感词总数</div>
            </div>
            <el-icon class="stat-icon"><Warning /></el-icon>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-number">{{ totalSegmentations }}</div>
              <div class="stat-label">分词总数</div>
            </div>
            <el-icon class="stat-icon"><Collection /></el-icon>
          </el-card>
        </el-col>
      </el-row>

      <!-- 管理功能模块 -->
      <el-row :gutter="20" class="modules-row">
        <el-col :span="8">
          <el-card class="module-card" @click="goToUserManagement">
            <div class="module-content">
              <el-icon class="module-icon"><User /></el-icon>
              <h3>用户管理</h3>
              <p>管理系统用户，包括用户信息、角色分配、状态管理等</p>
              <div class="module-stats">
                <span>活跃用户: {{ activeUsers }}</span>
                <span>管理员: {{ adminCount }}</span>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="8">
          <el-card class="module-card" @click="goToSensitiveWords">
            <div class="module-content">
              <el-icon class="module-icon"><Warning /></el-icon>
              <h3>敏感词管理</h3>
              <p>管理敏感词库，添加、编辑、删除敏感词，配置过滤级别</p>
              <div class="module-stats">
                <span>敏感词: {{ totalSensitiveWords }}</span>
                <span>今日新增: {{ todayNewSensitiveWords }}</span>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="8">
          <el-card class="module-card" @click="goToSegmentation">
            <div class="module-content">
              <el-icon class="module-icon"><Collection /></el-icon>
              <h3>分词管理</h3>
              <p>管理分词词库，优化AI对话的语义理解和回复质量</p>
              <div class="module-stats">
                <span>分词数: {{ totalSegmentations }}</span>
                <span>今日新增: {{ todayNewSegmentations }}</span>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 客服消息卡片 -->
      <el-row :gutter="20" class="customer-service-row">
        <el-col :span="24">
          <el-card class="customer-service-card">
            <template #header>
              <div class="card-header">
                <el-icon class="header-icon"><ChatDotRound /></el-icon>
                <span>客服消息</span>
                <el-badge :value="unreadMessageCount" :max="99" class="badge-item" />
              </div>
            </template>

            <div class="customer-service-content">
              <!-- 统计信息 -->
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

              <!-- 最近会话 -->
              <div class="recent-sessions">
                <div v-if="customerServiceSessions.length === 0" class="empty-sessions">
                  <p>暂无客服消息</p>
                </div>
                <div v-else class="sessions-list">
                  <div v-for="session in customerServiceSessions.slice(0, 3)" :key="session.id" class="session-item" @click="openSession(session)">
                    <div class="session-header">
                      <span class="user-name">{{ session.userName }}</span>
                      <span class="session-time">{{ formatTime(session.lastMessageTime) }}</span>
                    </div>
                    <div class="session-preview">{{ session.lastMessage }}</div>
                    <el-tag v-if="session.unreadCount > 0" type="danger" size="small" class="unread-tag">
                      {{ session.unreadCount }}条未读
                    </el-tag>
                  </div>
                </div>
              </div>

              <!-- 快捷按马 -->
              <div class="service-actions">
                <el-button type="primary" size="small" @click="loadCustomerServiceData">刷新</el-button>
                <el-button type="default" size="small" @click="goToCustomerServiceManagement">查看全部</el-button>
                <el-button type="warning" size="small" @click="testAPI">测试API</el-button>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 客服回复模态框 -->
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
            <div v-for="msg in selectedSession.messages" :key="msg.id" class="message" :class="{ 'user-message': msg.senderType === 1, 'admin-message': msg.senderType === 2 }">
              <span class="message-content">{{ msg.messageContent }}</span>
              <span class="message-time">{{ formatTime(msg.createTime) }}</span>
            </div>
          </div>
        </div>
        <template #footer>
          <el-button @click="showCustomerServiceModal = false">关闭</el-button>
        </template>
      </el-dialog>

      <!-- 系统监控面板 -->
      <el-row :gutter="20" class="monitoring-row">
        <el-col :span="12">
          <el-card class="monitoring-card">
            <template #header>
              <div class="card-header">
                <span>系统状态</span>
                <el-button type="text" @click="refreshSystemStatus">
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

        <el-col :span="12">
          <el-card class="monitoring-card">
            <template #header>
              <span>最近操作日志</span>
            </template>

            <div class="operation-logs">
              <div v-if="recentLogs.length === 0" class="no-logs">
                <p>暂无操作日志</p>
              </div>
              <div v-for="log in recentLogs" :key="log.id" class="log-item">
                <div class="log-header">
                  <span class="log-operator">{{ log.operator }}</span>
                  <span class="log-action">{{ log.action }}</span>
                  <span class="log-time">{{ formatTime(log.timestamp) }}</span>
                </div>
                <div class="log-detail">{{ log.detail }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 快速操作 -->
      <el-card class="quick-actions-card">
        <template #header>
          <span>快速操作</span>
        </template>

        <div class="quick-actions">
          <el-button type="danger" @click="clearCache">
            <el-icon><Delete /></el-icon>
            清除系统缓存
          </el-button>
          <el-button type="warning" @click="exportData">
            <el-icon><Download /></el-icon>
            导出系统数据
          </el-button>
          <el-button type="primary" @click="backupData">
            <el-icon><FolderOpened /></el-icon>
            备份数据库
          </el-button>
          <el-button type="success" @click="showSystemLogs">
            <el-icon><Document /></el-icon>
            查看系统日志
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
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
  ChatDotRound,
  Bell
} from '@element-plus/icons-vue'
import { userApi } from '../../api/user'
import { operationLogApi } from '../../api/operationLog'
import request from '../../api/request'

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

// 系统状态
const apiStatus = ref({ type: 'success', text: '正常' })
const dbStatus = ref({ type: 'success', text: '正常' })
const redisStatus = ref({ type: 'warning', text: '延迟较高' })
const systemLoad = ref(45)

// 操作日志
const recentLogs = ref([])

// 客服消息
const customerServiceSessions = ref([])
const pendingSessionCount = ref(0)
const unreadMessageCount = ref(0)
const showCustomerServiceModal = ref(false)
const selectedSession = ref(null)
const customerServiceLoading = ref(false)

// 返回聊天界面
const goBack = () => {
  router.push('/chat')
}

// 导航到各个管理页面
const goToUserManagement = () => {
  router.push('/admin/user-management')
}

const goToSensitiveWords = () => {
  router.push('/admin/sensitive-words')
}

const goToSegmentation = () => {
  router.push('/admin/segmentation-words')
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
  
  console.log('[SystemManagement] 所有加载函数已调用')
})

// 获取仪表板统计数据
const loadDashboardStats = async () => {
  try {
    // ... existing code ...
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
    const response = await fetch('http://localhost:8080/customer-service/stats', {
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
        unreadMessageCount.value = result.data.unreadCount || 0
        
        console.log('[SystemManagement] 待处理:', pendingSessionCount.value, '未读:', unreadMessageCount.value)
        
        // 获取会话列表（使用userSessions字段）
        if (result.data.userSessions && result.data.userSessions.length > 0) {
          customerServiceSessions.value = result.data.userSessions
          console.log('[SystemManagement] 会话数量:', customerServiceSessions.value.length)
        } else {
          // 如果stats接口没有返回userSessions，再调用pending-sessions接口
          console.log('[SystemManagement] userSessions为空，调用pending-sessions接口...')
          const sessionsResponse = await fetch('http://localhost:8080/customer-service/pending-sessions', {
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
            } else {
              console.warn('[SystemManagement] pending-sessions API 返回错误:', sessionsResult.msg)
            }
          } else {
            console.error('[SystemManagement] pending-sessions API 请求失败:', sessionsResponse.statusText)
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

// 打开客服消息会话
const openSession = (session) => {
  // 跳转到客服管理页面并携带会话信息
  router.push('/admin/customer-service').then(() => {
    // 延迟触发，确保页面已加载
    setTimeout(() => {
      window.dispatchEvent(new CustomEvent('select-customer-session', {
        detail: { sessionId: session.id, userId: session.userId }
      }))
    }, 200)
  })
}

// 导航到客服消息管理页面
const goToCustomerServiceManagement = () => {
  router.push('/admin/customer-service')
}

// 测试API调用
const testAPI = async () => {
  console.log('[TEST] 开始测试API调用...')
  const token = localStorage.getItem('token')
  console.log('[TEST] Token:', token ? '存在' : '不存在')
  
  try {
    console.log('[TEST] 发送请求到: http://localhost:8080/customer-service/stats')
    const response = await fetch('http://localhost:8080/customer-service/stats', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })
    
    console.log('[TEST] 响应状态:', response.status)
    const data = await response.json()
    console.log('[TEST] 响应数据:', data)
    
    ElMessage.success('请查看控制台输出')
  } catch (error) {
    console.error('[TEST] 请求失败:', error)
    ElMessage.error('请求失败: ' + error.message)
  }
}
</script>

<style scoped>
.system-management {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.management-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.management-header h1 {
  margin: 0;
  color: #333;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  cursor: default;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-content {
  z-index: 2;
}

.stat-number {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 5px;
}

.stat-label {
  font-size: 14px;
  color: #666;
}

.stat-icon {
  font-size: 48px;
  color: rgba(64, 158, 255, 0.2);
  position: absolute;
  right: 20px;
  top: 50%;
  transform: translateY(-50%);
}

.modules-row {
  margin-bottom: 20px;
}

.module-card {
  cursor: pointer;
  transition: all 0.3s ease;
  height: 200px;
}

.module-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.module-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.module-icon {
  font-size: 48px;
  color: #409EFF;
  margin-bottom: 15px;
}

.module-content h3 {
  margin: 0 0 10px 0;
  color: #333;
  font-size: 18px;
}

.module-content p {
  margin: 0 0 15px 0;
  color: #666;
  font-size: 14px;
  line-height: 1.5;
  flex-grow: 1;
}

.module-stats {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
}

.monitoring-row {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.system-status {
  space-y: 15px;
}

.status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.status-label {
  color: #333;
  font-weight: 500;
}

.operation-logs {
  max-height: 200px;
  overflow-y: auto;
}

.log-item {
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.log-item:last-child {
  border-bottom: none;
}

.log-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 5px;
  font-size: 13px;
}

.log-operator {
  font-weight: bold;
  color: #409EFF;
}

.log-action {
  color: #67C23A;
}

.log-time {
  color: #999;
}

.log-detail {
  font-size: 12px;
  color: #666;
  line-height: 1.4;
}

.quick-actions-card {
  margin-bottom: 20px;
}

.quick-actions {
  display: flex;
  gap: 15px;
  flex-wrap: wrap;
}

.monitoring-card {
  height: auto;
  max-height: 320px;
}

.system-status {
  min-height: 240px;
  space-y: 15px;
}

.operation-logs {
  max-height: 240px;
  min-height: 150px;
  overflow-y: auto;
  padding: 10px;
  box-sizing: border-box;
}

.no-logs {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 20px;
  color: #999;
  font-size: 14px;
}

.log-item {
  padding: 12px;
  margin-bottom: 8px;
  background-color: #f9f9f9;
  border-left: 3px solid #409EFF;
  border-radius: 2px;
  box-sizing: border-box;
}

.log-item:last-child {
  margin-bottom: 5px;
}

.log-header {
  display: flex;
  gap: 15px;
  margin-bottom: 8px;
  font-size: 12px;
}

.log-operator {
  padding: 2px 6px;
  background-color: #e6f7ff;
  border-radius: 2px;
  color: #0050b3;
  font-weight: 500;
}

.log-action {
  padding: 2px 6px;
  background-color: #f6ffed;
  border-radius: 2px;
  color: #274e20;
  font-weight: 500;
}

.log-time {
  color: #999;
  margin-left: auto;
}

.log-detail {
  font-size: 12px;
  color: #666;
  padding-left: 5px;
  line-height: 1.4;
  word-wrap: break-word;
  word-break: break-word;
  white-space: normal;
}

@media (max-width: 768px) {
  .management-header {
    flex-direction: column;
    gap: 15px;
    align-items: flex-start;
  }

  .stats-row :deep(.el-col) {
    margin-bottom: 10px;
  }

  .modules-row :deep(.el-col) {
    margin-bottom: 15px;
  }

  .monitoring-row :deep(.el-col) {
    margin-bottom: 15px;
  }

  .quick-actions {
    justify-content: center;
  }

  .module-card {
    height: auto;
    min-height: 200px;
  }
}

/* 客服消息卡片样式 */
.customer-service-row {
  margin-bottom: 20px;
}

.customer-service-card {
  position: relative;
  overflow: hidden;
}

.customer-service-card :deep(.el-card__header) {
  border-bottom-color: #ebeef5;
  padding: 15px 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
}

.header-icon {
  color: #409EFF;
  font-size: 20px;
}

.badge-item {
  margin-left: auto;
}

.customer-service-content {
  padding: 20px 0;
}

.service-stats {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
  padding: 0 20px;
}

.stat-box {
  flex: 1;
  padding: 15px;
  background-color: #f5f7fa;
  border-radius: 6px;
  text-align: center;
}

.stat-number {
  font-size: 28px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 5px;
}

.stat-name {
  font-size: 12px;
  color: #909399;
}

.recent-sessions {
  margin-bottom: 20px;
  padding: 0 20px;
}

.empty-sessions {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100px;
  color: #909399;
  font-size: 14px;
}

.sessions-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.session-item {
  padding: 12px;
  background-color: #f9f9f9;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.session-item:hover {
  background-color: #f0f5ff;
  border-color: #409EFF;
}

.session-item .session-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.user-name {
  font-weight: 600;
  color: #333;
  font-size: 14px;
}

.session-time {
  color: #999;
  font-size: 12px;
}

.session-preview {
  color: #666;
  font-size: 12px;
  margin-bottom: 8px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.unread-tag {
  align-self: flex-start;
}

.service-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 0 20px;
  border-top: 1px solid #ebeef5;
  padding-top: 15px;
}

.modal-content {
  padding: 20px 0;
}

.modal-content .session-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.session-messages {
  max-height: 400px;
  overflow-y: auto;
  padding: 0 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message {
  padding: 10px;
  border-radius: 6px;
  font-size: 14px;
  line-height: 1.5;
}

.message.user-message {
  background-color: #e6f7ff;
  color: #0050b3;
  align-self: flex-end;
  max-width: 80%;
}

.message.admin-message {
  background-color: #f6ffed;
  color: #274e20;
  align-self: flex-start;
  max-width: 80%;
}

.message-content {
  display: block;
  margin-bottom: 5px;
}

.message-time {
  font-size: 12px;
  opacity: 0.7;
}
</style>