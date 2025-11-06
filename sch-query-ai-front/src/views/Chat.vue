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
            <!-- 录音按钮专用容器 -->
            <div class="voice-btn-container">
              <el-button
                  :class="['voice-btn', { 'recording': isRecording, 'voice-loading': voiceLoading }]"
                  @click="toggleRecording"
                  :disabled="voiceLoading"
                  :title="isRecording ? '点击停止录音' : '点击开始录音'"
              >
                <el-icon>
                  <Microphone />
                </el-icon>
              </el-button>
            </div>
            <!-- 实时转录显示区域 - 放在输入框内部 -->
            <div v-if="isRecording && realTimeTranscript" class="transcript-input-display">
              <div class="transcript-input-header">
                <el-icon class="transcript-icon"><Microphone /></el-icon>
                <span class="transcript-label">实时转录：</span>
              </div>
              <div class="transcript-input-content">
                {{ realTimeTranscript }}
              </div>
            </div>

            <el-input
                v-model="userMessage"
                type="textarea"
                :rows="2"
            placeholder="输入您的消息..."
            @keydown.enter.prevent="handleEnterKey"
            :disabled="isTyping"
            resize="none"
                class="message-input"
                :class="{ 'with-transcript': isRecording && realTimeTranscript }"
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
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElEmpty, ElAvatar, ElDropdown, ElDropdownMenu, ElDropdownItem, ElButton, ElInput, ElDialog, ElForm, ElFormItem, ElIcon } from 'element-plus'
import { Plus, Setting, Edit, Delete, UserFilled, Service, Upload, Microphone, Stop, Close } from '@element-plus/icons-vue'
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
const audioContext = ref(null)
const mediaStream = ref(null)
const processor = ref(null)
const recordingTimer = ref(null)
const chunkBuffer = ref(new Int16Array(0))
const realTimeTranscript = ref('')

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

// 开始录音（实时流式）
const startRecording = async () => {
  if (isRecording.value) return

  try {
    // 请求麦克风权限
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true
      }
    })

    mediaStream.value = stream

    // 尝试使用Web Audio API进行实时处理
    try {
      await startWebAudioRecording(stream)
    } catch (webAudioError) {
      console.warn('Web Audio API 失败，回退到 MediaRecorder API:', webAudioError)
      // 回退到MediaRecorder方案
      await startMediaRecorderRecording(stream)
    }

    isRecording.value = true
    ElMessage.success('开始录音')

  } catch (error) {
    console.error('录音启动失败:', error)
    if (error.name === 'NotAllowedError') {
      ElMessage.error('麦克风权限被拒绝，请在浏览器设置中允许麦克风访问')
    } else if (error.name === 'NotFoundError') {
      ElMessage.error('未找到麦克风设备')
    } else {
      ElMessage.error('无法访问麦克风，请检查权限设置')
    }
    cleanupRecording()
  }
}

// Web Audio API 录音方案
const startWebAudioRecording = async (stream) => {
  // 创建音频上下文（不强制设置采样率，使用默认值）
  audioContext.value = new (window.AudioContext || window.webkitAudioContext)()

  // 等待音频上下文启动
  if (audioContext.value.state === 'suspended') {
    await audioContext.value.resume()
  }

  const source = audioContext.value.createMediaStreamSource(stream)
  processor.value = audioContext.value.createScriptProcessor(4096, 1, 1)

  // 重置状态
  chunkBuffer.value = new Int16Array(0)
  realTimeTranscript.value = ''
  const actualSampleRate = audioContext.value.sampleRate

  // 音频处理回调
  processor.value.onaudioprocess = (event) => {
    const inputData = event.inputBuffer.getChannelData(0)

    // 转换为16位PCM格式
    const pcmData = new Int16Array(inputData.length)
    for (let i = 0; i < inputData.length; i++) {
      pcmData[i] = Math.max(-32768, Math.min(32767, inputData[i] * 32768))
    }

    // 累积音频数据
    const newBuffer = new Int16Array(chunkBuffer.value.length + pcmData.length)
    newBuffer.set(chunkBuffer.value)
    newBuffer.set(pcmData, chunkBuffer.value.length)
    chunkBuffer.value = newBuffer
  }

  source.connect(processor.value)
  processor.value.connect(audioContext.value.destination)

  // 启动定时器，每1秒发送一次音频分片
  recordingTimer.value = setInterval(() => {
    if (chunkBuffer.value.length > 0) {
      sendAudioChunk(chunkBuffer.value, actualSampleRate)
      chunkBuffer.value = new Int16Array(0)
    }
  }, 1000)
}

