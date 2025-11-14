<template>
  <div class="admin-customer-service">
    <el-container class="service-container">
      <!-- 头部 -->
      <el-header class="service-header">
        <div class="header-left">
          <el-button type="text" @click="goBack" class="back-btn">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <h2>客服消息管理</h2>
        </div>
        <div class="header-right">
          <el-input
              v-model="searchKeyword"
              placeholder="搜索用户名或主题..."
              style="width: 250px"
              @input="handleSearch"
          />
          <el-select v-model="filterStatus" placeholder="筛选状态" @change="handleStatusFilter">
            <el-option label="全部" value="" />
            <el-option label="待处理" value="pending" />
            <el-option label="已处理" value="completed" />
          </el-select>
        </div>
      </el-header>

      <!-- 主区域 -->
      <el-container class="service-content">
        <!-- 会话列表侧边栏 -->
        <el-aside width="300px" class="sessions-sidebar">
          <div class="sidebar-header">
            <h3>客服会话</h3>
            <el-badge :value="pendingCount" :max="99" />
          </div>
          <div class="sessions-list" v-loading="loading">
            <div v-if="filteredSessions.length === 0" class="empty-sessions">
              <p>暂无会话</p>
            </div>
            <div
                v-for="session in filteredSessions"
                :key="session.id"
                class="session-item"
                :class="{ active: currentSession?.id === session.id }"
                @click="selectSession(session)"
            >
              <div class="session-info">
                <div class="user-name">{{ session.userName }}</div>
                <div class="session-topic">{{ session.topic }}</div>
                <div class="session-time">{{ formatTime(session.lastMessageTime) }}</div>
              </div>
              <el-badge v-if="session.unreadCount > 0" :value="session.unreadCount" class="unread-badge" />
            </div>
          </div>
        </el-aside>

        <!-- 聊天区域 -->
        <el-main class="chat-area">
          <div v-if="!currentSession" class="empty-state">
            <el-empty description="选择一个会话开始处理" :image-size="120">
              <el-button type="primary" @click="refreshSessions">刷新会话列表</el-button>
            </el-empty>
          </div>

          <div v-else class="chat-wrapper">
            <!-- 会话头部 -->
            <div class="chat-header">
              <div class="header-info">
                <h3>{{ currentSession.userName }}</h3>
                <span class="topic-tag">{{ currentSession.topic }}</span>
                <span class="status-tag" :class="{ 'is-completed': currentSession.status === 'completed' }">
                  {{ currentSession.status === 'completed' ? '已完成' : '处理中' }}
                </span>
              </div>
              <div class="header-actions">
                <el-button type="success" size="small" @click="markAsResolved" v-if="currentSession.status !== 'completed'">
                  标记为已处理
                </el-button>
                <el-button type="danger" size="small" @click="deleteSession">删除会话</el-button>
              </div>
            </div>

            <!-- 消息区域 -->
            <div class="messages-area" ref="messagesContainer">
              <div v-for="msg in sortedMessages" :key="msg.id" class="message-item" :class="{ 'admin-msg': msg.senderType === 2, 'user-msg': msg.senderType === 1 }">
                <div class="message-avatar">
                  <el-avatar :size="36" :src="msg.senderType === 1 ? getUserAvatar() : getAdminAvatar()" />
                </div>
                <div class="message-content">
                  <div class="sender-name">{{ msg.senderType === 1 ? currentSession.userName : '管理员' }}</div>
                  <div class="message-text">{{ msg.messageContent }}</div>
                  <div class="message-time">{{ formatTime(msg.createTime) }}</div>
                </div>
              </div>
            </div>

            <!-- 回复区域 -->
            <div class="reply-area" v-if="currentSession.status !== 'completed'">
              <el-input
                  ref="replyInput"
                  v-model="replyMessage"
                  type="textarea"
                  :rows="3"
                  placeholder="输入您的回复..."
                  @keydown.enter.prevent="handleEnterKey"
                  resize="none"
              />
              <div class="reply-actions">
                <el-button type="primary" @click="sendReply" :loading="replySending" :disabled="!replyMessage.trim()">
                  发送回复
                </el-button>
                <el-button @click="replyMessage = ''">清除</el-button>
              </div>
            </div>
            <div v-else class="completed-notice">
              <el-alert
                  title="此会话已完成"
                  type="success"
                  description="用户发送新消息后会话将自动重新激活"
                  :closable="false"
              />
            </div>
          </div>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Upload } from '@element-plus/icons-vue'
