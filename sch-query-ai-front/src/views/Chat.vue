<template>
  <div class="chat-container">
    <el-container>
      <!-- 会话列表侧边栏 -->
      <el-aside width="300px" class="session-sidebar">
        <div class="session-header">
          <h3>聊天会话</h3>
          <el-button
              type="primary"
              class="new-session-btn"
              @click="createNewSession"
          >
            <el-icon class="plus-icon"><Plus /></el-icon>
            新建会话
          </el-button>
        </div>

        <div class="session-list" v-loading="sessionsLoading">
          <div
              v-for="session in sessions"
              :key="session.id"
              class="session-item"
              :class="{ active: currentSessionId === session.id }"
              @click="selectSession(session)"
          >
            <div class="session-info">
              <div class="session-name">{{ session.sessionName || '未命名会话' }}</div>
              <div class="session-time">{{ formatTime(session.lastMessageAt) }}</div>
            </div>
            <el-dropdown @command="(command) => handleSessionCommand(command, session)" trigger="click">
              <el-icon class="session-actions"><Setting /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename">
                    <el-icon><Edit /></el-icon>
                    重命名
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided>
                    <el-icon><Delete /></el-icon>
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <div v-if="sessions.length === 0 && !sessionsLoading" class="empty-sessions">
            <el-empty description="暂无会话" :image-size="80">
              <el-button type="primary" @click="createNewSession">创建第一个会话</el-button>
            </el-empty>
          </div>
        </div>
      </el-aside>

      <!-- 聊天主区域 -->
      <el-container class="chat-main">
        <el-header class="chat-header" v-if="currentSession">
          <div class="chat-title">
            <h3>{{ currentSession.sessionName || '未命名会话' }}</h3>
            <el-button type="text" size="small" @click="showRenameDialog = true">
              <el-icon><Edit /></el-icon>
            </el-button>
          </div>
        </el-header>

        <el-main class="chat-content">
          <div v-if="!currentSession" class="welcome-chat">
            <el-empty description="选择一个会话开始聊天" :image-size="120">
              <el-button type="primary" @click="createNewSession">创建新会话</el-button>
            </el-empty>
          </div>

          <div v-else class="chat-messages" ref="messagesContainer">
            <div
                v-for="message in messages"
                :key="message.id"
                class="message-item"
                :class="{ 'user-message': message.messageType === 0, 'ai-message': message.messageType === 1 }"
            >
              <div class="message-avatar">
                <el-avatar
                    :size="40"
                    :icon="message.messageType === 0 ? UserFilled : Service"
                    :class="{ 'user-avatar': message.messageType === 0, 'ai-avatar': message.messageType === 1 }"
                />
              </div>
              <div class="message-content">
                <div class="message-text" v-if="message.messageType === 0">{{ message.content }}</div>
                <div class="message-text markdown-content" v-else v-html="renderMarkdown(message.content)"></div>
                <div class="message-time">{{ formatTime(message.createdAt) }}</div>
              </div>
            </div>

            <div v-if="isTyping" class="message-item ai-message">
              <div class="message-avatar">
                <el-avatar :size="40" :icon="Service" class="ai-avatar" />
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
        </el-main>

        <el-footer class="chat-input" v-if="currentSession">
          <div class="input-container">
            <el-button
                :class="['voice-btn', { 'recording': isRecording, 'voice-loading': voiceLoading }]"
                @click="toggleRecording"
                :disabled="voiceLoading"
                :title="isRecording ? '停止录音' : '开始录音'"
            >
              <el-icon>
                <Microphone v-if="!isRecording" />
                <VideoPlay v-else />
              </el-icon>
            </el-button>
            <el-input
                v-model="userMessage"
                type="textarea"
                :rows="2"
            placeholder="输入您的消息..."
            @keydown.enter.prevent="handleEnterKey"
            :disabled="isTyping"
            resize="none"
            class="message-input"
            />
            <el-button
                type="primary"
                @click="sendMessage"
                :disabled="!userMessage.trim() || isTyping"
                :loading="isTyping"
                class="send-btn"
            >
              <el-icon><Upload /></el-icon>
            </el-button>
          </div>
        </el-footer>
      </el-container>
    </el-container>

    <!-- 重命名会话对话框 -->
    <el-dialog
        v-model="showRenameDialog"
        title="重命名会话"
        width="400px"
        @close="renameSessionName = ''"
    >
      <el-form label-width="80px">
        <el-form-item label="会话名称">
          <el-input v-model="renameSessionName" placeholder="请输入会话名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showRenameDialog = false">取消</el-button>
          <el-button type="primary" @click="renameSession">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElEmpty, ElAvatar, ElDropdown, ElDropdownMenu, ElDropdownItem, ElButton, ElInput, ElDialog, ElForm, ElFormItem, ElIcon } from 'element-plus'
