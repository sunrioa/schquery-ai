<template>
  <div class="customer-service-page">
    <el-container class="service-container">
      <!-- 头部 -->
      <el-header class="service-header">
        <div class="header-content">
          <el-button type="text" @click="goBack" class="back-btn">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <h2>客服对话</h2>
        </div>
      </el-header>

      <!-- 聊天主区域 -->
      <el-main class="service-main">
        <div class="chat-wrapper">
          <!-- 消息列表 -->
          <div class="messages-area" ref="messagesContainer">
            <div v-if="messages.length === 0 && !loading" class="empty-state">
              <el-empty description="暂无消息，开始咨询吧" :image-size="120">
                <el-button type="primary" @click="focusInput">发送消息</el-button>
              </el-empty>
            </div>

            <div v-else>
              <div
                  v-for="msg in messages"
                  :key="`msg-${msg.id}`"
                  class="message-item"
                  :class="{ 'user-msg': msg.senderType === 1, 'admin-msg': msg.senderType === 2 }"
              >
                <div class="message-avatar">
                  <el-avatar
                      :size="36"
                      :src="msg.senderType === 1 ? userStore.getDisplayAvatar() : getAdminAvatar()"
                      :icon="null"
                  />
                </div>
                <div class="message-content">
                  <div class="sender-name">
                    {{ msg.senderType === 1 ? userStore.userInfo.userName : '客服' }}
                  </div>
                  <div class="message-text">{{ msg.messageContent }}</div>
                  <div class="message-time">{{ formatTime(msg.createTime) }}</div>
                </div>
              </div>

              <!-- 输入中指示器 -->
              <div v-if="isTyping" class="message-item admin-msg">
                <div class="message-avatar">
                  <el-avatar :size="36" :src="getAdminAvatar()" :icon="null" />
                </div>
                <div class="message-content">
                  <div class="typing-indicator">
                    <span class="dot"></span>
                    <span class="dot"></span>
                    <span class="dot"></span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 输入区域 -->
          <div class="input-area">
            <div class="topic-selector">
              <el-select v-model="topic" placeholder="请选择咨询主题" size="small">
                <el-option label="一般问题" value="general" />
                <el-option label="账户问题" value="account" />
                <el-option label="功能问题" value="feature" />
                <el-option label="其他问题" value="other" />
              </el-select>
            </div>

            <div class="message-input-wrapper">
              <el-input
                  ref="inputRef"
                  v-model="messageInput"
                  type="textarea"
                  :rows="3"
                  placeholder="输入您的问题或反馈..."
                  @keydown.enter.prevent="handleEnterKey"
                  resize="none"
                  class="message-input"
              />
              <el-button
                  type="primary"
                  @click="sendMessage"
                  :loading="sending"
                  :disabled="!messageInput.trim()"
                  class="send-btn"
              >
                <el-icon><Upload /></el-icon>
                发送
              </el-button>
            </div>
          </div>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Upload } from '@element-plus/icons-vue'
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

  // 从userStore获取用户ID
  userId.value = userStore.userInfo.id

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
      messages.value = result.data || []
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
    const token = localStorage.getItem('token')
    const wsUrl = `ws://localhost:8080/ws/customer-service?userId=${userId.value}&userType=user`
    webSocket.value = new WebSocket(wsUrl)

    webSocket.value.onopen = () => {
      console.log('WebSocket连接已建立')
      ElMessage.success('已连接到客服')
    }

    webSocket.value.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        handleWebSocketMessage(data)
      } catch (error) {
        console.error('处理WebSocket消息失败:', error)
      }
    }

    webSocket.value.onerror = (error) => {
      console.error('WebSocket错误:', error)
      ElMessage.error('连接出错，请刷新页面')
    }

    webSocket.value.onclose = () => {
      console.log('WebSocket连接已关闭')
      ElMessage.warning('连接已断开')
    }
  } catch (error) {
    console.error('连接WebSocket失败:', error)
    ElMessage.error('连接失败，请刷新页面')
  }
}

// 处理WebSocket消息
const handleWebSocketMessage = (data) => {
  if (data.type === 'message') {
    messages.value.push(data.data)
    isTyping.value = false
    nextTick(() => {
      scrollToBottom()
    })
  } else if (data.type === 'typing') {
    isTyping.value = true
  } else if (data.type === 'stopped_typing') {
    isTyping.value = false
  }
}