// MediaRecorder 录音方案（备用）
const startMediaRecorderRecording = async (stream) => {
  const recorder = new MediaRecorder(stream, {
    mimeType: MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : 'audio/mp4'
  })

  mediaRecorder.value = recorder
  audioChunks.value = []
  realTimeTranscript.value = ''

  // 每1秒收集一次数据
  recorder.ondataavailable = (event) => {
    if (event.data.size > 0) {
      audioChunks.value.push(event.data)
    }
  }

  // 启动定时器，每1秒发送一次音频分片
  recordingTimer.value = setInterval(async () => {
    if (audioChunks.value.length > 0) {
      await sendMediaRecorderChunk()
    }
  }, 1000)

  recorder.start(1000) // 每1秒触发一次dataavailable事件
}

// 停止录音
const stopRecording = async () => {
  if (!isRecording.value) return

  console.log('立即停止录音...')

  // 立即设置录音状态为false，确保UI立即更新
  isRecording.value = false

  // 发送最后的音频片段（Web Audio API方案）
  if (chunkBuffer.value.length > 0 && audioContext.value) {
    await sendAudioChunk(chunkBuffer.value, audioContext.value.sampleRate)
    chunkBuffer.value = new Int16Array(0)
  }

  // 发送最后的音频片段（MediaRecorder方案）
  if (audioChunks.value.length > 0) {
    await sendMediaRecorderChunk()
    audioChunks.value = []
  }

  // 立即处理转录文本
  if (realTimeTranscript.value && realTimeTranscript.value.trim()) {
    const transcriptText = realTimeTranscript.value.trim()
    realTimeTranscript.value = ''
    // 立即追加到输入框
    userMessage.value += (userMessage.value && !userMessage.value.endsWith(' ') ? ' ' : '') + transcriptText
    ElMessage.success(`语音识别完成: ${transcriptText}`)
  }

  // 立即清理录音资源
  cleanupRecording()

  console.log('录音已立即停止')
}

// 强制停止录音（紧急情况使用）
const forceStopRecording = () => {
  console.log('强制停止录音...')

  // 立即清理所有资源
  cleanupRecording()
  isRecording.value = false
  realTimeTranscript.value = ''

  ElMessage.warning('录音已强制停止')
}

// 清理录音资源
const cleanupRecording = () => {
  if (recordingTimer.value) {
    clearInterval(recordingTimer.value)
    recordingTimer.value = null
  }

  if (processor.value) {
    processor.value.disconnect()
    processor.value = null
  }

  if (audioContext.value) {
    audioContext.value.close()
    audioContext.value = null
  }

  if (mediaRecorder.value && mediaRecorder.value.state !== 'inactive') {
    mediaRecorder.value.stop()
    mediaRecorder.value = null
  }

  if (mediaStream.value) {
    mediaStream.value.getTracks().forEach(track => track.stop())
    mediaStream.value = null
  }

  // 重置状态
  chunkBuffer.value = new Int16Array(0)
  audioChunks.value = []
}

// 发送音频分片到后端（Web Audio API方案）
const sendAudioChunk = async (audioData, sampleRate) => {
  try {
    // 检查录音状态，如果已经停止则不发送
    if (!isRecording.value) return

    // 将Int16Array转换为WAV格式的Blob
    const wavBlob = createWavBlob(audioData, sampleRate)

    const formData = new FormData()
    formData.append('radioFile', wavBlob, 'chunk.wav')

    const response = await fetch('http://localhost:8080/user/uploadAudioFile', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: formData
    })

    if (response.ok) {
      const result = await response.json()
      if (result.code === 200 && result.data && result.data.trim()) {
        // 追加新的转录内容到现有的转录文本
        const newTranscript = result.data.trim()
        if (realTimeTranscript.value) {
          // 如果已有内容，添加空格后追加新内容
          realTimeTranscript.value += ' ' + newTranscript
        } else {
          // 如果没有内容，直接设置
          realTimeTranscript.value = newTranscript
        }
      } else {
        // 服务器返回空结果或无效数据，不更新转录文本
        console.warn('服务器返回空结果或无效数据:', result)
      }
    } else {
      console.warn('服务器响应错误:', response.status, response.statusText)
    }
  } catch (error) {
    console.error('音频分片发送失败:', error)
    // 不显示错误消息，避免频繁打扰用户
  }
}

