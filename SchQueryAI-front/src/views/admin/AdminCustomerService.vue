<template>
  <div class="admin-page admin-customer-service">
    <div class="page-header">
      <div class="header-left">
        <el-button @click="goBack">返回</el-button>
        <div class="title">
          <h2>客服消息管理</h2>
          <p class="sub">处理会话、回复、标记完成</p>
        </div>
        <el-tag v-if="pendingCount > 0" type="danger" effect="dark" size="small">待处理 {{ pendingCount }}</el-tag>
      </div>

      <div class="header-actions">
        <el-button class="theme-toggle" circle plain @click="toggleDarkMode">
          <el-icon><Moon /></el-icon>
        </el-button>
        <el-button type="primary" @click="refreshSessions" plain>
          <el-icon><Upload /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <div class="metrics-row">
      <div
        class="metric-card primary"
        :class="{ active: filterStatus === 'all' }"
        @click="setFilter('all')"
      >
        <div class="metric-top">
          <p class="metric-label">会话总数</p>
          <span class="metric-badge">总览</span>
        </div>
        <p class="metric-value">{{ totalSessions }}</p>
        <span class="metric-desc">全部客服会话</span>
      </div>
      <div
        class="metric-card warning"
        :class="{ active: filterStatus === 'pending' }"
        @click="setFilter('pending')"
      >
        <div class="metric-top">
          <p class="metric-label">待处理会话</p>
          <span class="metric-badge badge-warning">跟进</span>
        </div>
        <p class="metric-value">{{ pendingCount }}</p>
        <span class="metric-desc">含未读或未完成</span>
      </div>
      <div
        class="metric-card success"
        :class="{ active: filterStatus === 'unread' }"
        @click="setFilter('unread')"
      >
        <div class="metric-top">
          <p class="metric-label">未读消息</p>
          <span class="metric-badge badge-success">提醒</span>
        </div>
        <p class="metric-value">{{ unreadTotal }}</p>
        <span class="metric-desc">列表红点汇总</span>
      </div>
      <div
        class="metric-card info"
        :class="{ active: filterStatus === 'active' }"
        @click="setFilter('active')"
      >
        <div class="metric-top">
          <p class="metric-label">进行中</p>
          <span class="metric-badge badge-info">处理中</span>
        </div>
        <p class="metric-value">{{ activeSessions }}</p>
        <span class="metric-desc">状态为处理中</span>
      </div>
    </div>

    <div class="workspace">
      <el-card class="sessions-card" shadow="never" v-loading="loading">
        <div class="card-header">
          <div>
            <h3>客服会话</h3>
          </div>
          <el-badge v-if="pendingCount > 0" :value="pendingCount" :max="99" />
        </div>
        <div class="sessions-list">
          <div v-if="filteredSessions.length === 0" class="empty-sessions">
            <el-empty description="暂无会话" :image-size="80" />
          </div>
          <div
              v-for="session in filteredSessions"
              :key="session.id"
              class="session-item"
              :class="{ active: currentSession?.id === session.id }"
              @click="selectSession(session)"
            >
              <div class="session-main">
                <div class="session-top">
                <div class="user-name-row">
                  <span class="user-name">{{ session.userName }}</span>
                  <span class="topic-inline">{{ formatTopic(session.topic) }}</span>
                </div>
                <span class="status-dot" :class="session.status === 'completed' ? 'done' : 'processing'">
                  {{ session.status === 'completed' ? '已完成' : '处理中' }}
                </span>
              </div>
              <div class="session-last-message">{{ session.lastMessage }}</div>
            </div>
            <el-badge v-if="session.unreadCount > 0" :value="session.unreadCount" class="unread-badge" />
          </div>
        </div>
      </el-card>

      <el-card class="chat-card" shadow="never">
        <div v-if="!currentSession" class="empty-state">
          <el-empty description="选择一个会话开始处理" :image-size="120">
            <el-button type="primary" @click="refreshSessions">刷新会话列表</el-button>
          </el-empty>
        </div>

        <div v-else class="chat-wrapper">
          <div class="chat-header">
            <div class="header-info">
              <h3>{{ currentSession.userName }}</h3>
              <span class="topic-tag">{{ formatTopic(currentSession.topic) }}</span>
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

          <div class="messages-area" ref="messagesContainer">
            <div v-for="msg in sortedMessages" :key="msg.id" class="message-item" :class="{ 'admin-msg': msg.senderType === 2, 'user-msg': msg.senderType === 1 }">
              <div class="message-avatar">
                <el-avatar :size="36" :src="msg.senderType === 1 ? getUserAvatar() : getAdminAvatar()" />
              </div>
              <div class="message-content">
                <div class="sender-name">{{ msg.senderType === 1 ? currentSession.userName : '我' }}</div>
                <div class="message-text">{{ msg.messageContent }}</div>
                <div class="message-meta">
                  <span class="message-time">{{ formatTime(msg.createTime) }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="reply-area" v-if="currentSession.status !== 'completed'">
            <div class="reply-box" :class="{ 'is-focused': isInputFocused }">
              <div class="input-icon">
                <el-icon><ChatDotRound /></el-icon>
              </div>
              <el-input
                  ref="replyInput"
                  v-model="replyMessage"
                  type="textarea"
                  :rows="1"
                  placeholder="输入您的回复..."
                  @keydown.enter.prevent="handleEnterKey"
                  @focus="isInputFocused = true"
                  @blur="isInputFocused = false"
                  resize="none"
              />
              <div class="input-actions">
                <span class="char-count" v-if="replyMessage.length > 0">{{ replyMessage.length }}</span>
                <el-button
                  class="send-btn"
                  :class="{ 'can-send': replyMessage.trim() }"
                  circle
                  @click="sendReply"
                  :loading="replySending"
                  :disabled="!replyMessage.trim()"
                >
                  <el-icon><Position /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
          <div v-else class="completed-notice">
            <div class="completed-box">
              <el-icon class="completed-icon"><CircleCheck /></el-icon>
              <span class="completed-text">此会话已完成，用户发送新消息后将自动重新激活</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Upload, Moon, Position, ChatDotRound, CircleCheck } from '@element-plus/icons-vue'
