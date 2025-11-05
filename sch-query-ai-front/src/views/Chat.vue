<template>
  <div class="chat-container">
    <el-container>
      <!-- 会话列表侧边栏 -->
      <el-aside width="300px" class="session-sidebar">
        <div class="session-header">
          <h3>聊天会话</h3>
          <el-button type="primary" size="small" @click="createNewSession" :icon="Plus">
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
              <el-icon class="session-actions"><MoreFilled /></el-icon>
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
            <el-input
              v-model="userMessage"
              type="textarea"
              :rows="3"
              placeholder="输入您的消息..."
              @keydown.enter.prevent="handleEnterKey"
              :disabled="isTyping"
              resize="none"
            />
            <div class="input-actions">
              <el-button
                type="primary"
                @click="sendMessage"
                :disabled="!userMessage.trim() || isTyping"
                :loading="isTyping"
              >
                发送
              </el-button>
            </div>
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
      <el-form :model="renameForm" label-width="80px">
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

<script>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  MoreFilled,
  Edit,
  Delete,
  UserFilled,
  Service
} from '@element-plus/icons-vue'
import { chatApi } from '../api/chat'
import { marked } from 'marked'
import 'highlight.js/styles/github.css'

export default {
  name: 'ChatPage',
  components: {
    Plus,
    MoreFilled,
    Edit,
    Delete,
    UserFilled,
    Service
  },
  setup() {
    const router = useRouter()
    const sessions = ref([])
    const currentSession = ref(null)
    const currentSessionId = ref(null)
    const messages = ref([])
    const userMessage = ref('')
    const isTyping = ref(false)
    const sessionsLoading = ref(false)
    const messagesLoading = ref(false)
    const showRenameDialog = ref(false)
    const renameSessionName = ref('')
    const renameForm = ref({})
    const messagesContainer = ref(null)

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
        // 配置marked选项
        marked.setOptions({
          breaks: true,
          gfm: true,
          highlight: function(code, lang) {
            // 这里可以添加代码高亮，但为了简化先返回原代码
            return `<pre><code class="hljs">${code}</code></pre>`
          }
        })

        return marked.parse(text)
      } catch (error) {
        console.error('Markdown rendering error:', error)
        return text // 如果渲染失败，返回原文本
      }
    }

    // 加载会话列表
    const loadSessions = async () => {
      console.log('Loading sessions...')
      try {
        sessionsLoading.value = true
        const response = await chatApi.getSessions()
        console.log('Sessions response:', response)
        if (response && response.code === 200) {
          sessions.value = response.data || []
          console.log('Sessions loaded:', sessions.value)
        }
      } catch (error) {
        console.error('Load sessions error:', error)
        // 401错误已经在拦截器中处理了
        if (error.code !== 401) {
          ElMessage.error('加载会话列表失败')
        }
      } finally {
        sessionsLoading.value = false
      }
    }

    // 创建新会话
    const createNewSession = async () => {
      // 检查是否已登录
      const token = localStorage.getItem('token')
      if (!token) {
        ElMessage.warning('请先登录')
        router.push('/login')
        return
      }

      try {
        const response = await chatApi.createSession()
        if (response && response.code === 200) {
          ElMessage.success('创建会话成功')
          await loadSessions()
          // 自动选择最新创建的会话（假设返回的会话列表按时间倒序）
          if (sessions.value.length > 0) {
            selectSession(sessions.value[0])
          }
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
        messagesLoading.value = true
        const response = await chatApi.getMessages(sessionId)
        if (response && response.code === 200) {
          messages.value = response.data || []
          // 滚动到底部
          nextTick(() => {
            if (messagesContainer.value) {
              messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
            }
          })
        }
      } catch (error) {
        // 401错误已经在拦截器中处理了
        if (error.code !== 401) {
          ElMessage.error('加载消息失败')
        }
      } finally {
        messagesLoading.value = false
      }
    }

    // 发送消息
    const sendMessage = async () => {
      if (!userMessage.value.trim() || isTyping.value) return

      // 检查是否已登录
      const token = localStorage.getItem('token')
      if (!token) {
        ElMessage.warning('请先登录')
        router.push('/login')
        return
      }

      const messageContent = userMessage.value.trim()
      userMessage.value = ''

      // 添加用户消息到界面
      const userMessageObj = {
        id: Date.now(),
        content: messageContent,
        messageType: 0,
        createdAt: new Date().toISOString()
      }
      messages.value.push(userMessageObj)

      // 显示AI正在输入
      isTyping.value = true
      nextTick(() => {
        if (messagesContainer.value) {
          messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
        }
      })

      try {
        // 调用API发送消息（流式响应）
        const baseURL = 'http://localhost:8080' // 使用8081端口
        const response = await fetch(`${baseURL}/user/message/send?sessionId=${currentSessionId.value}&content=${encodeURIComponent(messageContent)}`, {
          method: 'GET',
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'text/event-stream'
          }
        })

        if (!response.ok) {
          throw new Error('发送失败')
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let aiMessageText = ''

        // 移除typing指示器，准备显示流式响应
        isTyping.value = false

        // 创建AI消息对象
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

          const chunk = decoder.decode(value)

          // 过滤掉所有的 "data:" 前缀，只保留实际内容
          const cleanChunk = chunk.replace(/data:\s*/g, '')
          aiMessageText += cleanChunk

          // 更新AI消息内容
          const aiMsgIndex = messages.value.findIndex(msg => msg.id === aiMessageObj.id)
          if (aiMsgIndex > -1) {
            messages.value[aiMsgIndex].content = aiMessageText
          }

          // 滚动到底部
          nextTick(() => {
            if (messagesContainer.value) {
              messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
            }
          })
        }

        // 流式传输完成，更新会话列表
        await loadSessions()

      } catch (error) {
        console.error('Send message error:', error)
        // 移除typing指示器
        isTyping.value = false

        // 401错误已经在拦截器中处理了
        if (error.code !== 401) {
          ElMessage.error(error.message || '发送消息失败')
        }

        // 移除用户消息（因为发送失败）
        const index = messages.value.findIndex(msg => msg.id === userMessageObj.id)
        if (index > -1) {
          messages.value.splice(index, 1)
        }
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
      if (event.ctrlKey) {
        userMessage.value += '\n'
      } else {
        sendMessage()
      }
    }

    // 处理会话操作
    const handleSessionCommand = async (command, session) => {
      // 检查是否已登录
      const token = localStorage.getItem('token')
      if (!token) {
        ElMessage.warning('请先登录')
        router.push('/login')
        return
      }

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
            if (response && response.code === 200) {
              ElMessage.success('删除成功')
              await loadSessions()
              if (currentSessionId.value === session.id) {
                currentSession.value = null
                currentSessionId.value = null
                messages.value = []
              }
            }
          } catch (error) {
            if (error !== 'cancel' && error.code !== 401) {
              ElMessage.error('删除失败')
            }
          }
          break
      }
    }

    // 重命名会话
    const renameSession = async () => {
      if (!renameSessionName.value.trim()) return

      // 检查是否已登录
      const token = localStorage.getItem('token')
      if (!token) {
        ElMessage.warning('请先登录')
        router.push('/login')
        return
      }

      try {
        const response = await chatApi.updateSession({
          id: currentSession.value.id,
          sessionName: renameSessionName.value.trim()
        })

        if (response && response.code === 200) {
          ElMessage.success('重命名成功')
          currentSession.value.sessionName = renameSessionName.value.trim()
          await loadSessions()
          showRenameDialog.value = false
        }
      } catch (error) {
        // 401错误已经在拦截器中处理了
        if (error.code !== 401) {
          ElMessage.error('重命名失败')
        }
      }
    }

    onMounted(() => {
      // 检查是否已登录
      const token = localStorage.getItem('token')
      if (!token) {
        ElMessage.warning('请先登录')
        router.push('/login')
        return
      }
      loadSessions()
    })

    return {
      sessions,
      currentSession,
      currentSessionId,
      messages,
      userMessage,
      isTyping,
      sessionsLoading,
      messagesLoading,
      showRenameDialog,
      renameSessionName,
      renameForm,
      messagesContainer,
      formatTime,
      renderMarkdown,
      createNewSession,
      selectSession,
      sendMessage,
      handleEnterKey,
      handleSessionCommand,
      renameSession
    }
  }
}
</script>

