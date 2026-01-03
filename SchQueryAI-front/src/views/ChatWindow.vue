<template>
  <div class="chat-window">
    <!-- 头部 -->
    <div class="chat-header">
      <div class="header-left">
        <el-button type="text" @click="goBack" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div class="header-info">
          <h3>客服中心</h3>
          <span class="status" :class="{ online: isOnline }">
            <span class="dot"></span>
            {{ isOnline ? '在线' : '离线' }}
          </span>
        </div>
      </div>
      <div class="header-right">
        <el-tooltip content="刷新消息" placement="bottom">
          <el-button type="text" @click="loadMessageHistory" :loading="loading">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="chat-content">
      <!-- 消息区域 -->
      <div class="messages-container" ref="messagesContainer">
        <div v-if="messages.length === 0 && !loading" class="empty-state">
          <el-empty description="暂无消息，开始咨询吧">
            <template #default>
              <div class="empty-icon">
                <el-icon><ChatLineRound /></el-icon>
              </div>
            </template>
          </el-empty>
        </div>

        <transition-group v-else name="message-list" tag="div" class="messages-list">
          <div
              v-for="msg in messages"
              :key="`msg-${msg.id}`"
              class="message-group"
              :class="msg.senderType === 1 ? 'user-group' : 'admin-group'"
          >
            <!-- 管理员消息 (左侧) -->
            <template v-if="msg.senderType === 2">
              <div class="avatar-container">
                <el-avatar :size="36" class="admin-avatar">
                  <el-icon><Service /></el-icon>
                </el-avatar>
              </div>
              <div class="message-wrapper">
                <div class="sender-name">客服</div>
                <div class="message-bubble admin-bubble">
                  <div class="bubble-content">{{ msg.messageContent }}</div>
                </div>
                <div class="message-meta">
                  <span class="message-time">{{ formatTime(msg.createTime) }}</span>
                </div>
              </div>
            </template>

            <!-- 用户消息 (右侧) -->
            <template v-else>
              <div class="avatar-container">
                <el-avatar :size="36" class="user-avatar" :src="userStore.getDisplayAvatar()">
                  <el-icon><UserFilled /></el-icon>
                </el-avatar>
              </div>
              <div class="message-wrapper">
                <div class="message-bubble user-bubble">
                  <div class="bubble-content">{{ msg.messageContent }}</div>
                </div>
                <div class="message-meta">
                  <span class="message-time">{{ formatTime(msg.createTime) }}</span>
                  <span class="read-status" :class="{ 'is-read': msg.readStatus === 1 }">
                    <el-icon v-if="msg.readStatus === 1"><Check /></el-icon>
                    <span class="status-text">{{ msg.readStatus === 1 ? '已读' : '未读' }}</span>
                  </span>
                </div>
              </div>
            </template>
          </div>

          <!-- 输入中指示器 -->
          <div v-if="isTyping" key="typing-indicator" class="message-group admin-group">
             <div class="avatar-container">
                <el-avatar :size="36" class="admin-avatar">
                  <el-icon><Service /></el-icon>
                </el-avatar>
              </div>
            <div class="message-wrapper">
              <div class="sender-name">客服</div>
              <div class="message-bubble admin-bubble typing-bubble">
                <div class="typing-indicator">
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span class="dot"></span>
                </div>
              </div>
            </div>
          </div>
        </transition-group>
      </div>

      <!-- 输入区域 -->
      <div class="input-section">
        <div class="toolbar">
           <div class="topic-selector">
            <span class="label">主题:</span>
            <el-select v-model="topic" placeholder="选择咨询主题" size="small" style="width: 120px">
              <el-option label="一般问题" value="general" />
              <el-option label="账户问题" value="account" />
              <el-option label="功能问题" value="feature" />
              <el-option label="技术支持" value="tech" />
              <el-option label="其他问题" value="other" />
            </el-select>
          </div>
        </div>

        <div class="input-area-wrapper">
          <el-input
              ref="inputRef"
              v-model="messageInput"
              type="textarea"
              :rows="3"
              placeholder="请输入您的问题... (Shift+Enter 换行)"
              @keydown.enter.prevent="handleEnterKey"
              resize="none"
              maxlength="500"
              class="custom-textarea"
          />
          <div class="input-actions">
             <span class="char-count">{{ messageInput.length }}/500</span>
            <el-button
                type="primary"
                @click="sendMessage"
                :loading="sending"
                :disabled="!messageInput.trim() || sending"
                circle
                class="send-btn"
            >
              <el-icon><Promotion /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Refresh, ChatLineRound, Promotion, UserFilled, Service, Check } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/userStore'