// 发送消息
const sendMessage = async () => {
  if (!messageInput.value.trim()) return

  try {
    sending.value = true

    const token = localStorage.getItem('token')
    const response = await fetch('http://localhost:8080/customer-service/send', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        messageContent: messageInput.value,
        topic: topic.value,
        senderType: 1
      })
    })

    if (!response.ok) {
      throw new Error('发送消息失败')
    }

    const result = await response.json()
    if (result.code === 200) {
      messages.value.push(result.data)
      messageInput.value = ''

      if (webSocket.value && webSocket.value.readyState === WebSocket.OPEN) {
        webSocket.value.send(JSON.stringify({
          type: 'message',
          data: result.data
        }))
      }

      nextTick(() => {
        scrollToBottom()
        inputRef.value?.focus()
      })

      ElMessage.success('消息已发送')
    } else {
      throw new Error(result.msg || '发送失败')
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    ElMessage.error(error.message || '发送消息失败')
  } finally {
    sending.value = false
  }
}

// 处理Enter键
const handleEnterKey = () => {
  if (messageInput.value.trim()) {
    sendMessage()
  }
}

// 返回上一页
const goBack = () => {
  router.back()
}

// 焦点到输入框
const focusInput = () => {
  inputRef.value?.focus()
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 获取管理员头像
const getAdminAvatar = () => {
  return 'https://cube.elemecdn.com/0/88/ff0b88ba1220c6fb3b85e36ae2d47png'
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`

  return date.toLocaleString()
}
</script>

<style scoped>
.customer-service-page {
  width: 100%;
  height: 100vh;
  background-color: #f9fafb;
}

[data-theme="dark"] .customer-service-page {
  background-color: #111827;
}

.service-container {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.service-header {
  background-color: white;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  padding: 0 24px;
  height: 64px;
}

[data-theme="dark"] .service-header {
  background-color: #1f2937;
  border-bottom-color: #374151;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}

.header-content h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}

[data-theme="dark"] .header-content h2 {
  color: #f9fafb;
}

.back-btn {
  color: #6b7280;
  font-size: 18px;
  padding: 0;
  height: auto;
}

[data-theme="dark"] .back-btn {
  color: #e5e7eb;
}

.back-btn:hover {
  color: #111827;
}

[data-theme="dark"] .back-btn:hover {
  color: #f9fafb;
}

.service-main {
  flex: 1;
  overflow: hidden;
  padding: 0;
}

.chat-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #f9fafb;
}

[data-theme="dark"] .chat-wrapper {
  background-color: #111827;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.message-item {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  margin-bottom: 12px;
  animation: slideIn 0.3s ease;
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

.message-item.user-msg {
  flex-direction: row-reverse;
  justify-content: flex-start;
}

.message-item.admin-msg {
  justify-content: flex-start;
}

.message-avatar {
  flex-shrink: 0;
}

.message-avatar :deep(.el-avatar) {
  background-color: #e5e7eb;
}

.message-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 60%;
}

.message-item.user-msg .message-content {
  align-items: flex-end;
}

.message-item.admin-msg .message-content {
  align-items: flex-start;
}

.sender-name {
  font-size: 12px;
  color: #9ca3af;
  padding: 0 8px;
}

.message-text {
  background-color: white;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 12px 16px;
  word-wrap: break-word;
  word-break: break-word;
  line-height: 1.5;
}

.message-item.user-msg .message-text {
  background-color: #3b82f6;
  color: white;
  border-color: #2563eb;
  border-bottom-right-radius: 4px;
}

.message-item.admin-msg .message-text {
  background-color: #f3f4f6;
  color: #374151;
  border-color: #e5e7eb;
  border-bottom-left-radius: 4px;
}

.message-time {
  font-size: 12px;
  color: #9ca3af;
  padding: 0 8px;
}

.message-item.user-msg .message-time {
  text-align: right;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 12px;
}

.typing-indicator .dot {
  width: 8px;
  height: 8px;
  background-color: #9ca3af;
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

[data-theme="dark"] .message-text {
  background-color: #374151;
  border-color: #4b5563;
  color: #e5e7eb;
}

[data-theme="dark"] .message-item.admin-msg .message-text {
  background-color: #374151;
  color: #e5e7eb;
  border-color: #4b5563;
  border-bottom-left-radius: 4px;
}

.input-area {
  padding: 16px 24px;
  background-color: white;
  border-top: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

[data-theme="dark"] .input-area {
  background-color: #1f2937;
  border-top-color: #374151;
}

.topic-selector {
  width: 100%;
}

.topic-selector :deep(.el-select) {
  width: 100%;
}

.message-input-wrapper {
  display: flex;
  gap: 12px;
}

.message-input {
  flex: 1;
}

.send-btn {
  flex-shrink: 0;
  padding: 8px 16px;
  height: auto;
  align-self: flex-end;
}
</style>
