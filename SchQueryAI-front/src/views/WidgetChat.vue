<template>
  <div class="widget-shell" :style="themeStyle">
    <div class="widget-header">
      <div class="widget-title">{{ widgetConfig.title }}</div>
      <div class="widget-subtitle">{{ widgetConfig.subtitle }}</div>
    </div>

    <div class="widget-messages" ref="messagesRef">
      <div v-if="messages.length === 0" class="widget-empty">输入内容开始对话</div>
      <div v-for="message in messages" :key="message.id" class="widget-message" :class="message.role">
        <div class="bubble">{{ message.content }}</div>
      </div>
    </div>

    <div class="widget-input">
      <el-input
        v-model="inputValue"
        type="textarea"
        :rows="2"
        placeholder="输入您的消息..."
        resize="none"
        class="widget-textarea"
        @keydown.enter.prevent="handleEnter"
      />
      <el-button
        type="primary"
        class="widget-send"
        :disabled="sending || !inputValue.trim()"
        @click="sendMessage"
      >
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'

const widgetConfig = ref({
  title: 'SchQueryAI',
  subtitle: 'AI Assistant',
  primaryColor: '#1d4ed8',
  backgroundColor: '#ffffff',
  textColor: '#0f172a'
})

const widgetToken = ref('')
const sessionId = ref('')
const messages = ref([])
const inputValue = ref('')
const sending = ref(false)
const messagesRef = ref(null)

const themeStyle = computed(() => ({
  '--widget-primary': widgetConfig.value.primaryColor,
  '--widget-bg': widgetConfig.value.backgroundColor,
  '--widget-text': widgetConfig.value.textColor
}))

const scrollToBottom = () => {
  nextTick(() => {
    const el = messagesRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

const getSiteKey = () => {
  const params = new URLSearchParams(window.location.search)
  return params.get('siteKey') || 'demo-site-key'
}

const apiBaseUrl = 'http://localhost:8080'

const loadConfig = async (siteKey) => {
  try {
    const response = await fetch(`${apiBaseUrl}/widget/config?siteKey=${encodeURIComponent(siteKey)}`)
    const result = await response.json()
    if (result?.code === 200 && result.data) {
      widgetConfig.value = { ...widgetConfig.value, ...result.data }
    }
  } catch (error) {
    console.warn('Widget config加载失败:', error)
  }
}

const authWidget = async (siteKey) => {
  const response = await fetch(`${apiBaseUrl}/widget/auth`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ siteKey })
  })
  const result = await response.json()
  if (result?.code !== 200 || !result.data?.token) {
    throw new Error(result?.msg || '站点鉴权失败')
  }
  widgetToken.value = result.data.token
}

const createSession = async () => {
  const response = await fetch(`${apiBaseUrl}/user/session/add`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${widgetToken.value}` }
  })
  const result = await response.json()
  if (result?.code !== 200 || !result.data) {
    throw new Error(result?.msg || '创建会话失败')
  }
  sessionId.value = result.data
}

const sendMessage = async () => {
  const content = inputValue.value.trim()
  if (!content || sending.value) return

  const userMessage = {
    id: `${Date.now()}-user`,
    role: 'user',
    content
  }
  messages.value.push(userMessage)
  inputValue.value = ''
  scrollToBottom()

  const aiMessage = {
    id: `${Date.now()}-ai`,
    role: 'ai',
    content: ''
  }
  messages.value.push(aiMessage)
  sending.value = true

  try {
    const response = await fetch(`${apiBaseUrl}/user/message/send?sessionId=${encodeURIComponent(sessionId.value)}&content=${encodeURIComponent(content)}`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${widgetToken.value}`,
        'Content-Type': 'text/event-stream'
      }
    })

    if (!response.ok || !response.body) {
      throw new Error('发送失败')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    let isReading = true
    while (isReading) {
      const { done, value } = await reader.read()
      if (done) {
        isReading = false
        break
      }

      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replace(/\r+/g, '')
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''

      for (const evt of events) {
        const lines = evt.split('\n')
        const dataLines = lines
          .filter((line) => line.startsWith('data:'))
          .map((line) => line.slice(5).replace(/^ /, ''))
        const eventData = dataLines.length ? dataLines.join('\n') : evt
        if (!eventData || eventData.trim() === '[DONE]') continue
        aiMessage.content += eventData
      }

      scrollToBottom()
    }

    if (buffer && buffer.trim() && buffer.trim() !== '[DONE]') {
      aiMessage.content += buffer.replace(/^data:\s*/gm, '')
    }
  } catch (error) {
    console.error('Widget发送消息失败:', error)
    ElMessage.error(error.message || '发送失败')
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

const handleEnter = (event) => {
  if (event.shiftKey) {
    return
  }
  event.preventDefault()
  sendMessage()
}

onMounted(async () => {
  const siteKey = getSiteKey()
  await loadConfig(siteKey)
  try {
    await authWidget(siteKey)
    await createSession()
  } catch (error) {
    ElMessage.error(error.message || 'Widget初始化失败')
  }
})
</script>

<style scoped>
.widget-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--widget-bg);
  color: var(--widget-text);
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.16);
  overflow: hidden;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.widget-header {
  padding: 16px 20px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--widget-primary) 18%, #ffffff) 0%, #ffffff 100%);
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.widget-title {
  font-size: 16px;
  font-weight: 600;
}

.widget-subtitle {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.55);
  margin-top: 4px;
}

.widget-messages {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: color-mix(in srgb, var(--widget-primary) 5%, #ffffff);
}

.widget-empty {
  color: rgba(15, 23, 42, 0.45);
  font-size: 13px;
  text-align: center;
  margin-top: 16px;
}

.widget-message {
  display: flex;
}

.widget-message.user {
  justify-content: flex-end;
}

.widget-message.ai {
  justify-content: flex-start;
}

.bubble {
  max-width: 80%;
  padding: 10px 12px;
  border-radius: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  background: #ffffff;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.widget-message.user .bubble {
  background: var(--widget-primary);
  color: #fff;
  border: none;
}

.widget-input {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
  align-items: center;
  background: #fff;
}

.widget-textarea {
  flex: 1;
}

:deep(.widget-textarea .el-textarea__inner) {
  border-radius: 10px;
  border: 1px solid rgba(15, 23, 42, 0.15);
  padding: 8px 12px;
  min-height: 42px;
  resize: none;
}

.widget-send {
  background: var(--widget-primary);
  border: none;
  box-shadow: 0 6px 12px rgba(29, 78, 216, 0.2);
}

.widget-send:disabled {
  opacity: 0.6;
}
</style>