// 发送音频分片到后端（MediaRecorder方案）
const sendMediaRecorderChunk = async () => {
  try {
    if (audioChunks.value.length === 0 || !isRecording.value) return

    // 合并所有音频块
    const combinedBlob = new Blob(audioChunks.value, {
      type: mediaRecorder.value.mimeType
    })

    const formData = new FormData()
    formData.append('radioFile', combinedBlob, 'chunk.webm')

    const response = await fetch('http://localhost:8080/user/uploadAudioFile', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: formData
    })

    if (response.ok) {
      const result = await response.json()
      if (result.code === 200 && result.data && result.data.trim()) {
        // 追加新的转录内容到现有的转录文本
        const newTranscript = result.data.trim()
        if (realTimeTranscript.value) {
          // 如果已有内容，添加空格后追加新内容
          realTimeTranscript.value += ' ' + newTranscript
        } else {
          // 如果没有内容，直接设置
          realTimeTranscript.value = newTranscript
        }
      } else {
        // 服务器返回空结果或无效数据，不更新转录文本
        console.warn('服务器返回空结果或无效数据:', result)
      }
    } else {
      console.warn('服务器响应错误:', response.status, response.statusText)
    }

    // 清空已发送的音频块
    audioChunks.value = []
  } catch (error) {
    console.error('MediaRecorder音频分片发送失败:', error)
    // 不显示错误消息，避免频繁打扰用户
  }
}