const router = useRouter()
const userStore = useUserStore()

// 状态变量
const messages = ref([])
const messageInput = ref('')
const topic = ref('general')
const sending = ref(false)
const loading = ref(false)
const isTyping = ref(false)
const isOnline = ref(false)
const messagesContainer = ref(null)
const inputRef = ref(null)
const webSocket = ref(null)
const userId = ref(null)

// 初始化
onMounted(async () => {
  // 检查登录
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }

  // 确保用户信息已加载
  if (!userStore.userInfo.id) {
    await userStore.fetchUserInfo()
  }

  // 从 userStore 获取用户ID
  userId.value = userStore.userInfo.id
  
  console.log('当前用户信息:', userStore.userInfo)

  // 加载消息历史
  await loadMessageHistory()

  // 调用API标记管理员消息为已读（发送者类型=2）
  try {
    const token = localStorage.getItem('token')
    await fetch('/api/customer-service/mark-read-by-user?userId=' + userId.value + '&senderType=2', {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })
    console.log('[ChatWindow] 已标记管理员消息为已读')
    
    // 需要重新加载一次消息，以获取最新的readStatus状态
    await loadMessageHistory()
    
    // 触发事件，告诉AdminCustomerService消息已读，更新红点
    window.dispatchEvent(new CustomEvent('user-marked-admin-read', {
      detail: { userId: userId.value }
    }))
  } catch (error) {
    console.error('[ChatWindow] 标记已读失败:', error)
  }

  // 连接WebSocket
  connectWebSocket()
  
  // 监听管理员已标记消息事件
  window.addEventListener('admin-marked-read', handleAdminMarkedRead)
  console.log('[ChatWindow] 已添加 admin-marked-read 监听器')
})

// 清理
const handleAdminMarkedRead = async (event) => {
  console.log('[ChatWindow] 接收到管理员已标记消息事件', event?.detail)
  const { userId: eventUserId } = event?.detail || {}
  
  // 只有当事件中的userId与当前用户ID匹配时，才更新消息状态
  if (eventUserId && eventUserId == userId.value) {
    // 立即更新本地消息列表，将用户消息标记为已读
    messages.value.forEach(msg => {
      if (msg.senderType === 1) { // 用户消息
        msg.readStatus = 1 // 标记为已读
      }
    })
    console.log('[ChatWindow] 已更新本地用户消息为已读')
  }
}

onUnmounted(() => {
  if (webSocket.value) {
    webSocket.value.close()
  }
  // 移除事件监听器
  window.removeEventListener('admin-marked-read', handleAdminMarkedRead)
})