<style scoped>
.chat-container {
  height: 100vh;
  background-color: #f5f5f5;
}

.session-sidebar {
  background-color: #fff;
  border-right: 1px solid #e4e7ed;
}

.session-header {
  padding: 20px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.session-header h3 {
  margin: 0;
  color: #303133;
}

.session-list {
  height: calc(100vh - 80px);
  overflow-y: auto;
}

.session-item {
  padding: 15px 20px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: background-color 0.3s;
}

.session-item:hover {
  background-color: #f5f7fa;
}

.session-item.active {
  background-color: #ecf5ff;
  border-left: 3px solid #409eff;
}

.session-info {
  flex: 1;
  margin-right: 10px;
}

.session-name {
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}

.session-time {
  font-size: 12px;
  color: #909399;
}

.session-actions {
  color: #909399;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}

.session-actions:hover {
  color: #409eff;
  background-color: #f0f9ff;
}

.empty-sessions {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

.chat-main {
  height: 100vh;
}

.chat-header {
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 20px;
  display: flex;
  align-items: center;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-title h3 {
  margin: 0;
  color: #303133;
}

.chat-content {
  background-color: #fff;
  margin: 1px;
  border-radius: 8px;
  overflow: hidden;
}

.welcome-chat {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.chat-messages {
  height: 100%;
  overflow-y: auto;
  padding: 20px;
}

.message-item {
  display: flex;
  margin-bottom: 20px;
  align-items: flex-start;
}

.user-message {
  flex-direction: row-reverse;
}

.user-message .message-content {
  margin-right: 12px;
  margin-left: 0;
  background-color: #409eff;
  color: white;
}

.user-message .message-text {
  text-align: right;
}

.ai-message .message-content {
  margin-left: 12px;
  margin-right: 0;
  background-color: #f4f4f4;
  color: #303133;
}

.message-avatar {
  flex-shrink: 0;
}

.user-avatar {
  background-color: #409eff;
  color: white;
}

.ai-avatar {
  background-color: #67c23a;
  color: white;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  position: relative;
}

.message-text {
  line-height: 1.5;
  word-wrap: break-word;
}

.markdown-content {
  line-height: 1.6;
}

.markdown-content h1, .markdown-content h2, .markdown-content h3 {
  margin: 12px 0 8px 0;
  color: #303133;
  font-weight: 600;
}

.markdown-content h1 { font-size: 1.5em; }
.markdown-content h2 { font-size: 1.3em; }
.markdown-content h3 { font-size: 1.1em; }

.markdown-content p {
  margin: 8px 0;
  color: #606266;
}

.markdown-content ul, .markdown-content ol {
  margin: 8px 0;
  padding-left: 20px;
}

.markdown-content li {
  margin: 4px 0;
  color: #606266;
}

.markdown-content code {
  background-color: #f5f7fa;
  color: #e6a23c;
  padding: 2px 4px;
  border-radius: 3px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.9em;
}

.markdown-content pre {
  background-color: #f6f8fa;
  border: 1px solid #e9ecef;
  border-radius: 4px;
  padding: 12px;
  overflow-x: auto;
  margin: 8px 0;
}

.markdown-content pre code {
  background: none;
  padding: 0;
  color: #333;
}

.markdown-content blockquote {
  border-left: 4px solid #dfe2e5;
  margin: 8px 0;
  padding: 8px 12px;
  background-color: #f8f9fa;
  color: #6c757d;
}

.markdown-content table {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
  border: 1px solid #ebeef5;
}

.markdown-content th, .markdown-content td {
  border: 1px solid #ebeef5;
  padding: 8px 12px;
  text-align: left;
}

.markdown-content th {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #303133;
}

.markdown-content tr:nth-child(even) {
  background-color: #fafafa;
}

.markdown-content a {
  color: #409eff;
  text-decoration: none;
}

.markdown-content a:hover {
  text-decoration: underline;
}

.message-time {
  font-size: 12px;
  opacity: 0.7;
  margin-top: 4px;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  align-items: center;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #909399;
  animation: typing 1.4s infinite ease-in-out;
}

.dot:nth-child(1) { animation-delay: 0s; }
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0%, 60%, 100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-10px);
  }
}

.chat-input {
  background-color: #fff;
  border-top: 1px solid #e4e7ed;
  padding: 20px;
}

.input-container {
  max-width: 800px;
  margin: 0 auto;
}

.input-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>