// 创建WAV格式的Blob
const createWavBlob = (audioData, sampleRate) => {
  const length = audioData.length
  const buffer = new ArrayBuffer(44 + length * 2)
  const view = new DataView(buffer)

  // WAV文件头
  const writeString = (offset, string) => {
    for (let i = 0; i < string.length; i++) {
      view.setUint8(offset + i, string.charCodeAt(i))
    }
  }

  writeString(0, 'RIFF')
  view.setUint32(4, 36 + length * 2, true)
  writeString(8, 'WAVE')
  writeString(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeString(36, 'data')
  view.setUint32(40, length * 2, true)

  // 写入PCM数据
  let offset = 44
  for (let i = 0; i < length; i++) {
    view.setInt16(offset, audioData[i], true)
    offset += 2
  }

  return new Blob([buffer], { type: 'audio/wav' })
}


// 挂载时加载
onMounted(() => {
  if (checkToken()) {
    loadSessions()
  }
})

// 组件卸载时清理资源
onUnmounted(() => {
  cleanupRecording()
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

/* 实时转录显示区域 */
.transcript-display {
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px 16px;
  margin: 0 0 12px 80px; /* 左边距留出录音按钮和发送按钮的空间 */
  max-width: calc(100% - 96px); /* 减去左边距 */
  position: relative;
  z-index: 0; /* 设置为最低层级，确保不会遮挡按钮 */
  animation: none; /* 移除动画 */
}

.transcript-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.transcript-icon {
  color: #3b82f6;
  font-size: 16px;
  margin-right: 8px;
}

.transcript-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
  flex: 1;
}

.transcript-hint {
  font-size: 12px;
  color: #94a3b8;
  font-style: italic;
  flex: 1;
}

.force-stop-btn {
  color: #ef4444;
  font-size: 14px;
  padding: 2px 6px;
  height: auto;
  border-radius: 4px;
  transition: all 0.2s ease;
  margin-left: 8px;
  z-index: 1004; /* 最高层级，确保强制停止按钮始终可点击 */
  position: relative; /* 确保层级生效 */
  background-color: rgba(239, 68, 68, 0.1); /* 添加背景增强可见性 */
}

.force-stop-btn:hover {
  background-color: #ef4444;
  color: white;
}

/* 强制停止按钮专用容器 */
.force-stop-container {
  position: relative;
  z-index: 1003; /* 确保强制停止按钮在最上层 */
  isolation: isolate; /* 创建独立的堆叠上下文 */
}

/* 全局停止按钮容器（录音时使用） */
.global-stop-container {
  position: relative;
  z-index: 100; /* 适当的层级，确保转录区域可见但不会过度遮挡 */
  isolation: isolate; /* 创建独立的堆叠上下文 */
}

.transcript-content {
  font-size: 14px;
  color: #374151;
  line-height: 1.5;
  min-height: 20px;
  padding: 8px 0;
  word-wrap: break-word;
  white-space: pre-wrap;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 输入区域 - 固定高度 */
.chat-input {
  background-color: #ffffff;
  border-top: 1px solid #e5e7eb;
  padding: 16px 24px;
  min-height: 88px; /* 最小高度，会根据转录区域扩展 */
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
  isolation: isolate; /* 创建新的堆叠上下文 */
  overflow: visible; /* 确保按钮不被裁剪 */
  z-index: 10; /* 为容器设置基础层级 */
}

/* 录音按钮专用容器 */
.voice-btn-container {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 9999; /* 设置最高层级 */
  pointer-events: none;
}

/* 录音按钮样式 */
.voice-btn {
  position: relative;
  left: 12px;
  width: 48px !important;
  height: 48px !important;
  border-radius: 50%;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  padding: 0 !important;
  background-color: #f3f4f6;
  border: 1px solid #e5e7eb;
  color: #6b7280;
  z-index: 9999;
  user-select: none;
  cursor: pointer;
  outline: none;
  pointer-events: auto;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.voice-btn:focus {
  outline: none;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
}

/* 确保图标正确显示 */
.voice-btn .el-icon {
  font-size: 18px;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.voice-btn:hover {
  background-color: #e5e7eb;
  color: #374151;
  border-color: #d1d5db;
}

.voice-btn.recording {
  background-color: #ef4444 !important;
  border-color: #dc2626 !important;
  color: white !important;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.4) !important;
}

.voice-btn.voice-loading {
  background-color: #fbbf24;
  border-color: #f59e0b;
  color: white;
  display: flex !important; /* 强制显示 */
  visibility: visible !important;
  opacity: 1 !important;
}

.voice-btn:disabled {
  background-color: #f9fafb;
  color: #9ca3af;
  border-color: #e5e7eb;
  cursor: not-allowed;
  display: flex !important; /* 强制显示 */
  visibility: visible !important;
  opacity: 1 !important;
}

/* 状态文字样式已移除，现在只使用图标 */

/* 脉冲动画已移除，避免层级问题 */

/* 输入框内部的转录显示区域 */
.transcript-input-display {
  position: absolute;
  top: 8px;
  left: 72px;
  right: 72px;
  background-color: #f0f9ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  padding: 8px 12px;
  z-index: 5;
  font-size: 13px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.transcript-input-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.transcript-input-content {
  color: #374151;
  line-height: 1.4;
  word-wrap: break-word;
  white-space: pre-wrap;
}

/* 强制停止按钮样式已移除，现在使用主录音按钮 */

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
  resize: none;
  flex-grow: 1; /* 占满容器宽度 */
  position: relative; /* 确保输入框不会影响按钮定位 */
  z-index: 0; /* 降低输入框层级 */
}

/* 有转录时调整输入框样式 */
.message-input.with-transcript {
  padding-top: 80px; /* 为转录区域留出空间 */
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
  z-index: 50; /* 提高发送按钮的层级 */
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
    min-height: 80px; /* 移动端输入区高度 */
    padding: 12px 16px;
  }

  .transcript-display {
    padding: 10px 12px;
    margin: 0 0 10px 70px; /* 移动端左边距稍微小一些 */
    max-width: calc(100% - 84px);
  }

  .transcript-content {
    font-size: 13px;
  }

  .transcript-hint {
    font-size: 11px;
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
    height: 44px;
    left: 10px;
    z-index: 10; /* 确保移动端按钮层级正确 */
    min-width: 44px; /* 移动端最小宽度 */
    padding: 0 8px !important; /* 移动端内边距稍小 */
  }

  .voice-btn.recording {
    padding: 0 12px !important; /* 移动端录音状态内边距 */
  }

  .voice-btn-text {
    font-size: 13px; /* 移动端文字稍小 */
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