// 格式化时间
const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now - date

  // 相对时间显示
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`

  // 判断是否为当年
  const isSameYear = date.getFullYear() === now.getFullYear()

  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')

  if (isSameYear) {
    // 当年只显示月日和时间
    return `${month}-${day} ${hours}:${minutes}`
  } else {
    // 非当年显示完整日期
    const year = date.getFullYear()
    return `${year}-${month}-${day} ${hours}:${minutes}`
  }
}

// 加载消息历史
const loadMessageHistory = async () => {
  try {
    loading.value = true
    const token = localStorage.getItem('token')
    const response = await fetch('/api/customer-service/history', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })

    if (!response.ok) {
      throw new Error('加载消息失败')
    }

    const result = await response.json()
    if (result.code === 200) {
      // 按时间升序排列（最早的在上，最新的在下）
      messages.value = (result.data || []).sort((a, b) => {
        return new Date(a.createTime) - new Date(b.createTime)
      })
      nextTick(() => {
        scrollToBottom()
      })
    }
  } catch (error) {
    console.error('加载消息历史失败:', error)
    ElMessage.error('加载消息历史失败')
  } finally {
    loading.value = false
  }
}

// 连接WebSocket
const connectWebSocket = () => {
  try {
    console.log('[ChatWindow] ========== 开始连接WebSocket ==========')
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const wsUrl = `${protocol}//${host}/ws/customer-service?userId=${userId.value}&userType=user`
    console.log('[ChatWindow] WebSocket URL:', wsUrl)
    webSocket.value = new WebSocket(wsUrl)

    webSocket.value.onopen = () => {
      console.log('[ChatWindow] ========== WebSocket连接已建立 ==========')
      console.log('[ChatWindow] 用户ID:', userId.value)
      isOnline.value = true
      // ElMessage.success('已连接到客服')
    }

    webSocket.value.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        console.log('[ChatWindow] 收到WebSocket消息:', data)
        console.log('[ChatWindow] 消息类型:', data.type)

        if (data.type === 'user_message_received') {
          // 管理员已接收到消息
          console.log('[ChatWindow] 管理员已接收到您的消息')
        } else if (data.type === 'admin_reply') {
          // 处理管理员回复
          console.log('[ChatWindow] ✅ 收到管理员回复:', data.content)
          const newMsg = {
            id: Date.now(),
            messageContent: data.content,
            senderType: 2,  // 管理员消息
            senderId: data.fromUserId,
            senderName: '客服',
            createTime: new Date().toISOString(),
            readStatus: 1 // 管理员消息，用户立即查看为已读
          }
          messages.value.push(newMsg)
          console.log('[ChatWindow] 消息已添加到列表，当前消息数:', messages.value.length)
          ElMessage.success('您有新的回复')
          nextTick(() => {
            scrollToBottom()
          })
        } else if (data.type === 'typing') {
          isTyping.value = true
        } else if (data.type === 'typing_end') {
          isTyping.value = false
        } else if (data.type === 'admin_viewing') {
          // 管理员正在查看你的消息
          console.log('[ChatWindow] 管理员正在查看你的消息')
          // 立即标记所有用户消息为已读
          messages.value.forEach(msg => {
            if (msg.senderType === 1) {
              msg.readStatus = 1
            }
          })
          // ElMessage.success('客服正在查看您的消息')
        } else {
          console.warn('[ChatWindow] 未知消息类型:', data.type)
        }
      } catch (error) {
        console.error('[ChatWindow] 处理WebSocket消息错误:', error)
      }
    }

    webSocket.value.onerror = (error) => {
      console.error('WebSocket错误:', error)
      isOnline.value = false
    }

    webSocket.value.onclose = () => {
      console.log('WebSocket连接已关闭')
      isOnline.value = false
      // 尝试重新连接
      setTimeout(() => {
        if (webSocket.value?.readyState === WebSocket.CLOSED) {
          connectWebSocket()
        }
      }, 3000)
    }
  } catch (error) {
    console.error('连接WebSocket失败:', error)
    ElMessage.error('连接客服失败，请稍后重试')
  }
}

