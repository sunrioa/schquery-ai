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
        <el-tooltip content="刷新" placement="bottom">
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
            <!-- 管理员消息 -->
            <div v-if="msg.senderType === 2" class="message-wrapper">
              <div class="message-bubble admin-bubble">
                <div class="bubble-content">{{ msg.messageContent }}</div>
              </div>
              <div class="message-time">{{ formatTime(msg.createTime) }}</div>
            </div>

            <!-- 用户消息 -->
            <div v-else class="message-wrapper">
              <div class="message-bubble user-bubble">
                <div class="bubble-content">{{ msg.messageContent }}</div>
              </div>
              <div class="message-time">{{ formatTime(msg.createTime) }}</div>
            </div>
          </div>

          <!-- 输入中指示器 -->
          <div v-if="isTyping" key="typing-indicator" class="message-group admin-group">
            <div class="message-wrapper">
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
        <div class="topic-bar">
          <span class="label">咨询主题：</span>
          <el-select v-model="topic" placeholder="请选择主题" size="small" class="topic-select">
            <el-option label="一般问题" value="general" />
            <el-option label="账户问题" value="account" />
            <el-option label="功能问题" value="feature" />
            <el-option label="技术支持" value="tech" />
            <el-option label="其他问题" value="other" />
          </el-select>
        </div>

        <div class="input-box">
          <el-input
              ref="inputRef"
              v-model="messageInput"
              type="textarea"
              :rows="3"
              placeholder="输入您的问题或反馈...按 Shift+Enter 换行，Enter 发送"
              @keydown.enter.prevent="handleEnterKey"
              resize="none"
              maxlength="500"
              show-word-limit
          />
          <div class="input-actions">
            <el-button
                type="primary"
                @click="sendMessage"
                :loading="sending"
                :disabled="!messageInput.trim() || sending"
                size="large"
            >
              <el-icon><SendFilled /></el-icon>
              发送
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
import { ArrowLeft, Refresh, ChatLineRound, SendFilled } from '@element-plus/icons-vue'
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

  // 连接WebSocket
  connectWebSocket()
})

// 清理
onUnmounted(() => {
  if (webSocket.value) {
    webSocket.value.close()
  }
})

// 格式化时间
const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  
  return `${year}/${month}/${day} ${hours}:${minutes}:${seconds}`
}