import { useUserStore } from '../../stores/userStore'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 状态变量
const sessions = ref([])
const currentSession = ref(null)
const replyMessage = ref('')
const loading = ref(false)
const replySending = ref(false)
const filterStatus = ref('all')
const messagesContainer = ref(null)
const replyInput = ref(null)
const isInputFocused = ref(false)
const webSocket = ref(null)
const pendingCount = ref(0)
const isDarkMode = ref(false)

// 计算过滤后的会话
const filteredSessions = computed(() => {
  return sessions.value.filter(session => {
    let matchesStatus = true
    switch (filterStatus.value) {
      case 'pending':
        matchesStatus = session.status !== 'completed'
        break
      case 'completed':
        matchesStatus = session.status === 'completed'
        break
      case 'active':
        matchesStatus = session.status !== 'completed'
        break
      case 'unread':
        matchesStatus = (session.unreadCount || 0) > 0
        break
      default:
        matchesStatus = true
    }
    return matchesStatus
  })
})

const totalSessions = computed(() => sessions.value.length)
const activeSessions = computed(() => sessions.value.filter(s => s.status !== 'completed').length)
const unreadTotal = computed(() => sessions.value.reduce((sum, s) => sum + (s.unreadCount || 0), 0))

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

// 连接管理员WebSocket
const connectAdminWebSocket = () => {
  try {
    console.log('[AdminCustomerService] ========== 开始连接管理员WebSocket ==========')
    const adminId = userStore.userInfo.id
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const wsUrl = `${protocol}//${host}/ws/customer-service?userId=${adminId}&userType=admin`
    console.log('[AdminCustomerService] WebSocket URL:', wsUrl)
    webSocket.value = new WebSocket(wsUrl)
    
    webSocket.value.onopen = () => {
      console.log('[AdminCustomerService] ========== 管理员WebSocket连接已建立 ==========')
      console.log('[AdminCustomerService] 管理员ID:', adminId)
    }
    
    webSocket.value.onmessage = (event) => {
      console.log('[AdminCustomerService] 收到WebSocket消恫:', event.data)
    }
    
    webSocket.value.onerror = (error) => {
      console.error('[AdminCustomerService] WebSocket错误:', error)
    }
    
    webSocket.value.onclose = () => {
      console.log('[AdminCustomerService] WebSocket连接已关闭code')
    }
  } catch (error) {
    console.error('[AdminCustomerService] 连接WebSocket失败:', error)
  }
}