import { useUserStore } from '../../stores/userStore'

const router = useRouter()
const userStore = useUserStore()

// 状态变量
const sessions = ref([])
const currentSession = ref(null)
const replyMessage = ref('')
const loading = ref(false)
const replySending = ref(false)
const searchKeyword = ref('')
const filterStatus = ref('')
const messagesContainer = ref(null)
const replyInput = ref(null)
const webSocket = ref(null)
const pendingCount = ref(0)

// 计算过滤后的会话
const filteredSessions = computed(() => {
  return sessions.value.filter(session => {
    const matchesKeyword = !searchKeyword.value || 
      session.userName.toLowerCase().includes(searchKeyword.value.toLowerCase()) ||
      session.topic.toLowerCase().includes(searchKeyword.value.toLowerCase())
    
    const matchesStatus = !filterStatus.value || 
      (filterStatus.value === 'pending' && session.status !== 'completed') ||
      (filterStatus.value === 'completed' && session.status === 'completed')
    
    return matchesKeyword && matchesStatus
  })
})

// 按时间排序的消息（从上到下，最早的在上面）
const sortedMessages = computed(() => {
  if (!currentSession.value || !currentSession.value.messages) {
    return []
  }
  // 复制数组并按时间升序排列
  return [...currentSession.value.messages].sort((a, b) => {
    return new Date(a.createTime) - new Date(b.createTime)
  })
})

// 加载会话列表
const loadSessions = async () => {
  try {
    loading.value = true
    const token = localStorage.getItem('token')
    const response = await fetch('http://localhost:8080/customer-service/pending-sessions', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })

    if (response.ok) {
      const result = await response.json()
      if (result.code === 200) {
        sessions.value = result.data || []
        // 计算待处理数
        pendingCount.value = sessions.value.filter(s => s.status !== 'completed').length
        
        // 如果当前没有选中的会话，自动选中第一个
        if (!currentSession.value && sessions.value.length > 0) {
          selectSession(sessions.value[0])
        }
      }
    }
  } catch (error) {
    console.error('加载会话失败:', error)
    ElMessage.error('加载会话失败')
  } finally {
    loading.value = false
  }
}