// 发送消息
const sendMessage = async () => {
  if (!messageInput.value.trim()) {
    ElMessage.warning('请输入消息内容')
    return
  }

  try {
    sending.value = true
    const token = localStorage.getItem('token')

    // 添加本地消息
    const userMsg = {
      id: Date.now(),
      messageContent: messageInput.value,
      senderType: 1, // 用户消息
      senderId: userId.value,
      senderName: userStore.userInfo.userName,
      createTime: new Date().toISOString(),
      readStatus: 0 // 用户发送，管理员尚未查看
    }
    messages.value.push(userMsg)

    // 通过WebSocket发送消息
    if (webSocket.value?.readyState === WebSocket.OPEN) {
      const userName = userStore.userInfo.userName || '用户' + userId.value
      console.log('发送消息 - 用户名:', userName)
      
      webSocket.value.send(JSON.stringify({
        type: 'user_message',
        fromUserId: userId.value,
        userName: userName,
        fromType: 'user',
        toUserId: 'admin',
        content: messageInput.value,
        topic: topic.value
      }))
    } else {
      // 降级到HTTP API
      const response = await fetch('/api/customer-service/send', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          messageContent: messageInput.value,
          topic: topic.value
        })
      })

      if (!response.ok) {
        throw new Error('发送消息失败')
      }

      const result = await response.json()
      if (result.code !== 200) {
        throw new Error(result.msg || '发送失败')
      }
    }

    messageInput.value = ''
    nextTick(() => {
      scrollToBottom()
      inputRef.value?.focus()
    })
    // ElMessage.success('消息已发送')
  } catch (error) {
    console.error('发送消息失败:', error)
    ElMessage.error(`发送消息失败: ${error.message}`)
    // 移除失败的本地消息
    messages.value.pop()
  } finally {
    sending.value = false
  }
}

// 处理Enter键
const handleEnterKey = (event) => {
  if (event.shiftKey) {
    // Shift+Enter 换行
    return
  }
  // Enter 发送
  event.preventDefault()
  sendMessage()
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 返回
const goBack = () => {
  router.back()
}
</script>

<style scoped>
.chat-window {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f5f7fa;
  overflow: hidden;
}

/* 深色主题 */
[data-theme="dark"] .chat-window {
  background-color: #121212;
}

/* 头部 */
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  height: 60px;
  background: white;
  border-bottom: 1px solid #ebeef5;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  z-index: 10;
}

[data-theme="dark"] .chat-header {
  background: #1e1e1e;
  border-bottom-color: #333;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  font-size: 20px;
  color: #606266;
  padding: 8px;
}

.back-btn:hover {
  color: #409eff;
  background-color: #ecf5ff;
  border-radius: 50%;
}

[data-theme="dark"] .back-btn {
  color: #a0a0a0;
}
[data-theme="dark"] .back-btn:hover {
  background-color: #333;
}

.header-info {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.header-info h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

[data-theme="dark"] .header-info h3 {
  color: #e0e0e0;
}

.status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
}

.status .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #c0c4cc;
  transition: all 0.3s;
}

.status.online .dot {
  background-color: #67c23a;
  box-shadow: 0 0 6px rgba(103, 194, 58, 0.4);
}

.status.online {
  color: #67c23a;
}

/* 主内容区 */
.chat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

/* 消息容器 */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  scroll-behavior: smooth;
}