// 加载消息历史
const loadMessageHistory = async () => {
  try {
    loading.value = true
    const token = localStorage.getItem('token')
    const response = await fetch('http://localhost:8080/customer-service/history', {
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
    const token = localStorage.getItem('token')
    const wsUrl = `ws://localhost:8080/ws/customer-service?userId=${userId.value}&userType=user`
    console.log('[ChatWindow] WebSocket URL:', wsUrl)
    webSocket.value = new WebSocket(wsUrl)

    webSocket.value.onopen = () => {
      console.log('[ChatWindow] ========== WebSocket连接已建立 ==========')
      console.log('[ChatWindow] 用户ID:', userId.value)
      isOnline.value = true
      ElMessage.success('已连接到客服')
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
            createTime: new Date().toISOString()
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
      createTime: new Date().toISOString()
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
      const response = await fetch('http://localhost:8080/customer-service/send', {
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
    ElMessage.success('消息已发送')
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
  background-color: #fff;
  overflow: hidden;
}

/* 深色主题 */
[data-theme="dark"] .chat-window {
  background-color: #1a1a1a;
}

/* 头部 */
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: linear-gradient(135deg, #1e90ff 0%, #0066cc 100%);
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  min-height: 70px;
}

[data-theme="dark"] .chat-header {
  background: linear-gradient(135deg, #1e90ff 0%, #0066cc 100%);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  color: white;
  font-size: 24px;
  cursor: pointer;
  transition: transform 0.2s;
}

.back-btn:hover {
  transform: scale(1.1);
}

.header-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.header-info h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  opacity: 0.9;
}

.status .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #ff6b6b;
  animation: pulse 2s infinite;
}

.status.online .dot {
  background-color: #51cf66;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

.header-right {
  display: flex;
  gap: 12px;
}

.header-right :deep(.el-button) {
  color: white;
  font-size: 18px;
}

.header-right :deep(.el-button:hover) {
  color: #e9ecef;
}

/* 主内容区 */
.chat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f8f9fa;
}

[data-theme="dark"] .chat-content {
  background-color: #252525;
}

/* 消息容器 */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.messages-container::-webkit-scrollbar {
  width: 8px;
}

.messages-container::-webkit-scrollbar-track {
  background-color: transparent;
}

.messages-container::-webkit-scrollbar-thumb {
  background-color: #ddd;
  border-radius: 4px;
}

.messages-container::-webkit-scrollbar-thumb:hover {
  background-color: #999;
}

[data-theme="dark"] .messages-container::-webkit-scrollbar-thumb {
  background-color: #555;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.empty-icon {
  font-size: 80px;
  color: #ddd;
  margin-bottom: 16px;
}

/* 消息列表 */
.messages-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-group {
  display: flex;
  animation: slideIn 0.3s ease;
}

.message-group.user-group {
  justify-content: flex-end;
}

.message-group.admin-group {
  justify-content: flex-start;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-wrapper {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 70%;
}

.message-group.user-group .message-wrapper {
  align-items: flex-end;
}

.message-group.admin-group .message-wrapper {
  align-items: flex-start;
}

/* 消息气泡 */
.message-bubble {
  padding: 12px 16px;
  border-radius: 16px;
  word-wrap: break-word;
  word-break: break-word;
  line-height: 1.6;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.user-bubble {
  background: linear-gradient(135deg, #1e90ff 0%, #0066cc 100%);
  color: white;
  border-bottom-right-radius: 4px;
}

.admin-bubble {
  background-color: white;
  color: #333;
  border: 1px solid #e0e0e0;
  border-bottom-left-radius: 4px;
}

[data-theme="dark"] .admin-bubble {
  background-color: #2a2a2a;
  color: #e0e0e0;
  border-color: #444;
}

.bubble-content {
  white-space: pre-wrap;
}

/* 消息时间 */
.message-time {
  font-size: 12px;
  color: #999;
  padding: 0 8px;
}

.message-group.user-group .message-time {
  text-align: right;
}

[data-theme="dark"] .message-time {
  color: #777;
}

/* 输入指示器 */
.typing-bubble {
  padding: 12px 16px;
  min-height: 40px;
  display: flex;
  align-items: center;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  align-items: center;
}

.typing-indicator .dot {
  width: 8px;
  height: 8px;
  background-color: #1e90ff;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-indicator .dot:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator .dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    opacity: 0.5;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-8px);
  }
}

/* 输入区域 */
.input-section {
  padding: 16px 24px;
  background-color: white;
  border-top: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

[data-theme="dark"] .input-section {
  background-color: #1f1f1f;
  border-top-color: #333;
}

/* 主题选择 */
.topic-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.topic-bar .label {
  font-size: 14px;
  color: #666;
  white-space: nowrap;
}

[data-theme="dark"] .topic-bar .label {
  color: #999;
}

.topic-select {
  width: 200px;
}

/* 输入框 */
.input-box {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-box :deep(.el-textarea) {
  flex: 1;
  max-height: 120px;
}

.input-box :deep(.el-textarea__inner) {
  font-size: 14px;
  line-height: 1.6;
  resize: none;
}

.input-box :deep(.el-input__count) {
  color: #999;
}

[data-theme="dark"] .input-box :deep(.el-textarea__inner) {
  background-color: #2a2a2a;
  color: #e0e0e0;
  border-color: #444;
}

.input-actions {
  display: flex;
  gap: 12px;
}

.input-actions :deep(.el-button) {
  flex-shrink: 0;
}

/* 过渡动画 */
.message-list-enter-active,
.message-list-leave-active {
  transition: all 0.3s ease;
}

.message-list-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.message-list-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