import { Plus, Setting, Edit, Delete, UserFilled, Service, Upload, Microphone, VideoPlay } from '@element-plus/icons-vue'
import { chatApi } from '../api/chat'
import { marked } from 'marked'
import 'highlight.js/styles/github.css'

// 路由实例
const router = useRouter()

// 状态变量
const sessions = ref([])
const currentSession = ref(null)
const currentSessionId = ref(null)
const messages = ref([])
const userMessage = ref('')
const isTyping = ref(false)
const sessionsLoading = ref(false)
const showRenameDialog = ref(false)
const renameSessionName = ref('')
const messagesContainer = ref(null)

// 录音相关状态
const isRecording = ref(false)
const voiceLoading = ref(false)
const mediaRecorder = ref(null)
const audioChunks = ref([])

// 检查登录状态，返回是否已登录
const checkToken = () => {
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return false
  }
  return true
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

  return date.toLocaleDateString()
}

// Markdown渲染方法
const renderMarkdown = (text) => {
  if (!text) return ''

  try {
    marked.setOptions({
      breaks: true,
      gfm: true,
      highlight: (code) => `<pre><code class="hljs">${code}</code></pre>`
    })
    return marked.parse(text)
  } catch (error) {
    console.error('Markdown rendering error:', error)
    return text
  }
}

// 加载会话列表
const loadSessions = async () => {
  console.log('Loading sessions...')
  try {
    sessionsLoading.value = true
    const response = await chatApi.getSessions()
    console.log('Sessions response:', response)
    if (response?.code === 200) {
      sessions.value = response.data || []
      console.log('Sessions loaded:', sessions.value)
    }
  } catch (error) {
    console.error('Load sessions error:', error)
    if (error.code !== 401) ElMessage.error('加载会话列表失败')
  } finally {
    sessionsLoading.value = false
  }
}

// 创建新会话
const createNewSession = async () => {
  if (!checkToken()) return

  try {
    const response = await chatApi.createSession()
    if (response?.code === 200) {
      ElMessage.success('创建会话成功')
      await loadSessions()
      if (sessions.value.length > 0) selectSession(sessions.value[0])
    }
  } catch (error) {
    console.error('Create session error:', error)
    ElMessage.error(error.response?.data?.msg || '创建会话失败')
  }
}

// 选择会话
const selectSession = async (session) => {
  currentSession.value = session
  currentSessionId.value = session.id
  await loadMessages(session.id)
}

// 加载消息历史
const loadMessages = async (sessionId) => {
  try {
    const response = await chatApi.getMessages(sessionId)
    if (response?.code === 200) {
      messages.value = response.data || []
      nextTick(() => {
        if (messagesContainer.value) {
          messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
        }
      })
    }
  } catch (error) {
    if (error.code !== 401) ElMessage.error('加载消息失败')
  }
}