// 选中会话
const selectSession = (session) => {
  currentSession.value = session
  replyMessage.value = ''
  nextTick(() => {
    scrollToBottom()
    replyInput.value?.focus()
  })
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 处理选中会话事件
const handleSelectSessionEvent = async (event) => {
  console.log('收到选中会话事件', event?.detail)
  if (event?.detail?.userId && sessions.value.length > 0) {
    const targetSession = sessions.value.find(s => s.userId === event.detail.userId)
    if (targetSession) {
      console.log('自动选中用户会话:', targetSession)
      selectSession(targetSession)
    }
  }
}

// 处理新消息事件（实时更新）
const handleNewMessageEvent = (event) => {
  console.log('[AdminCustomerService] 收到新消息事件', event?.detail)
  const { userId, message } = event?.detail || {}
  
  if (!userId || !message) {
    console.warn('[AdminCustomerService] 新消息事件数据不完整', { userId, message })
    return
  }
  
  console.log('[AdminCustomerService] 处理新消息 - 用户ID:', userId, '(类型:', typeof userId, ')')
  console.log('[AdminCustomerService] 当前会话:', currentSession.value)
  console.log('[AdminCustomerService] 当前会话用户ID:', currentSession.value?.userId, '(类型:', typeof currentSession.value?.userId, ')')
  console.log('[AdminCustomerService] ID相等?', userId == currentSession.value?.userId, '严格相等?', userId === currentSession.value?.userId)
  
  // 如果当前选中的就是这个用户的会话，直接添加消息
  // 使用 == 而不是 === 来比较，因为可能有类型不匹配
  if (currentSession.value && userId == currentSession.value.userId) {
    console.log('[AdminCustomerService] ✅ 匹配当前会话，添加消息')
    // 添加新消息到当前会话
    if (!currentSession.value.messages) {
      currentSession.value.messages = []
    }
    
    console.log('[AdminCustomerService] 添加消息到当前会话', message)
    currentSession.value.messages.push(message)
    currentSession.value.lastMessage = message.messageContent
    currentSession.value.unreadCount = (currentSession.value.unreadCount || 0) + 1
    
    nextTick(() => {
      scrollToBottom()
    })
  } else {
    console.log('[AdminCustomerService] ❌ 不是当前会话，只更新列表')
  }
  
  // 更新会话列表中的该会话
  const sessionInList = sessions.value.find(s => s.userId == userId)
  if (sessionInList) {
    console.log('[AdminCustomerService] 更新会话列表中的会话', sessionInList)
    sessionInList.lastMessage = message.messageContent
    sessionInList.unreadCount = (sessionInList.unreadCount || 0) + 1
  } else {
    console.log('[AdminCustomerService] 会话列表中没有此用户，刷新列表')
    // 如果会话列表中没有，刷新整个列表
    loadSessions()
  }
}

// 处理刷新事件
const handleRefreshEvent = async (event) => {
  console.log('收到刷新会话列表事件', event?.detail)
  await loadSessions()
  
  // 如果事件包含用户ID，自动选中该用户的会话
  if (event?.detail?.userId && sessions.value.length > 0) {
    const targetSession = sessions.value.find(s => s.userId === event.detail.userId)
    if (targetSession) {
      console.log('自动选中用户会话:', targetSession)
      selectSession(targetSession)
    }
  }
}

// 初始化
onMounted(async () => {
  console.log('[AdminCustomerService] ========== 页面初始化 ==========')
  
  // 检查登录
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }

  // 确保用户信息已加载（触发WebSocket初始化）
  if (!userStore.userInfo || !userStore.userInfo.id) {
    console.log('[AdminCustomerService] 用户信息未加载，先获取用户信息...')
    await userStore.fetchUserInfo()
  }
  
  // 检查WebSocket连接状态
  console.log('[AdminCustomerService] 检查WebSocket连接...')
  console.log('[AdminCustomerService] userStore.userInfo:', userStore.userInfo)
  
  // 加载会话列表
  await loadSessions()

  // 监听刷新事件
  window.addEventListener('refresh-customer-sessions', handleRefreshEvent)
  console.log('[AdminCustomerService] 已添加 refresh-customer-sessions 监听器')
  
  // 监听选中会话事件（从SystemManagement跳转过来）
  window.addEventListener('select-customer-session', handleSelectSessionEvent)
  console.log('[AdminCustomerService] 已添加 select-customer-session 监听器')
  
  // 监听新消息事件（实时更新消息列表）
  window.addEventListener('new-customer-message', handleNewMessageEvent)
  console.log('[AdminCustomerService] 已添加 new-customer-message 监听器')
  
  console.log('[AdminCustomerService] ========== 初始化完成 ==========')
})

// 清理
onUnmounted(() => {
  // 移除事件监听
  window.removeEventListener('refresh-customer-sessions', handleRefreshEvent)
  window.removeEventListener('select-customer-session', handleSelectSessionEvent)
  window.removeEventListener('new-customer-message', handleNewMessageEvent)
})

// 刷新会话列表
const refreshSessions = async () => {
  await loadSessions()
  ElMessage.success('已刷新')
}

// 发送回复
const sendReply = async () => {
  if (!replyMessage.value.trim()) return

  try {
    replySending.value = true

    const token = localStorage.getItem('token')
    const response = await fetch('http://localhost:8080/customer-service/reply', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        userId: currentSession.value.id,
        messageContent: replyMessage.value,
        senderType: 2  // 2-管理员
      })
    })

    if (!response.ok) {
      throw new Error('发送回复失败')
    }

    const result = await response.json()
    if (result.code === 200) {
      // 立即在本地添加管理员消息，无需等待WebSocket
      const adminMsg = {
        id: Date.now(),
        userId: currentSession.value.userId,
        userName: '管理员',
        messageContent: replyMessage.value,
        senderType: 2, // 管理员
        senderId: userStore.userInfo.id,
        senderName: '管理员',
        createTime: new Date().toISOString()
      }
      
      // 添加到当前会话
      if (!currentSession.value.messages) {
        currentSession.value.messages = []
      }
      currentSession.value.messages.push(adminMsg)
      currentSession.value.lastMessage = replyMessage.value
      
      // 清空输入框
      replyMessage.value = ''
      
      // 滚动到底部并聚焦
      nextTick(() => {
        scrollToBottom()
        replyInput.value?.focus()
      })

      ElMessage.success('回复已发送')
    } else {
      throw new Error(result.msg || '发送失败')
    }
  } catch (error) {
    console.error('发送回复失败:', error)
    ElMessage.error(error.message || '发送回复失败')
  } finally {
    replySending.value = false
  }
}