// 加载会话列表
const loadSessions = async () => {
  try {
    loading.value = true
    const token = localStorage.getItem('token')
    const response = await fetch('/api/customer-service/pending-sessions', {
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
        updatePendingCount()

        // 支持通过路由参数指定会话（/admin/customer-service/chat/:userId）
        const preferredUserId = route.params?.userId
        if (preferredUserId != null && sessions.value.length > 0) {
          const targetSession = sessions.value.find((s) => s.userId == preferredUserId)
          if (targetSession) {
            await selectSession(targetSession)
            return
          }
        }

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

// 更新待处理会话数
const updatePendingCount = () => {
  // pendingCount指有未读消恫的会话数（不是未读消恫数量）
  pendingCount.value = sessions.value.filter(s => (s.unreadCount || 0) > 0).length
  console.log('[AdminCustomerService] 有未读消恫的会话数:', pendingCount.value)
  console.log('[AdminCustomerService] 所有会话:', sessions.value.map(s => ({ userId: s.userId, unreadCount: s.unreadCount })))
}

// 选中会话
const selectSession = async (session) => {
  currentSession.value = session
  replyMessage.value = ''
  
  // 调用API标记仅该用户的消恫为已读
  try {
    const token = localStorage.getItem('token')
    // 标记senderType=1(用户)的消恫为已读
    await fetch('/api/customer-service/mark-read-by-user?userId=' + session.userId + '&senderType=1', {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })
    
    // 本地立即更新消除状态，实时显示已读
    if (currentSession.value && currentSession.value.messages) {
      currentSession.value.messages.forEach(msg => {
        if (msg.senderType === 1) {
          msg.readStatus = 1 // 立即标记为已读
        }
      })
    }
    
    // 清除红点
    session.unreadCount = 0
    console.log('[AdminCustomerService] 已标记用户', session.userId, '的消恫为已读')
    
    // 更新待处理会话数
    updatePendingCount()
    // 通过WebSocket通知用户：管理员正在查看你的消息
    if (webSocket.value && webSocket.value.readyState === WebSocket.OPEN) {
      try {
        webSocket.value.send(JSON.stringify({
          type: 'admin_viewing',
          toUserId: session.userId,
          adminId: userStore.userInfo.id
        }))
        console.log('[AdminCustomerService] 已通知用户管理员正在查看消息')
      } catch (error) {
        console.error('[AdminCustomerService] 发送viewing事件失败:', error)
      }
    }
    
    // 触发事件，告诉其他页面已标记为已读
    window.dispatchEvent(new CustomEvent('admin-marked-read', {
      detail: { userId: session.userId }
    }))
  } catch (error) {
    console.error('[AdminCustomerService] 标记已读失败:', error)
  }
  
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

// 处理新消恫事件（实时更新）
const handleNewMessageEvent = async (event) => {
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
    
    console.log('[AdminCustomerService] 添加消恫到当前会话', message)
    currentSession.value.messages.push(message)
    currentSession.value.lastMessage = message.messageContent
    currentSession.value.topic = message.topic
    // 注意：当管理员正在查看此会话时，不增加未读计数
        
    // 如果新消恫是管理员發送（senderType=2），需要且正在查看這個用户的對話框，則立即標記爲已諯
    if (message.senderType === 2 && currentSession.value && currentSession.value.userId == userId) {
      message.readStatus = 1 // 管理员消恫自动为已读
      // 调用API標記供略者消恫為已读（senderType=0標記所有）
      try {
        const token = localStorage.getItem('token')
        await fetch('/api/customer-service/mark-read-by-user?userId=' + userId + '&senderType=2', {
          method: 'PUT',
          headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
          }
        })
        console.log('[AdminCustomerService] 已標記管理员消恫為已读')
      } catch (error) {
        console.error('[AdminCustomerService] 標記已读失败:', error)
      }
    }
    
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
    sessionInList.topic = message.topic
    
    // 仅当管理员正在查看此会话时，不增加或减少未读计数
    if (currentSession.value && currentSession.value.userId == userId) {
      // 正在查看此会话，消恫到达时自动为已读，所以不增加未读计数
      console.log('[AdminCustomerService] 正在查看此会话，不增加或减少未读计数')
    } else {
      // 不在查看，消恫数+1
      sessionInList.unreadCount = (sessionInList.unreadCount || 0) + 1
    }
  } else {
    console.log('[AdminCustomerService] 会话列表中沒有此用户，刷新列表')
    // 如果会话列表中沒有，刷新整个列表
    loadSessions()
  }
    
  // 更新待处理会话数
  updatePendingCount()
    
  // 踦发自定义事件，让SystemManagement也能接收到新消恫
  window.dispatchEvent(new CustomEvent('new-customer-message', {
    detail: { userId, message }
  }))
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

// 清理
const handleUserMarkedAdminRead = (event) => {
  console.log('[AdminCustomerService] 接收到用户已标记管理员消恫事件', event?.detail)
  const { userId } = event?.detail || {}
  
  if (userId) {
    // 找到对应的会话，减少红点数
    const sessionInList = sessions.value.find(s => s.userId == userId)
    if (sessionInList && sessionInList.unreadCount > 0) {
      sessionInList.unreadCount--
      console.log('[AdminCustomerService] 红点数渐次执行了，当前未读数:', sessionInList.unreadCount)
      
      // 如果是当前选中的会话，也要更新
      if (currentSession.value && currentSession.value.userId == userId) {
        currentSession.value.unreadCount = sessionInList.unreadCount
      }
    }
  }
}

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

  // 初始化主题
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme === 'dark') {
    isDarkMode.value = true
    document.documentElement.setAttribute('data-theme', 'dark')
  }
  
  // 检查WebSocket连接状态
  console.log('[AdminCustomerService] 检查WebSocket连接...')
  console.log('[AdminCustomerService] userStore.userInfo:', userStore.userInfo)
  
  // 连接管理员WebSocket
  connectAdminWebSocket()
  
  // 加载会话列表
  await loadSessions()

  // 监听刷新事件
  window.addEventListener('refresh-customer-sessions', handleRefreshEvent)
  console.log('[AdminCustomerService] 已添加 refresh-customer-sessions 监听器')
  
  // 监听选中会话事件（从SystemManagement跳转过来）
  window.addEventListener('select-customer-session', handleSelectSessionEvent)
  console.log('[AdminCustomerService] 已添加 select-customer-session 监听器')
  
  // 监听新消恫事件（实时更新消恫列表）
  window.addEventListener('new-customer-message', handleNewMessageEvent)
  console.log('[AdminCustomerService] 已添加 new-customer-message 监听器')
    
  // 监听用户已标记管理员消恫事件（实时更新红点）
  window.addEventListener('user-marked-admin-read', handleUserMarkedAdminRead)
  console.log('[AdminCustomerService] 已添加 user-marked-admin-read 监听器')
  
  console.log('[AdminCustomerService] ========== 初始化完成 ==========')
})

watch(
  () => route.params?.userId,
  (userId) => {
    if (userId == null) return
    const target = sessions.value.find((s) => s.userId == userId)
    if (target) selectSession(target)
  }
)

// 清理
onUnmounted(() => {
  // 移除事件监听
  window.removeEventListener('refresh-customer-sessions', handleRefreshEvent)
  window.removeEventListener('select-customer-session', handleSelectSessionEvent)
  window.removeEventListener('new-customer-message', handleNewMessageEvent)
  window.removeEventListener('user-marked-admin-read', handleUserMarkedAdminRead)
  
  // 关闭WebSocket
  if (webSocket.value) {
    webSocket.value.close()
  }
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
    const response = await fetch('/api/customer-service/reply', {
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
      // 立即在本地添加管理员消恫，无需等待WebSocket
      const adminMsg = {
        id: Date.now(),
        userId: currentSession.value.userId,
        userName: '管理员',
        messageContent: replyMessage.value,
        senderType: 2, // 管理员
        senderId: userStore.userInfo.id,
        senderName: '管理员',
        createTime: new Date().toISOString(),
        readStatus: 1 // 管理员自己发送，自动为已读
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
      
      // 发送viewing事件，告诉用户管理员正在查看（此时已经发送了消恫）
      if (webSocket.value && webSocket.value.readyState === WebSocket.OPEN) {
        try {
          webSocket.value.send(JSON.stringify({
            type: 'admin_viewing',
            toUserId: currentSession.value.userId,
            adminId: userStore.userInfo.id
          }))
          console.log('[AdminCustomerService] 已上报viewing事件')
        } catch (error) {
          console.error('[AdminCustomerService] 发送viewing事件失败:', error)
        }
      }
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
    const response = await fetch(`/api/customer-service/session/${currentSession.value.id}/complete`, {
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
        // 更新会话刊状态
        const sessionInList = sessions.value.find(s => s.id === currentSession.value.id)
        if (sessionInList) {
          sessionInList.status = 'completed'
        }
        // 更新待处理会话数
        updatePendingCount()
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
  // 已移除搜索框，保留占位以兼容潜在引用
}

// 处理状态筛选
const handleStatusFilter = () => {
  // 已移除下拉筛选，改由顶部指标卡点击触发
}

// 通过点击指标卡设置筛选
const setFilter = (status) => {
  filterStatus.value = status
}

// 返回上一页
const goBack = () => {
  router.back()
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
  window.dispatchEvent(new CustomEvent('theme-change', { detail: { isDark: isDarkMode.value } }))
}

// 获取用户头像
const getUserAvatar = () => {
  return 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200"><rect fill="%23e6f7ff" width="200" height="200"/><circle cx="100" cy="70" r="30" fill="%231890ff"/><path d="M60 140 Q100 120 140 140 L140 180 L60 180 Z" fill="%231890ff"/></svg>'
}

// 获取管理员头像
const getAdminAvatar = () => {
  return 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200"><rect fill="%23f0f5ff" width="200" height="200"/><circle cx="100" cy="70" r="30" fill="%23666"/><path d="M60 140 Q100 120 140 140 L140 180 L60 180 Z" fill="%23666"/></svg>'
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

// 转换topic显示文本
const formatTopic = (topic) => {
  const topicMap = {
    'general': '一般问题',
    'account': '账户问题',
    'feature': '功能问题',
    'tech': '技术支持',
    'other': '其他问题'
  }
  return topicMap[topic] || topic
}
</script>

<style scoped>
.admin-customer-service {
  width: 100%;
  height: 100%;
  padding: 0;
  background: transparent;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.page-header .header-left {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.page-header .title {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.theme-toggle {
  border-radius: 50%;
}

.metrics-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 8px;
  margin: 4px 0 8px;
  flex-shrink: 0;
}

.metric-card {
  padding: 10px 12px;
  border-radius: 10px;
  background: #ffffff;
  border: 1px solid #edf2f7;
  box-shadow: 0 8px 20px rgba(31, 45, 61, 0.05);
  display: flex;
  flex-direction: column;
  gap: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

[data-theme="dark"] .metric-card {
  background: #111827;
  border-color: #1f2937;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
}

.metric-card.active {
  border-color: #409EFF;
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.16);
}

.metric-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.metric-label {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
}

.metric-value {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #111827;
}

.metric-desc {
  font-size: 12px;
  color: #9ca3af;
}

.metric-badge {
  padding: 2px 8px;
  border-radius: 8px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
}

.badge-warning {
  background: #fff7ed;
  color: #c2410c;
}

.badge-success {
  background: #ecfdf3;
  color: #15803d;
}

.badge-info {
  background: #e0f2fe;
  color: #0ea5e9;
}

.metric-card.primary { border-left: 4px solid #409EFF; }
.metric-card.warning { border-left: 4px solid #f59e0b; }
.metric-card.success { border-left: 4px solid #22c55e; }
.metric-card.info { border-left: 4px solid #0ea5e9; }

[data-theme="dark"] .metric-value { color: #e0e0e0; }
[data-theme="dark"] .metric-label,
[data-theme="dark"] .metric-desc { color: #a3a3a3; }

.workspace {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 12px;
  align-items: stretch;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

@media (max-width: 1200px) {
  .workspace {
    grid-template-columns: 1fr;
  }
}

.sessions-card, .chat-card {
  min-height: 0;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 20px rgba(31, 45, 61, 0.06);
  overflow: hidden;
}

:deep(.sessions-card .el-card__body),
:deep(.chat-card .el-card__body) {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  padding: 12px;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 0 0 10px 0;
  border-bottom: 1px solid #edf2f7;
}

[data-theme="dark"] .card-header {
  border-bottom-color: #1f2937;
}

.card-header h3 {
  margin: 0;
  font-size: 16px;
  color: #111827;
}

[data-theme="dark"] .card-header h3 {
  color: #e5e7eb;
}

.card-subtitle {
  margin: 4px 0 0;
  color: #9ca3af;
  font-size: 12px;
}

[data-theme="dark"] .card-subtitle {
  color: #94a3b8;
}

.sessions-list {
  margin-top: 12px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
  padding-bottom: 8px;
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
  padding: 10px;
  margin-bottom: 8px;
  background-color: white;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.25s;
  position: relative;
  color: #111827;
}

[data-theme="dark"] .session-item {
  background-color: #1f2937;
  border-color: #293548;
  color: #e0e0e0;
}

.session-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 22px rgba(31, 45, 61, 0.08);
  border-color: #409EFF;
}

.session-item.active {
  border-color: #409EFF;
  box-shadow: 0 12px 28px rgba(64, 158, 255, 0.12);
}

.session-main {
  padding-right: 30px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user-name-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.user-name {
  font-weight: 600;
  color: #111827;
  font-size: 14px;
}

[data-theme="dark"] .user-name {
  color: #e0e0e0;
}

.topic-inline {
  font-size: 12px;
  color: #6b7280;
}

.status-dot {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #f0f4ff;
  color: #1d4ed8;
}

.status-dot.processing {
  background: #fff7ed;
  color: #c2410c;
}

.status-dot.done {
  background: #f0fdf4;
  color: #15803d;
}

.session-topic {
  color: #6b7280;
  font-size: 12px;
}

.session-last-message {
  color: #4b5563;
  font-size: 13px;
  line-height: 1.4;
}

.unread-badge {
  position: absolute;
  right: 12px;
  top: 12px;
}

[data-theme="dark"] .session-topic { color: #9ca3af; }
[data-theme="dark"] .session-last-message { color: #cbd5e1; }

.chat-card {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
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
  flex: 1;
  min-height: 0;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid #e5e7eb;
  background: #f9fafb;
  border-radius: 10px;
}

[data-theme="dark"] .chat-header {
  background-color: #1f2937;
  border-bottom-color: #293548;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-info h3 {
  margin: 0;
  font-size: 16px;
  color: #111827;
}

[data-theme="dark"] .header-info h3 {
  color: #e0e0e0;
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
  min-height: 0;
  overflow-y: auto;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background-color: #ffffff;
}

[data-theme="dark"] .messages-area {
  background-color: #111827;
}

.message-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.message-item.user-msg {
  flex-direction: row;
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
  align-items: flex-start;
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
  color: #333;
}

.message-item.admin-msg {
  flex-direction: row-reverse;
}

.message-item.admin-msg .message-content {
  align-items: flex-end;
}

.message-item.admin-msg .message-text {
  background-color: #3b82f6;
  color: white;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  font-size: 12px;
}

.message-item.admin-msg .message-meta {
  justify-content: flex-end;
}

.message-item.admin-msg .message-time {
  text-align: right;
}

[data-theme="dark"] .sender-name { color: #94a3b8; }
[data-theme="dark"] .message-text { background-color: #1f2937; color: #e0e0e0; }
[data-theme="dark"] .message-item.user-msg .message-text { background-color: #1f2937; color: #e0e0e0; }
[data-theme="dark"] .message-meta { color: #94a3b8; }

.reply-area {
  padding: 8px 12px;
  border-top: 1px solid #e5e7eb;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  flex-shrink: 0;
  border-radius: 0 0 14px 14px;
}

.reply-box {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  background: #fff;
  border: 2px solid #e2e8f0;
  border-radius: 16px;
  padding: 4px 60px 4px 40px;
  box-shadow: 0 4px 12px rgba(31, 45, 61, 0.06),
              0 0 0 0 rgba(99, 102, 241, 0);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.reply-box:hover {
  border-color: #c7d2fe;
  box-shadow: 0 6px 16px rgba(31, 45, 61, 0.08),
              0 0 0 4px rgba(99, 102, 241, 0.05);
}

.reply-box.is-focused {
  border-color: #818cf8;
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.12),
              0 0 0 4px rgba(99, 102, 241, 0.1);
}

.input-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.3s ease;
}

.reply-box.is-focused .input-icon {
  color: #6366f1;
}

.input-actions {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  gap: 10px;
}

.char-count {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 500;
  min-width: 24px;
  text-align: right;
}

:deep(.reply-area .el-textarea__inner) {
  min-height: 32px !important;
  max-height: 80px;
  padding: 6px 0;
  border: none;
  box-shadow: none;
  background-color: transparent;
  font-size: 14px;
  line-height: 1.5;
  color: #334155;
  resize: none;
}

:deep(.reply-area .el-textarea__inner::placeholder) {
  color: #94a3b8;
  font-size: 14px;
}

:deep(.reply-area .el-textarea__inner:focus) {
  outline: none;
}

.send-btn {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #c7d2fe 0%, #a5b4fc 100%);
  border: none;
  color: #6366f1;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.15);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
}

.send-btn:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.25);
}

.send-btn.can-send {
  background: linear-gradient(135deg, #818cf8 0%, #6366f1 100%);
  color: #fff;
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.35);
}

.send-btn.can-send:hover {
  transform: scale(1.1);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.45);
}

.send-btn:active {
  transform: scale(0.95);
}

.send-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none !important;
}

.send-btn .el-icon {
  font-size: 14px;
  transition: transform 0.3s ease;
}

.send-btn.can-send .el-icon {
  animation: pulse-icon 2s ease-in-out infinite;
}

@keyframes pulse-icon {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.input-hint {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
}

.input-hint kbd {
  display: inline-block;
  padding: 2px 6px;
  margin: 0 2px;
  font-size: 11px;
  font-family: inherit;
  line-height: 1.2;
  color: #64748b;
  background: #e2e8f0;
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

[data-theme="dark"] .reply-area {
  background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
  border-top-color: #334155;
}

[data-theme="dark"] .reply-box {
  background: #1e293b;
  border-color: #334155;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

[data-theme="dark"] .reply-box:hover {
  border-color: #4f46e5;
}

[data-theme="dark"] .reply-box.is-focused {
  border-color: #6366f1;
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.2),
              0 0 0 4px rgba(99, 102, 241, 0.15);
}

[data-theme="dark"] .input-icon {
  color: #64748b;
}

[data-theme="dark"] .reply-box.is-focused .input-icon {
  color: #818cf8;
}

[data-theme="dark"] :deep(.reply-area .el-textarea__inner) {
  color: #e2e8f0;
}

[data-theme="dark"] :deep(.reply-area .el-textarea__inner::placeholder) {
  color: #64748b;
}

[data-theme="dark"] .char-count {
  color: #64748b;
}

[data-theme="dark"] .input-hint {
  color: #64748b;
}

[data-theme="dark"] .input-hint kbd {
  background: #334155;
  color: #94a3b8;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

.reply-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.completed-notice {
  padding: 8px 12px;
  background: linear-gradient(180deg, #f0fdf4 0%, #dcfce7 100%);
  border-top: 1px solid #bbf7d0;
  flex-shrink: 0;
  border-radius: 0 0 14px 14px;
}

.completed-box {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 6px 16px;
  background: #fff;
  border: 2px solid #86efac;
  border-radius: 16px;
  color: #15803d;
}

.completed-icon {
  font-size: 16px;
  color: #22c55e;
}

.completed-text {
  font-size: 13px;
  font-weight: 500;
}

[data-theme="dark"] .completed-notice {
  background: linear-gradient(180deg, #14532d 0%, #0f172a 100%);
  border-top-color: #166534;
}

[data-theme="dark"] .completed-box {
  background: #1e293b;
  border-color: #166534;
  color: #86efac;
}

[data-theme="dark"] .completed-icon {
  color: #4ade80;
}

[data-theme="dark"] .completed-text {
  color: #86efac;
}

.back-btn {
  color: #6b7280;
}

[data-theme="dark"] .back-btn {
  color: #cbd5e1;
}

:deep(.el-card) {
  --el-card-bg-color: white;
  --el-card-border-color: #ebeef5;
  --el-card-text-color: #333;
  background-color: white;
  border-color: #ebeef5;
}

[data-theme="dark"] :deep(.el-card) {
  --el-card-bg-color: #111827 !important;
  --el-card-border-color: #1f2937 !important;
  --el-card-text-color: #e0e0e0 !important;
  background-color: #111827 !important;
  border-color: #1f2937 !important;
}

[data-theme="dark"] :deep(.el-card__body) {
  background-color: #111827 !important;
}

[data-theme="dark"] .sessions-card,
[data-theme="dark"] .chat-card {
  background-color: #111827 !important;
  border-color: #1f2937 !important;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

[data-theme="dark"] .messages-area {
  background-color: #111827 !important;
}

[data-theme="dark"] .sessions-list {
  background-color: transparent;
}
</style>