// 发送消息
const sendMessage = async () => {
  if (!userMessage.value.trim() || isTyping.value) return

  if (!checkToken()) return

  const messageContent = userMessage.value.trim()
  userMessage.value = ''

  // 添加用户消息
  const userMessageObj = {
    id: Date.now(),
    content: messageContent,
    messageType: 0,
    createdAt: new Date().toISOString()
  }
  messages.value.push(userMessageObj)

  // 显示输入状态
  isTyping.value = true
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })

  try {
    const baseURL = 'http://localhost:8080'
    const response = await fetch(`${baseURL}/user/message/send?sessionId=${currentSessionId.value}&content=${encodeURIComponent(messageContent)}`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`,
        'Content-Type': 'text/event-stream'
      }
    })

    if (!response.ok) throw new Error('发送失败')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let aiMessageText = ''

    isTyping.value = false
    const aiMessageObj = {
      id: Date.now() + 1,
      content: '',
      messageType: 1,
      createdAt: new Date().toISOString()
    }
    messages.value.push(aiMessageObj)

    // 读取流式数据
    let isReading = true
    while (isReading) {
      const { done, value } = await reader.read()

      if (done) {
        isReading = false
        break
      }

      let chunk = decoder.decode(value)
      chunk = chunk.replace(/^data:\s*/, '')
      const cleanChunk = chunk.replace(/\n+/g, ' ').replace(/\r+/g, '').replace(/\s+/g, ' ').trim()

      if (cleanChunk) {
        aiMessageText += cleanChunk
      }

      const aiMsgIndex = messages.value.findIndex(msg => msg.id === aiMessageObj.id)
      if (aiMsgIndex > -1) {
        messages.value[aiMsgIndex].content = aiMessageText
      }

      nextTick(() => {
        if (messagesContainer.value) {
          messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
        }
      })
    }

    await loadSessions()
  } catch (error) {
    console.error('Send message error:', error)
    isTyping.value = false

    if (error.code !== 401) ElMessage.error(error.message || '发送消息失败')
    const index = messages.value.findIndex(msg => msg.id === userMessageObj.id)
    if (index > -1) messages.value.splice(index, 1)
  } finally {
    isTyping.value = false
    nextTick(() => {
      if (messagesContainer.value) {
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
      }
    })
  }
}

// 处理回车键
const handleEnterKey = (event) => {
  event.ctrlKey ? (userMessage.value += '\n') : sendMessage()
}

// 处理会话操作
const handleSessionCommand = async (command, session) => {
  if (!checkToken()) return

  switch (command) {
    case 'rename':
      currentSession.value = session
      renameSessionName.value = session.sessionName || ''
      showRenameDialog.value = true
      break
    case 'delete':
      try {
        await ElMessageBox.confirm('确定要删除这个会话吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })

        const response = await chatApi.deleteSession({ id: session.id })
        if (response?.code === 200) {
          ElMessage.success('删除成功')
          await loadSessions()
          if (currentSessionId.value === session.id) {
            currentSession.value = null
            currentSessionId.value = null
            messages.value = []
          }
        }
      } catch (error) {
        if (error !== 'cancel' && error.code !== 401) ElMessage.error('删除失败')
      }
      break
  }
}

// 重命名会话
const renameSession = async () => {
  if (!renameSessionName.value.trim()) return

  if (!checkToken()) return

  try {
    const response = await chatApi.updateSession({
      id: currentSession.value.id,
      sessionName: renameSessionName.value.trim()
    })

    if (response?.code === 200) {
      ElMessage.success('重命名成功')
      currentSession.value.sessionName = renameSessionName.value.trim()
      await loadSessions()
      showRenameDialog.value = false
    }
  } catch (error) {
    if (error.code !== 401) ElMessage.error('重命名失败')
  }
}

// 切换录音状态
const toggleRecording = async () => {
  if (isRecording.value) {
    stopRecording()
  } else {
    await startRecording()
  }
}

// 开始录音
const startRecording = async () => {
  try {
    // 请求麦克风权限
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })

    // 创建MediaRecorder实例
    const recorder = new MediaRecorder(stream)
    mediaRecorder.value = recorder
    audioChunks.value = []

    // 监听数据可用事件
    recorder.ondataavailable = (event) => {
      audioChunks.value.push(event.data)
    }

    // 监听录音结束事件
    recorder.onstop = async () => {
      const audioBlob = new Blob(audioChunks.value, { type: 'audio/wav' })
      await uploadAudioFile(audioBlob)

      // 关闭所有音频轨道
      stream.getTracks().forEach(track => track.stop())
    }

    // 开始录音
    recorder.start()
    isRecording.value = true

    ElMessage.success('开始录音')
  } catch (error) {
    console.error('录音启动失败:', error)
    ElMessage.error('无法访问麦克风，请检查权限设置')
  }
}

// 停止录音
const stopRecording = () => {
  if (mediaRecorder.value && mediaRecorder.value.state !== 'inactive') {
    mediaRecorder.value.stop()
    isRecording.value = false
    ElMessage.info('录音结束，正在识别...')
  }
}

// 上传音频文件到后端
const uploadAudioFile = async (audioBlob) => {
  try {
    voiceLoading.value = true

    const formData = new FormData()
    formData.append('radioFile', audioBlob, 'recording.wav')

    const response = await fetch('http://localhost:8080/user/uploadAudioFile', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: formData
    })

    if (!response.ok) {
      throw new Error('音频上传失败')
    }

    const result = await response.json()

    if (result.code === 200 && result.data) {
      // 将识别的文字追加到输入框
      userMessage.value += (userMessage.value ? ' ' : '') + result.data
      ElMessage.success('语音识别完成')
    } else {
      ElMessage.error(result.msg || '语音识别失败')
    }
  } catch (error) {
    console.error('音频上传失败:', error)
    ElMessage.error('语音识别失败，请重试')
  } finally {
    voiceLoading.value = false
  }
}

// 挂载时加载
onMounted(() => {
  if (checkToken()) {
    loadSessions()
  }
})
</script>

<style scoped>
/* 全局基础样式 */
.chat-container {
  height: 100vh;
  background-color: #f9fafb;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  overflow: hidden;
  color: #111827;
}

/* 侧边栏样式 */
.session-sidebar {
  background-color: #ffffff;
  border-right: 1px solid #e5e7eb;
  box-shadow: 0 0 12px rgba(0, 0, 0, 0.03);
  width: 280px !important;
  transition: all 0.3s ease;
}

/* 侧边栏头部 */
.session-header {
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.session-header h3 {
  margin: 0;
  color: #111827;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.4;
}

/* 新建会话按钮 */
.new-session-btn {
  width: 100%;
  padding: 11px 16px;
  font-size: 15px;
  font-weight: 500;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  border: none;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.new-session-btn:hover {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
}

.new-session-btn:active {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.1);
}

.plus-icon {
  font-size: 16px;
}

/* 会话列表 */
.session-list {
  height: calc(100vh - 120px);
  overflow-y: auto;
  padding: 8px 0;
}

.session-list::-webkit-scrollbar {
  width: 6px;
}

.session-list::-webkit-scrollbar-thumb {
  background-color: #d1d5db;
  border-radius: 3px;
}

.session-list::-webkit-scrollbar-track {
  background-color: #f3f4f6;
}

/* 会话项 */
.session-item {
  padding: 12px 20px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: all 0.2s ease;
  border-radius: 0;
  margin: 0;
}

.session-item:hover {
  background-color: #f0f9ff;
}

.session-item.active {
  background-color: #dbeafe;
  border-left: 3px solid #3b82f6;
}

.session-info {
  flex: 1;
  overflow: hidden;
}

.session-name {
  font-weight: 500;
  color: #111827;
  margin-bottom: 3px;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  font-size: 11px;
  color: #6b7280;
}

/* 会话操作图标 */
.session-actions {
  color: #6b7280;
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
  font-size: 32px;
  transition: all 0.2s ease;
}

.session-actions:hover {
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
}

/* 聊天主区域 - 核心布局控制 */
.chat-main {
  height: 100vh;
  padding: 0;
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden; /* 关键：防止子元素溢出容器 */
}

/* 聊天头部 - 固定高度 */
.chat-header {
  background-color: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 24px;
  display: flex;
  align-items: center;
  height: 64px; /* 固定高度 */
  flex-shrink: 0; /* 禁止收缩 */
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-title h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  font-weight: 600;
}

.chat-title .el-button {
  color: #6b7280;
  transition: color 0.2s ease;
}

.chat-title .el-button:hover {
  color: #3b82f6;
}

/* 聊天内容区域 - 精确高度控制（解决挤压问题核心） */
.chat-content {
  background-color: #f9fafb;
  flex-grow: 1; /* 占据剩余空间 */
  max-height: calc(100vh - 64px - 88px); /* 总高度 - 头部(64px) - 输入区(88px) */
  overflow: hidden;
  position: relative;
}

.welcome-chat {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

/* 消息容器 */
.chat-messages {
  height: 100%;
  overflow-y: auto;
  padding: 24px;
  box-sizing: border-box; /* 确保padding不增加总高度 */
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.chat-messages::-webkit-scrollbar {
  width: 8px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background-color: #d1d5db;
  border-radius: 4px;
}

.chat-messages::-webkit-scrollbar-track {
  background-color: #f3f4f6;
}

/* 消息项样式 */
.message-item {
  display: flex;
  margin-bottom: 16px;
  align-items: flex-start;
  max-width: 100%;
}

.user-message {
  flex-direction: row-reverse;
}

/* 头像样式 */
.message-avatar {
  flex-shrink: 0;
}

.el-avatar {
  width: 42px !important;
  height: 42px !important;
  font-size: 18px !important;
  border-radius: 8px !important;
  transition: all 0.2s ease;
}

.el-avatar:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.user-avatar {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: white;
  border: 1px solid rgba(59, 130, 246, 0.2);
}

.ai-avatar {
  background-color: #10b981;
  color: white;
  border: 1px solid rgba(16, 185, 129, 0.2);
}

/* 消息气泡 */
.message-content {
  max-width: 70%;
  padding: 14px 18px;
  position: relative;
  line-height: 1.6;
  font-size: 14px;
  border-radius: 12px;
  transition: all 0.2s ease;
}

.message-content:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.user-message .message-content {
  margin-right: 12px;
  margin-left: auto;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: white;
  border-radius: 12px 12px 0 12px;
}

.ai-message .message-content {
  margin-left: 12px;
  margin-right: auto;
  background-color: #ffffff;
  color: #111827;
  border-radius: 12px 12px 12px 0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  border: 1px solid #f3f4f6;
}

/* 消息文本 */
.message-text {
  word-wrap: break-word;
  white-space: pre-wrap;
}

.markdown-content {
  font-size: 14px;
}

.markdown-content pre {
  background-color: #f9fafb;
  border-radius: 6px;
  padding: 12px;
  margin: 8px 0;
  overflow-x: auto;
  color: #111827;
}

.markdown-content code {
  background-color: #f3f4f6;
  padding: 2px 4px;
  border-radius: 4px;
  font-size: 13px;
}

.markdown-content h1,
.markdown-content h2,
.markdown-content h3 {
  margin: 16px 0 8px;
  font-weight: 600;
}

.markdown-content ul,
.markdown-content ol {
  margin: 8px 0;
  padding-left: 24px;
}

/* 消息时间 */
.message-time {
  font-size: 11px;
  opacity: 0.7;
  margin-top: 6px;
  text-align: right;
}

/* 输入区域 - 固定高度 */
.chat-input {
  background-color: #ffffff;
  border-top: 1px solid #e5e7eb;
  padding: 16px 24px;
  height: 88px; /* 固定高度 */
  flex-shrink: 0; /* 禁止收缩 */
  box-sizing: border-box;
  box-shadow: 0 -1px 3px rgba(0, 0, 0, 0.03);
}

/* 输入框容器 */
.input-container {
  max-width: 1000px;
  margin: 0 auto;
  position: relative;
  width: 100%;
  height: 100%; /* 继承输入区高度 */
  display: flex;
  align-items: center; /* 垂直居中 */
}

/* 录音按钮样式 */
.voice-btn {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background-color: #f3f4f6;
  border: 1px solid #e5e7eb;
  color: #6b7280;
  transition: all 0.2s ease;
  z-index: 2;
}

.voice-btn:hover {
  background-color: #e5e7eb;
  color: #374151;
  border-color: #d1d5db;
  transform: translateY(-50%) scale(1.05);
}

.voice-btn.recording {
  background-color: #ef4444;
  border-color: #dc2626;
  color: white;
  animation: recording-pulse 1.5s infinite ease-in-out;
}

.voice-btn.voice-loading {
  background-color: #fbbf24;
  border-color: #f59e0b;
  color: white;
}

.voice-btn:disabled {
  background-color: #f9fafb;
  color: #9ca3af;
  border-color: #e5e7eb;
  cursor: not-allowed;
  transform: translateY(-50%);
}

@keyframes recording-pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4);
    transform: translateY(-50%) scale(1);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(239, 68, 68, 0.1);
    transform: translateY(-50%) scale(1.05);
  }
}

/* 输入框样式 */
.message-input {
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  padding: 14px 20px;
  padding-left: 72px; /* 给录音按钮留出空间 */
  padding-right: 72px;
  min-height: 56px;
  max-height: 200px; /* 限制最大高度 */
  box-sizing: border-box;
  font-size: 14px;
  line-height: 1.6;
  transition: all 0.2s ease;
  resize: none;
  flex-grow: 1; /* 占满容器宽度 */
}

.message-input::placeholder {
  color: #9ca3af;
  font-size: 14px;
  opacity: 1;
}

.el-input:hover .el-input__inner {
  border-color: #93c5fd;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.08);
}

.el-input:focus-within .el-input__inner {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
  outline: none;
}

/* 发送按钮 */
.send-btn {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.2);
  transition: all 0.2s ease;
}

.send-btn:hover {
  transform: translateY(-50%) scale(1.05);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.25);
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
}

.send-btn:active {
  transform: translateY(-50%) scale(0.98);
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.2);
}

.send-btn:disabled {
  background: #f3f4f6;
  color: #9ca3af;
  box-shadow: none;
  transform: translateY(-50%);
}

/* 输入状态指示器 */
.typing-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #6b7280;
  animation: typing 1.4s infinite ease-in-out both;
}

.dot:nth-child(1) {
  animation-delay: -0.32s;
}

.dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes typing {
  0%, 80%, 100% {
    transform: scale(0.6);
  }
  40% {
    transform: scale(1);
  }
}

/* 对话框样式 */
.el-dialog {
  border-radius: 12px !important;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
}

.el-dialog__header {
  padding: 18px 24px !important;
  border-bottom: 1px solid #f3f4f6;
}

.el-dialog__title {
  font-size: 16px !important;
  font-weight: 600 !important;
  color: #111827 !important;
}

.el-dialog__body {
  padding: 24px !important;
}

.el-dialog__footer {
  padding: 16px 24px !important;
  border-top: 1px solid #f3f4f6;
}

.el-form-item {
  margin-bottom: 0 !important;
}

.el-form-item__label {
  font-size: 14px !important;
  color: #374151 !important;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .session-sidebar {
    width: 260px !important;
  }

  .message-content {
    max-width: 75%;
  }
}

@media (max-width: 768px) {
  .session-sidebar {
    position: absolute;
    z-index: 10;
    height: 100vh;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
  }

  .session-sidebar.show {
    transform: translateX(0);
  }

  .chat-header {
    padding: 0 16px;
  }

  .chat-content {
    max-height: calc(100vh - 64px - 80px); /* 移动端输入区略窄 */
  }

  .chat-messages {
    padding: 16px;
  }

  .chat-input {
    height: 80px; /* 移动端输入区高度 */
    padding: 12px 16px;
  }

  .message-content {
    max-width: 85%;
    padding: 12px 16px;
  }

  .el-avatar {
    width: 38px !important;
    height: 38px !important;
    font-size: 16px !important;
  }

  .send-btn {
    width: 44px;
    height: 44px;
  }

  .message-input {
    min-height: 52px;
    padding-left: 64px; /* 移动端录音按钮空间 */
    padding-right: 64px;
  }

  .voice-btn {
    width: 44px;
    height: 44px;
    left: 10px;
  }
}

@media (max-width: 480px) {
  .message-content {
    max-width: 90%;
  }

  .session-header {
    padding: 14px 16px;
  }

  .session-item {
    padding: 10px 16px;
  }

  .chat-messages {
    padding: 12px;
  }

  .message-item {
    margin-bottom: 12px;
  }
}
</style>