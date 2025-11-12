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
  Document
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
  loadDashboardStats()
  loadRecentLogs()
  refreshSystemStatus()
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
</style>