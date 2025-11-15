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
            <el-badge v-if="pendingCount > 0" :value="pendingCount" :max="99" />
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
                <div class="session-topic">{{ formatTopic(session.topic) }}</div>
                <div class="session-last-message">{{ session.lastMessage }}</div>
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

            <!-- 消息区域 -->
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

// 连接管理员WebSocket
const connectAdminWebSocket = () => {
  try {
    console.log('[AdminCustomerService] ========== 开始连接管理员WebSocket ==========')
    const adminId = userStore.userInfo.id
    const wsUrl = `ws://localhost:8080/ws/customer-service?userId=${adminId}&userType=admin`
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
        updatePendingCount()
        
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
    await fetch('http://localhost:8080/customer-service/mark-read-by-user?userId=' + session.userId + '&senderType=1', {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })
    
    // 本地立即更新消恫状态，实时显示已读
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
        await fetch('http://localhost:8080/customer-service/mark-read-by-user?userId=' + userId + '&senderType=2', {
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
  height: 100vh;
}

[data-theme="dark"] .admin-customer-service {
  background-color: #1a1a1a;
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

[data-theme="dark"] .service-header {
  background-color: #2a2a2a;
  border-bottom-color: #444;
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
  color: #111827;
}

[data-theme="dark"] .header-left h2 {
  color: #e0e0e0;
}

.back-btn {
  color: #6b7280;
}

[data-theme="dark"] .back-btn {
  color: #999;
}

.back-btn:hover {
  color: #111827;
}

[data-theme="dark"] .back-btn:hover {
  color: #e0e0e0;
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

[data-theme="dark"] .sessions-sidebar {
  background-color: #252525;
  border-right-color: #444;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background-color: white;
  border-bottom: 1px solid #e5e7eb;
}

[data-theme="dark"] .sidebar-header {
  background-color: #2a2a2a;
  border-bottom-color: #444;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 16px;
  color: #111827;
}

[data-theme="dark"] .sidebar-header h3 {
  color: #e0e0e0;
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

[data-theme="dark"] .empty-sessions {
  color: #666;
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
  color: #111827;
}

[data-theme="dark"] .session-item {
  background-color: #333;
  border-color: #444;
  color: #e0e0e0;
}

.session-item:hover {
  background-color: #f0f5ff;
  border-color: #409EFF;
}

[data-theme="dark"] .session-item:hover {
  background-color: #3a4a5a;
  border-color: #409EFF;
}

.session-item.active {
  background-color: #e6f7ff;
  border-color: #409EFF;
}

[data-theme="dark"] .session-item.active {
  background-color: #1e3a4a;
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

[data-theme="dark"] .user-name {
  color: #e0e0e0;
}

.session-topic {
  color: #6b7280;
  font-size: 12px;
  margin-bottom: 4px;
}

[data-theme="dark"] .session-topic {
  color: #999;
}

.session-time {
  color: #9ca3af;
  font-size: 12px;
}

[data-theme="dark"] .session-time {
  color: #777;
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

[data-theme="dark"] .chat-area {
  background-color: #1f1f1f;
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

[data-theme="dark"] .chat-header {
  background-color: #2a2a2a;
  border-bottom-color: #444;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 12px;
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

[data-theme="dark"] .topic-tag {
  background-color: rgba(0, 80, 179, 0.2);
  color: #65b1ff;
}

.status-tag {
  background-color: #fde3cf;
  color: #ad6000;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

[data-theme="dark"] .status-tag {
  background-color: rgba(173, 96, 0, 0.2);
  color: #ffb366;
}

.status-tag.is-completed {
  background-color: #f6ffed;
  color: #274e20;
}

[data-theme="dark"] .status-tag.is-completed {
  background-color: rgba(82, 196, 26, 0.2);
  color: #85ce61;
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
  background-color: #f8f9fa;
}

[data-theme="dark"] .messages-area {
  background-color: #252525;
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

[data-theme="dark"] .sender-name {
  color: #777;
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

[data-theme="dark"] .message-text {
  background-color: #333;
  color: #e0e0e0;
}

.message-item.user-msg .message-text {
  background-color: #f3f4f6;
  color: #333;
}

[data-theme="dark"] .message-item.user-msg .message-text {
  background-color: #333;
  color: #e0e0e0;
}

.message-time {
  font-size: 12px;
  color: #9ca3af;
  padding: 0 8px;
}

[data-theme="dark"] .message-time {
  color: #777;
}

.message-item.user-msg .message-time {
  text-align: left;
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

.message-item.admin-msg .message-time {
  text-align: right;
}

/* 消恫元信息容器 */
.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  font-size: 12px;
}

.message-item.user-msg .message-meta {
  justify-content: flex-start;
}

.message-item.admin-msg .message-meta {
  justify-content: flex-end;
}

/* 已读/未读状态 */
.read-status {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 6px;
  border-radius: 8px;
}

.read-status.read {
  color: #52c41a;
  background-color: #f6ffed;
}

[data-theme="dark"] .read-status.read {
  background-color: rgba(82, 196, 26, 0.1);
  color: #85ce61;
}

.read-status.unread {
  color: #ff4d4f;
  background-color: #fff1f0;
}

[data-theme="dark"] .read-status.unread {
  background-color: rgba(255, 77, 79, 0.1);
  color: #ff7875;
}

.reply-area {
  padding: 16px 24px;
  border-top: 1px solid #e5e7eb;
  background-color: #fafafa;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

[data-theme="dark"] .reply-area {
  background-color: #2a2a2a;
  border-top-color: #444;
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

[data-theme="dark"] .completed-notice {
  background-color: #2a2a2a;
}

.completed-notice .el-alert {
  border-radius: 4px;
}

/* el-card暗夜模式适配 */
:deep(.el-card) {
  --el-card-bg-color: white;
  --el-card-border-color: #ebeef5;
  --el-card-text-color: #333;
}

[data-theme="dark"] :deep(.el-card) {
  --el-card-bg-color: #2a2a2a !important;
  --el-card-border-color: #444 !important;
  --el-card-text-color: #e0e0e0 !important;
  background-color: #2a2a2a !important;
  border-color: #444 !important;
  color: #e0e0e0 !important;
}

[data-theme="dark"] :deep(.el-card__header) {
  border-bottom-color: #444 !important;
  background-color: #2a2a2a !important;
}

[data-theme="dark"] :deep(.el-card__body) {
  color: #e0e0e0 !important;
  background-color: #2a2a2a !important;
}

[data-theme="dark"] :deep(.el-card__title) {
  color: #e0e0e0 !important;
}

/* el-dialog暗夜模式适配 */
:deep(.el-dialog) {
  --el-dialog-bg-color: white;
}

[data-theme="dark"] :deep(.el-dialog) {
  --el-dialog-bg-color: #2a2a2a;
}

[data-theme="dark"] :deep(.el-dialog__header) {
  border-bottom-color: #444;
}

[data-theme="dark"] :deep(.el-dialog__title) {
  color: #e0e0e0;
}

[data-theme="dark"] :deep(.el-dialog__close) {
  color: #999;
}

[data-theme="dark"] :deep(.el-dialog__close:hover) {
  color: #e0e0e0;
}

[data-theme="dark"] :deep(.el-dialog__body) {
  color: #e0e0e0;
}

[data-theme="dark"] :deep(.el-dialog__footer) {
  border-top-color: #444;
}
</style>