.messages-container::-webkit-scrollbar {
  width: 6px;
}
.messages-container::-webkit-scrollbar-thumb {
  background-color: rgba(144, 147, 153, 0.3);
  border-radius: 3px;
}
.messages-container::-webkit-scrollbar-track {
  background: transparent;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.empty-icon {
  font-size: 64px;
  color: #dcdfe6;
  margin-bottom: 16px;
}

/* 消息列表 */
.message-group {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  max-width: 75%;
}

.message-group.user-group {
  align-self: flex-end;
  flex-direction: row-reverse;
  margin-left: auto;
}

.message-group.admin-group {
  align-self: flex-start;
  margin-right: auto;
}

/* 头像 */
.avatar-container {
  flex-shrink: 0;
}
.admin-avatar {
  background-color: #409eff;
}
.user-avatar {
  background-color: #909399;
}

/* 消息包裹层 */
.message-wrapper {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0; /* 防止文本溢出 */
}

.user-group .message-wrapper {
  align-items: flex-end;
}

.sender-name {
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
}

/* 消息气泡 */
.message-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  position: relative;
  word-wrap: break-word;
  word-break: break-word;
  line-height: 1.5;
  font-size: 14px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.admin-bubble {
  background-color: white;
  color: #303133;
  border-top-left-radius: 2px;
}

.user-bubble {
  background-color: #409eff;
  color: white;
  border-top-right-radius: 2px;
}

[data-theme="dark"] .admin-bubble {
  background-color: #2b2b2b;
  color: #e0e0e0;
}

[data-theme="dark"] .user-bubble {
  background-color: #2b6a9e;
}

/* 气泡小尾巴 (仅在非深色模式简单背景下显示较好，这里使用伪元素简单实现) */
.admin-bubble::before {
  content: "";
  position: absolute;
  top: 0;
  left: -8px;
  width: 0;
  height: 0;
  border-style: solid;
  border-width: 0 8px 10px 0;
  border-color: transparent white transparent transparent;
}
.user-bubble::before {
  content: "";
  position: absolute;
  top: 0;
  right: -8px;
  width: 0;
  height: 0;
  border-style: solid;
  border-width: 10px 8px 0 0;
  border-color: #409eff transparent transparent transparent;
}

[data-theme="dark"] .admin-bubble::before {
  border-color: transparent #2b2b2b transparent transparent;
}
[data-theme="dark"] .user-bubble::before {
  border-color: #2b6a9e transparent transparent transparent;
}


/* 消息元数据 */
.message-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #c0c4cc;
  padding: 0 2px;
}

.read-status {
  display: flex;
  align-items: center;
  gap: 2px;
  opacity: 0.6;
}
.read-status.is-read {
  color: #67c23a;
  opacity: 1;
}

.status-text {
  display: none; /* 默认隐藏文字，保持界面清爽 */
}

/* 鼠标悬停显示文字 */
.read-status:hover .status-text {
  display: inline;
}

/* 输入中指示器 */
.typing-bubble {
  padding: 12px 16px;
  min-height: 36px;
  display: flex;
  align-items: center;
}

.typing-indicator {
  display: flex;
  gap: 4px;
}
.typing-indicator .dot {
  width: 6px;
  height: 6px;
  background-color: #909399;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}
.typing-indicator .dot:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator .dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0%, 100% { transform: translateY(0); opacity: 0.5; }
  50% { transform: translateY(-4px); opacity: 1; }
}

/* 输入区域 */
.input-section {
  background-color: white;
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.03);
}

[data-theme="dark"] .input-section {
  background-color: #1e1e1e;
  border-top-color: #333;
}

.toolbar {
  display: flex;
  align-items: center;
}

.topic-selector {
  display: flex;
  align-items: center;
  gap: 8px;
}
.topic-selector .label {
  font-size: 13px;
  color: #606266;
}
[data-theme="dark"] .topic-selector .label {
  color: #a0a0a0;
}

.input-area-wrapper {
  position: relative;
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: white;
  padding: 2px;
}
.input-area-wrapper:focus-within {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

[data-theme="dark"] .input-area-wrapper {
  background: #2b2b2b;
  border-color: #444;
}
[data-theme="dark"] .input-area-wrapper:focus-within {
  border-color: #409eff;
}

.custom-textarea :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  background: transparent;
  padding: 6px 10px;
  font-size: 14px;
  line-height: 1.5;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 4px 8px 4px;
  gap: 12px;
}

.char-count {
  font-size: 12px;
  color: #909399;
}

.send-btn {
  width: 36px;
  height: 36px;
  font-size: 16px;
}

/* 过渡动画 */
.message-list-enter-active,
.message-list-leave-active {
  transition: all 0.3s cubic-bezier(0.18, 0.89, 0.32, 1.28); /* 弹性效果 */
}
.message-list-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.9);
}
.message-list-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}
</style>