// 处理Enter键
const handleEnterKey = () => {
  if (replyMessage.value.trim()) {
    sendReply()
  }
}

// 标记为已处理
const markAsResolved = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要标记此会话为已处理吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    )

    const token = localStorage.getItem('token')
    const response = await fetch(`http://localhost:8080/customer-service/session/${currentSession.value.id}/complete`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })

    if (response.ok) {
      const result = await response.json()
      if (result.code === 200) {
        currentSession.value.status = 'completed'
        ElMessage.success('已标记为已处理')
      }
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('标记失败:', error)
      ElMessage.error('标记失败')
    }
  }
}

// 删除会话
const deleteSession = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要删除此会话吗？此操作不可撤销。',
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // TODO: 调用删除会话的API
    ElMessage.success('会话已删除')
    currentSession.value = null
    await loadSessions()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 处理搜索
const handleSearch = () => {
  // 搜索由computed自动处理
}

// 处理状态筛选
const handleStatusFilter = () => {
  // 筛选由computed自动处理
}

// 返回上一页
const goBack = () => {
  router.back()
}

// 获取用户头像
const getUserAvatar = () => {
  return 'https://cube.elemecdn.com/0/88/ff0b88ba1220c6fb3b85e36ae2d47png'
}

// 获取管理员头像
const getAdminAvatar = () => {
  return 'https://cube.elemecdn.com/0/88/ff0b88ba1220c6fb3b85e36ae2d47png'
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  
  return `${year}/${month}/${day} ${hours}:${minutes}:${seconds}`
}
</script>

<style scoped>
.admin-customer-service {
  width: 100%;
  height: 100vh;
}

.service-container {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.service-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  border-bottom: 1px solid #e5e7eb;
  background-color: white;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-left h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.back-btn {
  color: #6b7280;
}

.back-btn:hover {
  color: #111827;
}

.header-right {
  display: flex;
  gap: 12px;
}

.service-content {
  flex: 1;
  overflow: hidden;
}

.sessions-sidebar {
  border-right: 1px solid #e5e7eb;
  background-color: #f9fafb;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background-color: white;
  border-bottom: 1px solid #e5e7eb;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 16px;
}

.sessions-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.empty-sessions {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  color: #9ca3af;
  font-size: 14px;
}

.session-item {
  padding: 12px;
  margin-bottom: 8px;
  background-color: white;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
}

.session-item:hover {
  background-color: #f0f5ff;
  border-color: #409EFF;
}

.session-item.active {
  background-color: #e6f7ff;
  border-color: #409EFF;
}

.session-info {
  padding-right: 30px;
}

.user-name {
  font-weight: 600;
  color: #111827;
  font-size: 14px;
  margin-bottom: 4px;
}

.session-topic {
  color: #6b7280;
  font-size: 12px;
  margin-bottom: 4px;
}

.session-time {
  color: #9ca3af;
  font-size: 12px;
}

.unread-badge {
  position: absolute;
  right: 12px;
  top: 12px;
}

.chat-area {
  display: flex;
  flex-direction: column;
  background-color: white;
  overflow: hidden;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.chat-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 0;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #e5e7eb;
  background-color: #fafafa;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-info h3 {
  margin: 0;
  font-size: 16px;
}

.topic-tag {
  background-color: #e6f7ff;
  color: #0050b3;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.status-tag {
  background-color: #fde3cf;
  color: #ad6000;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.status-tag.is-completed {
  background-color: #f6ffed;
  color: #274e20;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.message-item.user-msg {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 70%;
}

.message-item.user-msg .message-content {
  align-items: flex-end;
}

.sender-name {
  font-size: 12px;
  color: #9ca3af;
  padding: 0 8px;
}

.message-text {
  background-color: #f3f4f6;
  border-radius: 8px;
  padding: 10px 12px;
  word-wrap: break-word;
  word-break: break-word;
  line-height: 1.5;
}

.message-item.user-msg .message-text {
  background-color: #3b82f6;
  color: white;
}

.message-time {
  font-size: 12px;
  color: #9ca3af;
  padding: 0 8px;
}

.message-item.user-msg .message-time {
  text-align: right;
}

.reply-area {
  padding: 16px 24px;
  border-top: 1px solid #e5e7eb;
  background-color: #fafafa;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reply-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.completed-notice {
  padding: 20px;
  background-color: #f5f7fa;
}

.completed-notice .el-alert {
  border-radius: 4px;
}
</style>
