<template>
  <div class="chat-container">
    <el-container>
      <!-- 会话列表侧边栏 -->
      <el-aside width="300px" class="session-sidebar" :class="{ show: mobileSidebarVisible }">
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

      <div
        v-if="isMobile && mobileSidebarVisible"
        class="mobile-sidebar-mask"
        @click="mobileSidebarVisible = false"
      />

      <!-- 聊天主区域 -->
      <el-container class="chat-main">
        <el-header class="chat-header">
          <div class="page-header chat-page-header">
            <div class="header-left">
              <el-button
                class="back-btn mobile-only"
                circle
                :title="mobileSidebarVisible ? '关闭会话列表' : '打开会话列表'"
                @click="toggleMobileSidebar"
              >
                <el-icon class="back-icon"><Menu /></el-icon>
              </el-button>
              <div class="title">
                <h2>{{ currentSession ? (currentSession.sessionName || '未命名会话') : 'SchQueryAI 智能聊天' }}</h2>
                <p class="sub">{{ currentSession ? '左侧切换/管理会话，底部输入框发送消息' : '选择左侧会话开始聊天' }}</p>
              </div>
              <el-button
                v-if="currentSession"
                text
                size="small"
                class="rename-btn"
                title="重命名会话"
                @click="showRenameDialog = true"
              >
                <el-icon><Edit /></el-icon>
              </el-button>
            </div>

            <div class="header-actions">
              <WeatherBadge />
              <!-- 黑夜模式切换按钮 -->
              <el-button
                @click="toggleDarkMode"
                circle
                size="small"
                :title="isDarkMode ? '切换到日间模式' : '切换到夜间模式'"
                class="dark-mode-toggle"
              >
                <el-icon>
                  <Sunny v-if="isDarkMode" />
                  <Moon v-else />
                </el-icon>
              </el-button>

              <el-dropdown @command="handleUserCommand" trigger="click">
                <span class="user-dropdown">
                  <el-avatar :size="32" :src="userStore.getDisplayAvatar()" />
                  <span class="username">{{ userStore.userInfo.userName || '用户' }}</span>
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <!-- 员工对话管理功能 -->
                    <el-dropdown-item v-if="isWorker()" command="conversation-management">
                      <el-icon><ChatDotRound /></el-icon>
                      对话管理
                    </el-dropdown-item>

                    <!-- 管理员系统管理功能 -->
                    <el-dropdown-item v-if="isAdmin()" command="system-management">
                      <el-icon><Tools /></el-icon>
                      系统管理
                    </el-dropdown-item>

                    <el-dropdown-item command="profile">
                      <el-icon><User /></el-icon>
                      个人信息
                    </el-dropdown-item>
                    <el-dropdown-item command="password">
                      <el-icon><Lock /></el-icon>
                      修改密码
                    </el-dropdown-item>
                    <el-dropdown-item v-if="userStore.userInfo.role !== 'admin'" command="contact-service" divided>
                      <el-icon><Service /></el-icon>
                      在线客服
                    </el-dropdown-item>
                    <el-dropdown-item divided command="logout">
                      <el-icon><SwitchButton /></el-icon>
                      退出登录
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
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
                :key="`msg-${message.id}-v${message.renderVersion || 0}`"
                class="message-item"
                :class="{ 'user-message': message.messageType === 0, 'ai-message': message.messageType === 1 }"
            >
              <div class="message-avatar">
                <el-avatar
                    :size="36"
                    :src="message.messageType === 0 ? userStore.getDisplayAvatar() : getAIAvatar()"
                    :icon="null"
                    :class="{ 'user-avatar': message.messageType === 0, 'ai-avatar': message.messageType === 1 }"
                />
              </div>
              <div class="message-content">
                <div class="message-text" v-if="message.messageType === 0">{{ message.content }}</div>
                <div class="message-text" v-else-if="message.content">
                  <!-- 流式传输时显示预处理文本，完成后显示Markdown格式 -->
                  <div v-if="message.isStreaming" class="streaming-text" v-text="message.content"></div>
                  <div v-else class="markdown-content"
                       :key="`md-${message.id}-${message.renderVersion || 0}`"
                       v-html="renderMarkdown(message.content, message.id)"></div>
                </div>
                <div class="message-time">{{ formatTime(message.createdAt) }}</div>
              </div>
            </div>

            <div v-if="isTyping" class="message-item ai-message">
              <div class="message-avatar">
                <el-avatar :size="36" :src="getAIAvatar()" class="ai-avatar" />
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
            <!-- 实时转录显示区域 -->
            <div v-if="isRecording && realTimeTranscript" class="transcript-input-display">
              <div class="transcript-input-header">
                <el-icon class="transcript-icon"><Microphone /></el-icon>
                <span class="transcript-label">实时转录：</span>
              </div>
              <div class="transcript-input-content" ref="transcriptScrollRef">
                {{ realTimeTranscript }}
              </div>
            </div>

            <div class="input-row">
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

              <el-input
                  v-model="userMessage"
                  type="textarea"
                  :rows="2"
                  placeholder="输入您的消息..."
                  @keydown.enter.prevent="handleEnterKey"
                  :readonly="isRecording"
                  :disabled="isTyping"
                  resize="none"
                  class="message-input"
                  ref="messageInputRef"
              />
              <el-button
                  type="primary"
                  @click="sendMessage"
                  :disabled="!userMessage.trim() || isTyping"
                  class="send-btn"
              >
                <el-icon v-if="isTyping" class="send-icon is-loading"><Loading /></el-icon>
                <el-icon v-else class="send-icon"><Upload /></el-icon>
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
import { ref, onMounted, onUnmounted, nextTick, watch, getCurrentInstance } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElEmpty, ElAvatar, ElDropdown, ElDropdownMenu, ElDropdownItem, ElButton, ElInput, ElDialog, ElForm, ElFormItem, ElIcon } from 'element-plus'
import { Plus, Setting, Edit, Delete, Service, Upload, Loading, Microphone, SwitchButton, User, Lock, ArrowDown, Moon, Sunny, ChatDotRound, Tools, Menu } from '@element-plus/icons-vue'
import { chatApi } from '@/api/chat'
import WeatherBadge from '../components/WeatherBadge.vue'
import { useUserStore } from '@/stores/userStore'
import { getAIAvatar } from '@/utils/avatarUtils'
import { Marked } from 'marked'
import {
  isAdmin,
  isWorker
} from '@/utils/auth'
import { applyTheme, isDarkTheme } from '@/utils/theme'
import hljs from 'highlight.js'

// 路由实例和用户store
const router = useRouter()
const userStore = useUserStore()

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
const messageInputRef = ref(null)
const transcriptScrollRef = ref(null)

// 录音相关状态
const isRecording = ref(false)
const voiceLoading = ref(false)
const mediaRecorder = ref(null)
const audioChunks = ref([])
const audioContext = ref(null)

// 用户头像 - 从store获取
const mediaStream = ref(null)
const processor = ref(null)
const recordingTimer = ref(null)
const chunkBuffer = ref(new Int16Array(0))
const realTimeTranscript = ref('')
const streamingSessionToken = ref('') // 流式识别会话令牌
let renderVersion = 0 // 渲染版本号，用于强制重新渲染

const transcriptBaseMessage = ref('')
let transcriptUpdateTimer = null
let transcriptPending = ''

const buildMergedMessage = (baseText, transcriptText) => {
  const base = (baseText || '').trimEnd()
  const transcript = (transcriptText || '').trim()
  if (!base) return transcript
  if (!transcript) return base
  return `${base}${base.endsWith(' ') ? '' : ' '}${transcript}`.replace(/\s{2,}/g, ' ')
}

const scrollTranscriptToBottom = () => {
  nextTick(() => {
    const el = transcriptScrollRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

const syncInputCaretToEnd = () => {
  nextTick(() => {
    const inputEl = messageInputRef.value?.$el?.querySelector('textarea')
    if (!inputEl) return
    const length = inputEl.value.length
    inputEl.setSelectionRange(length, length)
    inputEl.scrollTop = inputEl.scrollHeight
  })
}

const syncTranscriptToInput = (text) => {
  if (!isRecording.value) return
  const merged = buildMergedMessage(transcriptBaseMessage.value, text)
  if (merged === userMessage.value) return
  userMessage.value = merged
  syncInputCaretToEnd()
}

const applyFinalTranscript = (text) => {
  const merged = buildMergedMessage(transcriptBaseMessage.value, text)
  if (!merged) return
  userMessage.value = merged
  syncInputCaretToEnd()
}

const scheduleTranscriptUpdate = (text) => {
  const next = (text || '').trim()
  if (!isRecording.value) return
  if (!next || next === realTimeTranscript.value) return
  transcriptPending = next
  if (transcriptUpdateTimer) return
  transcriptUpdateTimer = setTimeout(() => {
    realTimeTranscript.value = transcriptPending
    syncTranscriptToInput(transcriptPending)
    scrollTranscriptToBottom()
    transcriptUpdateTimer = null
  }, 120)
}

const flushTranscriptUpdate = () => {
  if (transcriptUpdateTimer) {
    clearTimeout(transcriptUpdateTimer)
    transcriptUpdateTimer = null
  }
  if (transcriptPending) {
    realTimeTranscript.value = transcriptPending
    syncTranscriptToInput(transcriptPending)
    scrollTranscriptToBottom()
    transcriptPending = ''
  }
}

const resetTranscriptState = (preserveBase = false) => {
  if (transcriptUpdateTimer) {
    clearTimeout(transcriptUpdateTimer)
    transcriptUpdateTimer = null
  }
  transcriptPending = ''
  realTimeTranscript.value = ''
  if (!preserveBase) {
    transcriptBaseMessage.value = ''
  }
}

const isMobile = ref(false)
const mobileSidebarVisible = ref(false)

const updateIsMobile = () => {
  isMobile.value = window.innerWidth <= 768
  if (!isMobile.value) {
    mobileSidebarVisible.value = false
  }
}

const toggleMobileSidebar = () => {
  if (!isMobile.value) return
  mobileSidebarVisible.value = !mobileSidebarVisible.value
}

// 黑夜模式状态
const isDarkMode = ref(isDarkTheme())

// 切换黑夜模式
const toggleDarkMode = () => {
  isDarkMode.value = !isDarkMode.value
  applyTheme(isDarkMode.value ? 'dark' : 'light')
}

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

// 统一API错误处理函数
const handleApiError = (error, defaultMessage = '请求失败') => {
  console.error('API Error:', error)

  // 检查是否是401错误（令牌过期或无效）
  const isTokenExpired =
    error.code === 401 ||
    error.response?.status === 401 ||
    error.message?.includes('TOKEN_EXPIRED') ||
    error.message?.includes('JWT expired') ||
    (error.msg && error.msg.includes('TOKEN_EXPIRED')) ||
    (error.data?.msg && error.data.msg.includes('TOKEN_EXPIRED'))

  if (isTokenExpired) {
    // 清除本地令牌
    localStorage.removeItem('token')

    // 清除用户信息
    userStore.clearUserInfo()

    // 显示提示信息
    ElMessage.error('登录已过期，请重新登录')

    // 重定向到登录页面
    router.push('/login')
    return
  }

  // 对于其他错误，显示默认错误信息
  if (defaultMessage) {
    ElMessage.error(defaultMessage)
  }
}

// 清理AI响应内容中的多余字符
const cleanAIResponse = (text) => {
  if (!text) return ''

  // 移除所有的data:前缀，包括全局匹配
  let cleaned = text.replace(/^data:\s*/gm, '')

  // 基本格式清理
  cleaned = cleaned
      .replace(/\n{3,}/g, '\n\n') // 最多保留2个连续换行
      .replace(/[ \t]+$/gm, '') // 移除行尾空白
      .replace(/\r+/g, '') // 移除回车符
      .trim() // 去除首尾空白

  return cleaned
}

// 使用 highlight.js 进行语法高亮
const highlightCode = (code, lang) => {
  if (!code) return ''

  try {
    // 检测语言类型
    const detectedLang = detectLanguage(code, lang)
    const cleanLang = detectedLang.toLowerCase().replace(/[^a-z0-9]/g, '')

    // 使用 highlight.js 进行高亮
    if (cleanLang && cleanLang !== 'text' && hljs.getLanguage(cleanLang)) {
      return hljs.highlight(code, { language: cleanLang, ignoreIllegals: true }).value
    } else {
      // 自动检测语言
      const result = hljs.highlightAuto(code)
      return result.value
    }
  } catch (error) {
    console.error('Syntax highlighting error:', error)
    return escapeHtml(code)
  }
}

// 检测语言类型
const detectLanguage = (code, lang) => {
  if (lang && lang !== 'text') return lang

  // SQL检测
  if (isSQLCode(code)) return 'sql'

  // JavaScript检测
  if (/function\s+\w+|const\s+\w+\s*=|let\s+\w+\s*=|=>|import\s+/.test(code)) return 'javascript'

  // Python检测
  if (/def\s+\w+|import\s+\w+|from\s+\w+|print\s*\(/.test(code)) return 'python'

  // Java检测
  if (/public\s+class|private\s+class|System\.out\.println|import\s+java\./.test(code)) return 'java'

  // CSS检测
  if (/{[^}]*}|#[a-zA-Z-]+\s*\{|\.color|background-color|font-size/.test(code)) return 'css'

  return 'text'
}

// 检测是否为SQL代码
const isSQLCode = (code) => {
  const sqlKeywords = ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE', 'ALTER', 'DROP', 'FROM', 'WHERE', 'GROUP BY', 'ORDER BY', 'HAVING', 'JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'INNER JOIN']
  const upperCode = code.toUpperCase()
  return sqlKeywords.some(keyword => upperCode.includes(keyword))
}

let markdownParser = null

const sanitizeLinkHref = (href) => {
  if (!href) return '#'
  try {
    const url = new URL(href, window.location.origin)
    const protocol = (url.protocol || '').toLowerCase()
    if (protocol === 'http:' || protocol === 'https:' || protocol === 'mailto:' || protocol === 'tel:') {
      return href
    }
    return '#'
  } catch (e) {
    return href.startsWith('#') ? href : '#'
  }
}

const sanitizeImageSrc = (src) => {
  const safe = sanitizeLinkHref(src)
  return safe === '#' ? '' : safe
}

const getMarkdownParser = () => {
  if (markdownParser) return markdownParser

  markdownParser = new Marked({
    gfm: true,
    breaks: true,
    renderer: {
      code: (token) => {
        const code = token?.text ?? ''
        const langHint = token?.lang ?? undefined
        const detectedLang = detectLanguage(code, langHint)
        const cleanLang = (detectedLang || 'text').toLowerCase().replace(/[^a-z0-9]/g, '')
        const displayLang = escapeHtml(detectedLang || 'text')
        const copyPayload = encodeURIComponent(code)

        let highlightedCode = ''
        try {
          highlightedCode = highlightCode(code, langHint)
        } catch (e) {
          highlightedCode = escapeHtml(code)
        }

        return `<div class="code-block-wrapper" data-language="${cleanLang}">
          <div class="code-block-header">
            <div class="code-language">${displayLang}</div>
            <div class="code-actions">
              <button type="button" class="copy-btn" data-copy="${copyPayload}" onclick="copyCode(this)" aria-label="复制代码">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                  <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"></path>
                </svg>
              </button>
            </div>
          </div>
          <pre class="code-block ${cleanLang ? `language-${cleanLang}` : ''}"><code class="code-content hljs ${cleanLang ? `language-${cleanLang}` : ''}">${highlightedCode}</code></pre>
        </div>`
      },
      link: (token) => {
        const href = sanitizeLinkHref(token?.href)
        const safeHref = escapeHtml(href)
        const text = escapeHtml(token?.text ?? '')
        const title = token?.title ? ` title="${escapeHtml(token.title)}"` : ''
        return `<a href="${safeHref}"${title} target="_blank" rel="noopener noreferrer nofollow">${text}</a>`
      },
      image: (token) => {
        const src = sanitizeImageSrc(token?.href)
        const alt = escapeHtml(token?.text ?? '')
        const title = token?.title ? ` title="${escapeHtml(token.title)}"` : ''
        if (!src) return alt ? `<span class="md-image-alt">${alt}</span>` : ''
        return `<img src="${escapeHtml(src)}" alt="${alt}"${title} loading="lazy" decoding="async" />`
      },
      html: (token) => escapeHtml(token?.text ?? ''),
      table: function(token) {
        const headerCells = (token?.header || []).map((cell) => {
          const align = cell?.align || 'left'
          const html = this.parser.parseInline(cell?.tokens || [])
          return `<th style="text-align:${align};">${html}</th>`
        }).join('')

        const bodyRows = (token?.rows || []).map((row) => {
          const cells = (row || []).map((cell) => {
            const align = cell?.align || 'left'
            const html = this.parser.parseInline(cell?.tokens || [])
            return `<td style="text-align:${align};">${html}</td>`
          }).join('')
          return `<tr>${cells}</tr>`
        }).join('')

        const raw = token?.raw || ''
        const copyPayload = encodeURIComponent(raw)

        return `<div class="md-table-card">
          <div class="md-table-card-header">
            <div class="md-table-card-title">表格</div>
            <button type="button" class="md-table-copy-btn" data-copy="${copyPayload}" onclick="copyTable(this)" aria-label="复制表格">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"></path>
              </svg>
            </button>
          </div>
          <div class="md-table-card-body">
            <div class="md-table-scroll">
              <table>
                <thead><tr>${headerCells}</tr></thead>
                <tbody>${bodyRows}</tbody>
              </table>
            </div>
          </div>
        </div>`
      },
      list: function(token) {
        const ordered = !!token?.ordered
        const start = Number(token?.start || 0)
        const tag = ordered ? 'ol' : 'ul'

        const items = Array.isArray(token?.items) ? token.items : []
        const body = items.map((item) => this.listitem(item)).join('')

        const startAttr = ordered && start && start !== 1 ? ` start="${start}"` : ''
        const isTaskList = items.some((item) => !!item?.task)
        const classAttr = isTaskList ? ' class="md-task-list"' : ''

        return `<${tag}${classAttr}${startAttr}>\n${body}</${tag}>\n`
      },
      listitem: function(token) {
        const contentHtml = String(this.parser.parse(token?.tokens || [], !!token?.loose)).trimEnd()

        if (token?.task) {
          const checked = !!token?.checked
          const checkedAttr = checked ? ' checked=""' : ''
          const checkbox = `<input class="md-task-checkbox"${checkedAttr} disabled="" type="checkbox">`
          return `<li class="md-task-item${checked ? ' is-checked' : ''}">${checkbox}<div class="md-task-content">${contentHtml}</div></li>\n`
        }

        return `<li>${contentHtml}</li>\n`
      }
    }
  })

  return markdownParser
}

const encodeDomIdPart = (value) => {
  if (value === null || value === undefined) return '0'
  return encodeURIComponent(String(value)).replace(/%/g, '_')
}

const extractFootnoteDefinitions = (markdown) => {
  const footnotes = new Map()
  if (!markdown) return { markdown: '', footnotes }

  const lines = String(markdown).split('\n')
  const remaining = []
  let inFence = false
  let i = 0

  while (i < lines.length) {
    const line = lines[i]
    const trimmed = line.trim()

    if (trimmed.startsWith('```')) {
      inFence = !inFence
      remaining.push(line)
      i += 1
      continue
    }

    if (!inFence) {
      const match = line.match(/^\[\^([^\]]+)\]:\s*(.*)$/)
      if (match) {
        const id = (match[1] || '').trim()
        if (!id) {
          i += 1
          continue
        }

        const contentLines = []
        const firstLine = (match[2] || '').trimEnd()
        if (firstLine) contentLines.push(firstLine)

        i += 1
        while (i < lines.length) {
          const next = lines[i]
          const nextTrimmed = next.trim()

          if (nextTrimmed.startsWith('```')) break
          if (nextTrimmed === '') {
            contentLines.push('')
            i += 1
            continue
          }

          const continuation = next.match(/^(\t| {2,4})(.*)$/)
          if (!continuation) break
          contentLines.push(continuation[2] || '')
          i += 1
        }

        const content = contentLines.join('\n').trim()
        const prev = footnotes.get(id)
        footnotes.set(id, prev ? `${prev}\n\n${content}` : content)
        continue
      }
    }

    remaining.push(line)
    i += 1
  }

  return { markdown: remaining.join('\n'), footnotes }
}

const prepareFootnotes = (markdown, messageId) => {
  const { markdown: withoutDefs, footnotes } = extractFootnoteDefinitions(markdown)
  if (!footnotes || footnotes.size === 0) {
    return { markdown: withoutDefs, footnoteData: null }
  }

  const namespace = encodeDomIdPart(messageId)
  const refs = []
  const order = []
  const idToNumber = new Map()
  const idToRefCount = new Map()
  const idToFirstRefDomId = new Map()
  const idToAnchor = new Map()

  const ensureNumber = (id) => {
    if (idToNumber.has(id)) return idToNumber.get(id)
    const number = order.length + 1
    idToNumber.set(id, number)
    order.push(id)
    return number
  }

  const ensureAnchor = (id) => {
    if (idToAnchor.has(id)) return idToAnchor.get(id)
    const anchor = encodeDomIdPart(id)
    idToAnchor.set(id, anchor)
    return anchor
  }

  const replaceRefsInText = (text) => {
    const inlineCodeRe = /`[^`]*`/g
    let result = ''
    let lastIndex = 0
    let match

    while ((match = inlineCodeRe.exec(text)) !== null) {
      const before = text.slice(lastIndex, match.index)
      result += before.replace(/\[\^([^\]]+)\]/g, (m, rawId) => {
        const id = (rawId || '').trim()
        if (!id) return m

        const number = ensureNumber(id)
        const anchor = ensureAnchor(id)
        const count = (idToRefCount.get(id) || 0) + 1
        idToRefCount.set(id, count)

        const placeholder = `@@FNREF${refs.length}@@`
        const fnDomId = `fn-${namespace}-${anchor}`
        const refDomId = `fnref-${namespace}-${anchor}-${count}`
        if (!idToFirstRefDomId.has(id)) idToFirstRefDomId.set(id, refDomId)

        refs.push({ placeholder, number, fnDomId, refDomId })
        return placeholder
      })

      result += match[0]
      lastIndex = match.index + match[0].length
    }

    const tail = text.slice(lastIndex)
    result += tail.replace(/\[\^([^\]]+)\]/g, (m, rawId) => {
      const id = (rawId || '').trim()
      if (!id) return m

      const number = ensureNumber(id)
      const anchor = ensureAnchor(id)
      const count = (idToRefCount.get(id) || 0) + 1
      idToRefCount.set(id, count)

      const placeholder = `@@FNREF${refs.length}@@`
      const fnDomId = `fn-${namespace}-${anchor}`
      const refDomId = `fnref-${namespace}-${anchor}-${count}`
      if (!idToFirstRefDomId.has(id)) idToFirstRefDomId.set(id, refDomId)

      refs.push({ placeholder, number, fnDomId, refDomId })
      return placeholder
    })

    return result
  }

  const lines = String(withoutDefs).split('\n')
  const outLines = []
  let inFence = false
  for (const line of lines) {
    const trimmed = line.trim()
    if (trimmed.startsWith('```')) {
      inFence = !inFence
      outLines.push(line)
      continue
    }
    outLines.push(inFence ? line : replaceRefsInText(line))
  }

  // 未被引用的脚注也展示出来（按定义顺序追加编号）
  for (const id of footnotes.keys()) {
    ensureNumber(id)
  }

  const items = order.map((id) => {
    const number = idToNumber.get(id)
    const anchor = ensureAnchor(id)
    const fnDomId = `fn-${namespace}-${anchor}`
    const firstRefDomId = idToFirstRefDomId.get(id) || null
    const content = footnotes.get(id) || ''
    return { id, number, fnDomId, firstRefDomId, content }
  })

  return {
    markdown: outLines.join('\n'),
    footnoteData: { refs, items }
  }
}

// 改进的Markdown渲染方法
const renderMarkdown = (text, messageId) => {
  if (!text) return ''

  try {
    let processedText = preprocessMarkdown(text)
    if (!processedText) return ''

    const parser = getMarkdownParser()

    const { markdown, footnoteData } = prepareFootnotes(processedText, messageId)
    let html = parser.parse(markdown)

    if (footnoteData && footnoteData.refs && footnoteData.refs.length > 0) {
      html = html.replace(/@@FNREF(\d+)@@/g, (m, idx) => {
        const ref = footnoteData.refs[Number(idx)]
        if (!ref) return ''
        return `<sup class="footnote-ref"><a href="#${ref.fnDomId}" id="${ref.refDomId}">${ref.number}</a></sup>`
      })
    }

    if (footnoteData && footnoteData.items && footnoteData.items.length > 0) {
      const footnotesHtml = footnoteData.items.map((item) => {
        const contentHtml = item.content ? parser.parse(item.content) : '<p>（无脚注内容）</p>'
        const backRef = item.firstRefDomId
          ? `<a class="footnote-backref" href="#${item.firstRefDomId}" aria-label="返回引用">↩</a>`
          : ''
        return `<li id="${item.fnDomId}"><div class="footnote-content">${contentHtml}</div>${backRef}</li>`
      }).join('')

      html += `<section class="footnotes"><hr><ol>${footnotesHtml}</ol></section>`
    }

    return html
  } catch (error) {
    console.error('Markdown rendering error:', error)
    return `<div class="plain-text">${escapeHtml(text)}</div>`
  }
}

// 预处理Markdown文本，修复流式传输问题
const preprocessMarkdown = (text) => {
  if (!text) return ''

  // 只做必要的清理，不破坏正常的 Markdown 语法
  let processed = text
    .replace(/\r+/g, '')
    // 修复多余的连续换行（保留最多2个）
    .replace(/\n{3,}/g, '\n\n')
    // 移除行尾多余空格
    .replace(/[ \t]+$/gm, '')
    // 修复代码块的不完整结束标记
    .replace(/([^\n])\n*```$/gm, '$1\n```')

  // 若出现未闭合的 fenced code block，自动补齐结束符，避免后续内容全部被当作代码
  const fences = processed.match(/```/g)
  if (fences && fences.length % 2 === 1) {
    processed = processed + '\n```'
  }

  return processed.trim()
}

// HTML转义函数
const escapeHtml = (text) => {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

// 添加一个观察器来监视消息变化
watch(messages, () => {
  // 深度监听消息变化，确保渲染更新
  nextTick(() => {
    // Vue 响应式更新触发
  })
}, { deep: true, immediate: false })

// 加载会话列表
const loadSessions = async () => {
  try {
    sessionsLoading.value = true
    const response = await chatApi.getSessions()
    if (response?.code === 200) {
      sessions.value = response.data || []
    }
    if (isMobile.value && !currentSessionId.value) {
      mobileSidebarVisible.value = true
    }
  } catch (error) {
    handleApiError(error, '加载会话列表失败')
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
  if (isMobile.value) {
    mobileSidebarVisible.value = false
  }
  await loadMessages(session.id)
}

// 加载消息历史
const loadMessages = async (sessionId) => {
  try {
    const response = await chatApi.getMessages(sessionId)
    if (response?.code === 200) {
      messages.value = (response.data || []).map(msg => {
        // 清理AI消息内容中的多余字符
        if (msg.messageType === 1 && msg.content) {
          msg.content = cleanAIResponse(msg.content)
          msg.renderVersion = ++renderVersion // 关键添加
        }
        // 确保加载的消息不在流式状态
        msg.isStreaming = false
        return msg
      })
      nextTick(() => {
        if (messagesContainer.value) {
          messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
        }
      })
    }
  } catch (error) {
    handleApiError(error, '加载消息失败')
  }
}

// 发送消息
const sendMessage = async () => {
  if (!userMessage.value.trim() || isTyping.value) return

  if (!checkToken()) return

  const baseId = Date.now()
  const messageContent = userMessage.value.trim()
  userMessage.value = ''

  // 添加用户消息
  const userMessageObj = {
    id: baseId,
    content: messageContent,
    messageType: 0,
    createdAt: new Date().toISOString()
  }
  messages.value.push(userMessageObj)

  // 创建AI消息对象，提前定义以便在catch/finally块中访问
  const aiMessageObj = {
    id: baseId + 1,
    content: '',
    messageType: 1,
    createdAt: new Date().toISOString(),
    renderVersion: ++renderVersion, // 添加版本号字段
    isStreaming: true // 添加流式标记
  }

  // 显示输入状态
  isTyping.value = true
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })

  try {
    const baseURL = '/api'
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
    messages.value.push(aiMessageObj)
    // 获取 AI 消息在数组中的索引，用于后续更新
    const aiMessageIndex = messages.value.length - 1

    // SSE 缓冲区：按事件（\n\n）解析，保留换行以支持 Markdown
    let buffer = ''

    // 读取流式数据
    let isReading = true
    while (isReading) {
      const { done, value } = await reader.read()

      if (done) {
        isReading = false
        break
      }

      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replace(/\r\n/g, '\n').replace(/\r+/g, '')

      // 按行分割，处理每个 data: 行
      const lines = buffer.split('\n')
      // 保留最后一个可能不完整的行到缓冲区
      buffer = lines[lines.length - 1] || ''

      for (let i = 0; i < lines.length - 1; i++) {
        const line = lines[i].trim()
        if (!line) continue
        if (!line.startsWith('data:')) continue
        if (line === 'data:[DONE]') continue

        // 提取 data: 后面的内容
        const content = line.slice(5).replace(/^ /, '')
        aiMessageText += content

        // 每处理一个数据块就立即更新内容，实现流式显示效果
        // 直接通过数组索引更新，确保 Vue 响应式系统能检测到变化
        if (messages.value[aiMessageIndex]) {
          messages.value[aiMessageIndex].content = aiMessageText
          // 强制触发 Vue 响应式更新
          messages.value[aiMessageIndex].renderVersion = ++renderVersion
        }

        nextTick(() => {
          if (messagesContainer.value) {
            messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
          }
        })
      }
    }

    // 流式数据读取完成后，处理最终内容
    // 解析可能残留的最后一个事件（无 \n\n 结尾的情况）
    if (buffer && buffer.trim()) {
      const lines = buffer.split('\n')
      const dataLines = lines
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).replace(/^ /, ''))
      const eventData = dataLines.length ? dataLines.join('\n') : buffer
      if (eventData && eventData.trim() !== '[DONE]') {
        aiMessageText += eventData
      }
    }

    const cleanedContent = cleanAIResponse(aiMessageText)

    // 流式完成后，更新最终内容（本地更新，避免闪烁）
    if (messages.value[aiMessageIndex]) {
      messages.value[aiMessageIndex].content = cleanedContent
      messages.value[aiMessageIndex].renderVersion = ++renderVersion
      messages.value[aiMessageIndex].isStreaming = false
    }

    // 等待Vue更新完成
    await nextTick()

    // 重新加载消息列表，确保 Markdown 正确渲染
    await loadMessages(currentSessionId.value)

    await loadSessions()
  } catch (error) {
    console.error('Send message error:', error)
    isTyping.value = false

    // 确保清理流式标记
    const aiIndex = messages.value.findIndex(msg => msg.id === aiMessageObj.id)
    if (aiIndex > -1) {
      messages.value[aiIndex].isStreaming = false
    }

    handleApiError(error, error.message || '发送消息失败')
    const index = messages.value.findIndex(msg => msg.id === userMessageObj.id)
    if (index > -1) messages.value.splice(index, 1)

    // 也清理AI消息
    if (aiIndex > -1) messages.value.splice(aiIndex, 1)
  } finally {
    isTyping.value = false
    // 确保在所有情况下都清理流式状态
    const aiIndex = messages.value.findIndex(msg => msg.id === aiMessageObj.id)
    if (aiIndex > -1 && messages.value[aiIndex]) {
      messages.value[aiIndex].isStreaming = false
    }
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
        if (error !== 'cancel') {
          handleApiError(error, '删除失败')
        }
      }
      break
  }
}

// 处理用户下拉菜单命令
const handleUserCommand = async (command) => {
  switch (command) {
    case 'conversation-management':
      // 员工对话管理功能
      router.push('/worker/conversation-management')
      break
    case 'system-management':
      // 管理员系统管理功能
      router.push({ name: 'AdminDashboard' })
      break
    case 'profile':
      router.push('/profile')
      break
    case 'password':
      router.push('/password')
      break
    case 'contact-service':
      // 联系客服 - 跳转到独立的客服聊天窗口
      router.push('/chat-window')
      break
    case 'logout':
      try {
        await ElMessageBox.confirm(
            '确定要退出登录吗？',
            '提示',
            {
              confirmButtonText: '确定',
              cancelButtonText: '取消',
              type: 'warning'
            }
        )
        localStorage.removeItem('token')
        userStore.clearUserInfo()
        ElMessage.success('退出登录成功')
        router.push('/login')
      } catch {
        // 用户取消操作
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
    handleApiError(error, '重命名失败')
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
    transcriptBaseMessage.value = (userMessage.value || '').trimEnd()

    // 1. 启动流式识别会话
    const sessionId = currentSessionId.value || 'session_' + Date.now()
    const response = await fetch('/api/user/streaming/start', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ sessionId: sessionId })
    })

    if (!response.ok) {
      throw new Error('启动语音识别会话失败')
    }

    const result = await response.json()
    if (result.code !== 200) {
      throw new Error(result.msg || '启动语音识别会话失败')
    }

    streamingSessionToken.value = result.data

    // 2. 请求麦克风权限
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true
      }
    })

    mediaStream.value = stream

    // 3. 开始音频录制和处理
    try {
      await startWebAudioRecording(stream)
    } catch (webAudioError) {
      console.warn('Web Audio API 失败，回退到 MediaRecorder API:', webAudioError)
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
      ElMessage.error(error.message || '无法访问麦克风，请检查权限设置')
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
  resetTranscriptState(true)
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
      sendStreamingAudioChunk(chunkBuffer.value, actualSampleRate)
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
  resetTranscriptState(true)

  // 每1秒收集一次数据
  recorder.ondataavailable = (event) => {
    if (event.data.size > 0) {
      audioChunks.value.push(event.data)
    }
  }

  // 启动定时器，每1秒发送一次音频分片
  recordingTimer.value = setInterval(async () => {
    if (audioChunks.value.length > 0) {
      await sendStreamingMediaRecorderChunk()
    }
  }, 1000)

  recorder.start(1000) // 每1秒触发一次dataavailable事件
}

// 停止录音
const stopRecording = async () => {
  if (!isRecording.value) return

  // 保存当前的转录文本，避免后续被覆盖
  flushTranscriptUpdate()
  const currentTranscript = realTimeTranscript.value.trim()

  // 立即设置录音状态为false，确保UI立即更新显示麦克风图标
  isRecording.value = false

  // 停止定时器，不再发送新的音频分片
  if (recordingTimer.value) {
    clearInterval(recordingTimer.value)
    recordingTimer.value = null
  }

  // 发送最后的音频片段（Web Audio API方案）
  if (chunkBuffer.value.length > 0 && audioContext.value) {
    await sendStreamingAudioChunk(chunkBuffer.value, audioContext.value.sampleRate)
    chunkBuffer.value = new Int16Array(0)
  }

  // 发送最后的音频片段（MediaRecorder方案）
  if (audioChunks.value.length > 0) {
    await sendStreamingMediaRecorderChunk()
    audioChunks.value = []
  }

  // 延迟1.5秒后停止流式识别会话，确保最后一个音频分片的识别结果返回
  setTimeout(async () => {
    try {
      // 停止流式识别会话并获取最终结果
      if (streamingSessionToken.value) {
        const response = await fetch('/api/user/streaming/stop', {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ sessionToken: streamingSessionToken.value })
        })

        if (response.ok) {
          const result = await response.json()
          if (result.code === 200 && result.data) {
            const finalTranscript = result.data.trim()
            if (finalTranscript) {
              // 使用最终识别结果
              applyFinalTranscript(finalTranscript)
              ElMessage.success(`语音识别完成: ${finalTranscript}`)
            } else {
              // 如果最终结果为空，使用保存的实时转录结果
              if (currentTranscript) {
                applyFinalTranscript(currentTranscript)
                ElMessage.success(`语音识别完成: ${currentTranscript}`)
              }
            }
          } else {
            console.warn('停止语音识别会话失败:', response.status)
            // 失败时也使用保存的实时转录结果
            if (currentTranscript) {
              applyFinalTranscript(currentTranscript)
              ElMessage.success(`语音识别完成: ${currentTranscript}`)
            }
          }
        } else {
          console.warn('停止语音识别会话失败:', response.status)
          // 失败时也使用保存的实时转录结果
          if (currentTranscript) {
            applyFinalTranscript(currentTranscript)
            ElMessage.success(`语音识别完成: ${currentTranscript}`)
          }
        }
      } else {
        // 没有会话令牌时使用保存的实时转录结果
        if (currentTranscript) {
          applyFinalTranscript(currentTranscript)
          ElMessage.success(`语音识别完成: ${currentTranscript}`)
        }
      }

    } catch (error) {
      console.error('停止录音时发生错误:', error)
      // 发生错误时仍然尝试使用保存的实时转录结果
      if (currentTranscript) {
        applyFinalTranscript(currentTranscript)
        ElMessage.success(`语音识别完成: ${currentTranscript}`)
      }
    }

    // 最终清理状态
    resetTranscriptState()
    streamingSessionToken.value = ''

    // 清理录音资源
    cleanupRecording()
  }, 1500) // 延迟1.5秒发送停止请求
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
  resetTranscriptState()
}

// 发送音频分片到后端（Web Audio API方案）- 流式识别
const sendStreamingAudioChunk = async (audioData, sampleRate) => {
  try {
    // 检查录音状态和会话令牌
    if (!isRecording.value || !streamingSessionToken.value) return

    // 将Int16Array转换为WAV格式的Blob
    const wavBlob = createWavBlob(audioData, sampleRate)

    const formData = new FormData()
    formData.append('audioFile', wavBlob, 'chunk.wav')
    formData.append('sessionToken', streamingSessionToken.value)

    const response = await fetch('/api/user/streaming/audio', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: formData
    })

    if (response.ok) {
      const result = await response.json()
      if (result.code === 200 && result.data && result.data.trim()) {
        // 更新实时转录文本（服务器返回的是累积结果）
        scheduleTranscriptUpdate(result.data)
      }
    } else {
      console.warn('服务器响应错误:', response.status, response.statusText)
    }
  } catch (error) {
    console.error('流式音频分片发送失败:', error)
    // 不显示错误消息，避免频繁打扰用户
  }
}

// 发送音频分片到后端（MediaRecorder方案）- 流式识别
const sendStreamingMediaRecorderChunk = async () => {
  try {
    if (audioChunks.value.length === 0 || !isRecording.value || !streamingSessionToken.value) return

    // 合并所有音频块
    const combinedBlob = new Blob(audioChunks.value, {
      type: mediaRecorder.value.mimeType
    })

    const formData = new FormData()
    formData.append('audioFile', combinedBlob, 'chunk.webm')
    formData.append('sessionToken', streamingSessionToken.value)

    const response = await fetch('/api/user/streaming/audio', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: formData
    })

    if (response.ok) {
      const result = await response.json()
      if (result.code === 200 && result.data && result.data.trim()) {
        // 更新实时转录文本（服务器返回的是累积结果）
        scheduleTranscriptUpdate(result.data)
      }
    } else {
      console.warn('服务器响应错误:', response.status, response.statusText)
    }

    // 清空已发送的音频块
    audioChunks.value = []
  } catch (error) {
    console.error('MediaRecorder流式音频分片发送失败:', error)
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


// 加载用户信息（包含头像）
const loadUserInfo = async () => {
  try {
    await userStore.fetchUserInfo()
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
}

// 全局复制代码函数
window.copyCode = async function(button, code) {
  let decodedCode = code ?? button?.dataset?.copy ?? ''
  try {
    // 优先尝试 decodeURIComponent（新版本使用 encodeURIComponent 传参）
    try {
      decodedCode = decodeURIComponent(decodedCode)
    } catch (e) {
      // ignore
    }

    // 解码HTML实体（兼容旧版本）
    decodedCode = decodedCode
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&amp;/g, '&')
      .replace(/&quot;/g, '"')
      .replace(/&#39;/g, "'")
      .replace(/&#x2F;/g, '/')

    // 使用现代剪贴板API
    await navigator.clipboard.writeText(decodedCode)

    // 按钮状态更新
    const originalHTML = button.innerHTML
    button.classList.add('copied')
    button.innerHTML = `
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="20 6 9 17 4 12"></polyline>
      </svg>
    `

    // 按钮动画
    button.classList.add('copy-animation')

    // 2秒后恢复原状态
    setTimeout(() => {
      button.classList.remove('copied', 'copy-animation')
      button.innerHTML = originalHTML
    }, 2000)

  } catch (err) {
    console.error('复制失败:', err)

    // 降级方案：创建临时textarea
    const textArea = document.createElement('textarea')
    textArea.value = decodedCode

    textArea.style.position = 'fixed'
    textArea.style.left = '-999999px'
    textArea.style.top = '-999999px'
    document.body.appendChild(textArea)
    textArea.focus()
    textArea.select()

    try {
      document.execCommand('copy')

      // 复制成功的视觉反馈
      const originalHTML = button.innerHTML
      button.classList.add('copied')
      button.innerHTML = `
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="20 6 9 17 4 12"></polyline>
        </svg>
      `

      setTimeout(() => {
        button.classList.remove('copied')
        button.innerHTML = originalHTML
      }, 2000)
    } catch (fallbackErr) {
      console.error('降级复制也失败:', fallbackErr)
    }

    document.body.removeChild(textArea)
  }
}

// 表格复制（保持按钮宽度，避免布局跳动）
window.copyTable = async function(button, text) {
  let decodedText = text ?? button?.dataset?.copy ?? ''
  try {
    try {
      decodedText = decodeURIComponent(decodedText)
    } catch (e) {
      // ignore
    }

    await navigator.clipboard.writeText(decodedText)

    const originalHTML = button.innerHTML
    button.classList.add('copied')
    button.innerHTML = `
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="20 6 9 17 4 12"></polyline>
      </svg>
    `

    setTimeout(() => {
      button.classList.remove('copied')
      button.innerHTML = originalHTML
    }, 1600)
  } catch (err) {
    console.error('复制表格失败:', err)

    const textArea = document.createElement('textarea')
    textArea.value = decodedText
    textArea.style.position = 'fixed'
    textArea.style.left = '-999999px'
    textArea.style.top = '-999999px'
    document.body.appendChild(textArea)
    textArea.focus()
    textArea.select()

    try {
      document.execCommand('copy')
    } catch (fallbackErr) {
      console.error('降级复制表格也失败:', fallbackErr)
    }

    document.body.removeChild(textArea)
  }
}

// 挂载时加载
onMounted(() => {
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
  if (checkToken()) {
    loadSessions()
    loadUserInfo()
  }
})

// 组件卸载时清理资源
onUnmounted(() => {
  cleanupRecording()
  window.removeEventListener('resize', updateIsMobile)
})
</script>

<style scoped>
/* 全局基础样式 */
.chat-container {
  height: 100vh;
  height: 100dvh;
  background-color: var(--app-bg);
  overflow: hidden;
  color: var(--app-text);
}

/* 侧边栏样式 */
.session-sidebar {
  background-color: var(--app-surface);
  border-right: 1px solid var(--app-border);
  box-shadow: var(--app-shadow-xs);
  width: 280px !important;
  transition: all 0.3s ease;
}

/* 侧边栏头部 */
.session-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--app-border);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.session-header h3 {
  margin: 0;
  color: var(--app-text);
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
  background: linear-gradient(135deg, var(--app-primary) 0%, var(--app-primary-hover) 100%);
  border: none;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.new-session-btn:hover {
  background: linear-gradient(135deg, var(--app-primary-hover) 0%, var(--app-primary-active) 100%);
  box-shadow: var(--app-shadow-sm);
}

.new-session-btn:active {
  box-shadow: var(--app-shadow-xs);
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
  background-color: var(--app-primary-soft-2);
}

.session-item.active {
  background-color: var(--app-primary-soft);
  border-left: 3px solid var(--app-primary);
}

.session-info {
  flex: 1;
  overflow: hidden;
}

.session-name {
  font-weight: 500;
  color: var(--app-text);
  margin-bottom: 3px;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  font-size: 11px;
  color: var(--app-muted);
}

/* 会话操作图标 */
.session-actions {
  color: var(--app-muted);
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
  font-size: 32px;
  transition: all 0.2s ease;
}

.session-actions:hover {
  color: var(--app-primary);
  background-color: var(--app-primary-soft-2);
}

/* 聊天主区域 - 核心布局控制 */
.chat-main {
  height: 100vh;
  height: 100dvh;
  padding: 0;
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden; /* 关键：防止子元素溢出容器 */
}

/* 聊天头部 - 固定高度 */
.chat-header {
  background-color: var(--app-surface);
  border-bottom: 1px solid var(--app-border);
  padding: 0 24px;
  height: 64px; /* 固定高度 */
  flex-shrink: 0; /* 禁止收缩 */
  box-shadow: var(--app-shadow-xs);
  display: flex;
  align-items: center;
}

.chat-page-header {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
}

.chat-page-header .header-left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.chat-page-header .title {
  min-width: 0;
}

.chat-page-header .title h2 {
  margin: 0 0 6px 0;
  color: var(--app-text);
  font-size: 16px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-page-header .sub {
  margin: 0;
  color: var(--app-muted);
  font-size: 13px;
}

.rename-btn {
  color: var(--app-muted);
}

.rename-btn:hover {
  color: var(--app-primary);
}

/* 返回按钮样式 - 使用深度选择器确保样式穿透 */
:deep(.back-btn) {
  padding: 8px !important;
  border-radius: 8px !important;
  transition: all 0.2s ease;
  display: flex !important;
  align-items: center;
  justify-content: center;
  color: var(--app-muted) !important;
  font-size: 18px !important;
  min-width: 36px !important;
  height: 36px !important;
  background-color: var(--app-surface-2) !important;
  border: 1px solid var(--app-border) !important;
  position: relative;
  z-index: 10;
  margin-right: 8px;
}

:deep(.back-btn:hover) {
  background-color: var(--app-primary-soft-2) !important;
  color: var(--app-primary) !important;
  transform: translateX(-2px);
}

:deep(.back-btn:active) {
  transform: translateX(0);
}

.chat-page-header .header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mobile-only {
  display: none !important;
}

.mobile-sidebar-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
  z-index: 9;
}


:deep(.back-icon) {
  transition: transform 0.2s ease;
}

/* 用户下拉菜单 */
.user-dropdown {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 6px;
  transition: background-color 0.2s ease;
}

.user-dropdown:hover {
  background-color: var(--app-primary-soft-2);
}

.username {
  margin: 0 8px;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 500;
}

.user-dropdown .el-icon--right {
  color: var(--app-muted-2);
  font-size: 12px;
  transition: transform 0.2s ease;
}

.user-dropdown:hover .el-icon--right {
  transform: rotate(180deg);
}

/* 聊天内容区域 - 精确高度控制（解决挤压问题核心） */
.chat-content {
  background-color: var(--app-bg);
  flex-grow: 1; /* 占据剩余空间 */
  max-height: calc(100vh - 64px - 72px); /* 总高度 - 头部(64px) - 输入区(72px) */
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
  display: flex;
  flex-direction: column;
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
  align-self: flex-end;
  margin-left: auto;
}

.ai-message {
  align-self: flex-start;
  margin-right: auto;
}

/* 头像样式 */
.message-avatar {
  flex-shrink: 0;
}

.el-avatar {
  width: 36px !important;
  height: 36px !important;
  font-size: 16px !important;
  border-radius: 8px !important;
  transition: all 0.2s ease;
  background-color: transparent !important;
  overflow: hidden;
}

.el-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.el-avatar:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.user-avatar {
  border: 1px solid var(--app-border);
}

.ai-avatar {
  border: 1px solid var(--app-border);
}

/* 消息气泡 - 现代化样式 */
.message-content {
  max-width: 78ch;
  min-width: 0;
  padding: 10px 14px;
  position: relative;
  line-height: 1.6;
  font-size: 14px;
  border-radius: 12px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: var(--app-shadow-xs);
}

.message-content:hover {
  box-shadow: var(--app-shadow-sm);
}

.user-message .message-content {
  margin-right: 12px;
  margin-left: auto;
  max-width: 56ch;
  background: var(--app-primary);
  color: white;
  border-top-right-radius: 2px;
}

.user-message .message-content::before {
  content: "";
  position: absolute;
  top: 0;
  right: -8px;
  width: 0;
  height: 0;
  border-style: solid;
  border-width: 10px 8px 0 0;
  border-color: var(--app-primary) transparent transparent transparent;
}

.ai-message .message-content {
  margin-left: 12px;
  margin-right: auto;
  max-width: 78ch;
  padding: 12px 16px;
  background-color: var(--app-surface);
  color: var(--app-text);
  border-top-left-radius: 2px;
  border: 1px solid var(--app-border);
}

.ai-message .message-content::before {
  content: "";
  position: absolute;
  top: 0;
  left: -8px;
  width: 0;
  height: 0;
  border-style: solid;
  border-width: 0 8px 10px 0;
  border-color: transparent var(--app-surface) transparent transparent;
}

/* 消息时间样式优化 */
.message-time {
  font-size: 11px;
  opacity: 0.6;
  margin-top: 8px;
  text-align: right;
  color: var(--app-muted);
  font-weight: 500;
  letter-spacing: 0.5px;
}

.user-message .message-time {
  color: rgba(255, 255, 255, 0.7);
}

/* 消息头像优化 */
.message-avatar {
  flex-shrink: 0;
  transition: transform 0.2s ease;
}

.message-avatar:hover {
  transform: scale(1.05);
}

/* 消息输入指示器优化 */
.typing-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 0;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  animation: typing 1.4s infinite ease-in-out both;
  box-shadow: 0 2px 4px rgba(59, 130, 246, 0.3);
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
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* 流式文本动画优化 */
.streaming-text {
  color: #374151;
  white-space: pre-wrap;
  word-wrap: break-word;
  line-height: 1.7;
  font-family: inherit;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-radius: 12px;
  padding: 16px 20px;
  border-left: 4px solid #3b82f6;
  animation: streamingPulse 2s ease-in-out infinite;
}

@keyframes streamingPulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.1);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(59, 130, 246, 0.1);
  }
}

/* 消息文本 */
.message-text {
  word-wrap: break-word;
  white-space: pre-wrap;
}

.markdown-content {
  font-size: 14px;
}

/* 代码块容器 - 现代化设计 */
.code-block-wrapper {
  background: #282c34;
  border-radius: 12px;
  margin: 16px 0;
  overflow: hidden;
  position: relative;
  border: 1px solid #3e4451;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  transition: all 0.3s ease;
}

.code-block-wrapper:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
  transform: translateY(-2px);
}

.markdown-content .code-block-wrapper pre {
  background: transparent;
  border-radius: 0;
  padding: 0;
  margin: 0;
  overflow: hidden;
  position: relative;
  border: none;
  box-shadow: none;
}

.markdown-content .code-block-wrapper pre::before {
  content: none;
}

/* 代码块头部 */
.code-block-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: rgba(0, 0, 0, 0.3);
  border-bottom: 1px solid #3e4451;
  min-height: 40px;
  backdrop-filter: blur(10px);
}

.code-language {
  font-size: 12px;
  font-weight: 600;
  color: #abb2bf;
  text-transform: uppercase;
  letter-spacing: 0.8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.code-language-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #98c379;
  box-shadow: 0 0 10px rgba(152, 195, 121, 0.5);
  animation: pulseDot 2s ease-in-out infinite;
}

@keyframes pulseDot {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.15);
  }
}

.code-actions {
  display: flex;
  gap: 8px;
}

.copy-btn {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #abb2bf;
  padding: 5px 12px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.copy-btn:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.25);
  color: #ffffff;
  transform: translateY(-1px);
}

.copy-btn.copied {
  background: #98c379;
  border-color: #98c379;
  color: white;
}

.copy-animation {
  animation: copySuccess 0.3s ease-out;
}

@keyframes copySuccess {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
  100% {
    transform: scale(1);
  }
}

/* 代码内容区域 */
.markdown-content .code-block-wrapper pre code {
  display: block;
  padding: 20px;
  background: transparent;
  color: #abb2bf;
  font-family: 'Consolas', 'Monaco', 'Courier New', 'Fira Code', monospace;
  font-size: 14px;
  line-height: 1.6;
  overflow-x: auto;
  border-radius: 0;
  white-space: pre;
  word-wrap: normal;
  tab-size: 4;
}

/* highlight.js 主题适配 */
.markdown-content pre code .hljs-comment,
.markdown-content pre code .hljs-quote {
  color: #5c6370;
  font-style: italic;
}

.markdown-content pre code .hljs-keyword,
.markdown-content pre code .hljs-selector-tag,
.markdown-content pre code .hljs-literal,
.markdown-content pre code .hljs-section,
.markdown-content pre code .hljs-link {
  color: #c678dd;
  font-weight: 600;
}

.markdown-content pre code .hljs-function .hljs-keyword {
  color: #c678dd;
}

.markdown-content pre code .hljs-subst {
  color: #e06c75;
}

.markdown-content pre code .hljs-string,
.markdown-content pre code .hljs-title,
.markdown-content pre code .hljs-name,
.markdown-content pre code .hljs-type,
.markdown-content pre code .hljs-attribute,
.markdown-content pre code .hljs-symbol,
.markdown-content pre code .hljs-bullet,
.markdown-content pre code .hljs-addition,
.markdown-content pre code .hljs-variable,
.markdown-content pre code .hljs-template-tag,
.markdown-content pre code .hljs-template-variable {
  color: #98c379;
}

.markdown-content pre code .hljs-number,
.markdown-content pre code .hljs-selector-attr,
.markdown-content pre code .hljs-selector-pseudo {
  color: #d19a66;
}

.markdown-content pre code .hljs-built_in,
.markdown-content pre code .hljs-builtin-name,
.markdown-content pre code .hljs-class .hljs-title {
  color: #e6c07b;
}

.markdown-content pre code .hljs-attr,
.markdown-content pre code .hljs-variable,
.markdown-content pre code .hljs-template-variable,
.markdown-content pre code .hljs-class .hljs-title,
.markdown-content pre code .hljs-type {
  color: #e6c07b;
}

.markdown-content pre code .hljs-selector-class,
.markdown-content pre code .hljs-selector-id {
  color: #e6c07b;
}

.markdown-content pre code .hljs-meta,
.markdown-content pre code .hljs-meta-keyword {
  color: #61afef;
}

.markdown-content pre code .hljs-meta-string {
  color: #98c379;
}

.markdown-content pre code .hljs-deletion {
  color: #e06c75;
}

.markdown-content pre code .hljs-regexp {
  color: #56b6c2;
}

.markdown-content pre code .hljs-emphasis {
  font-style: italic;
}

.markdown-content pre code .hljs-strong {
  font-weight: bold;
}

/* 代码块滚动条美化 */
.markdown-content pre code::-webkit-scrollbar {
  height: 8px;
}

.markdown-content pre code::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.2);
  border-radius: 4px;
}

.markdown-content pre code::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  transition: background 0.2s ease;
}

.markdown-content pre code::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.3);
}

/* 内联代码样式 */
.markdown-content :not(pre) > code {
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  color: #92400e;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  border: 1px solid #fbbf24;
  box-shadow: 0 1px 3px rgba(251, 191, 36, 0.2);
  font-family: 'Consolas', 'Monaco', 'Courier New', 'Fira Code', monospace;
  white-space: nowrap;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .code-block-wrapper {
    margin: 12px -8px;
    border-radius: 8px;
  }

  .code-block-header {
    padding: 10px 12px;
  }

  .markdown-content pre code {
    padding: 16px 12px;
    font-size: 13px;
  }

  .copy-btn {
    padding: 4px 8px;
    font-size: 11px;
  }
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
  background-color: var(--app-surface);
  border-top: 1px solid var(--app-border);
  padding: 12px 24px;
  min-height: 72px; /* 最小高度，会根据转录区域扩展 */
  height: auto;
  flex-shrink: 0; /* 禁止收缩 */
  box-sizing: border-box;
  box-shadow: 0 -1px 3px rgba(15, 23, 42, 0.06);
}

/* 输入框容器 */
.input-container {
  max-width: 1000px;
  margin: 0 auto;
  position: relative;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: stretch;
  isolation: isolate; /* 创建新的堆叠上下文 */
  overflow: visible; /* 确保按钮不被裁剪 */
  z-index: 10; /* 为容器设置基础层级 */
}

.input-row {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 56px;
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
  background-color: var(--app-surface-2);
  border: 1px solid var(--app-border);
  color: var(--app-muted);
  z-index: 9999;
  user-select: none;
  cursor: pointer;
  outline: none;
  pointer-events: auto;
  box-shadow: var(--app-shadow-xs);
}

.voice-btn:focus {
  outline: none;
  box-shadow: var(--app-ring);
}

/* 确保图标正确显示 */
.voice-btn .el-icon {
  font-size: 18px;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.voice-btn:hover {
  background-color: var(--app-primary-soft-2);
  color: var(--app-text);
  border-color: var(--app-border-strong);
}

.voice-btn.recording {
  background-color: #ef4444 !important;
  border-color: #dc2626 !important;
  color: white !important;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.4) !important;
}

.voice-btn.voice-loading {
  background-color: #ffb020;
  border-color: #ff9a00;
  color: white;
  display: flex !important; /* 强制显示 */
  visibility: visible !important;
  opacity: 1 !important;
}

.voice-btn:disabled {
  background-color: var(--app-surface-2);
  color: var(--app-muted-2);
  border-color: var(--app-border);
  cursor: not-allowed;
  display: flex !important; /* 强制显示 */
  visibility: visible !important;
  opacity: 1 !important;
}

/* 状态文字样式已移除，现在只使用图标 */

/* 脉冲动画已移除，避免层级问题 */

/* 输入框内部的转录显示区域 */
.transcript-input-display {
  position: relative;
  margin-left: 72px;
  margin-right: 72px;
  background-color: var(--app-primary-soft-2);
  border: 1px solid rgba(var(--app-primary-rgb), 0.25);
  border-radius: 8px;
  padding: 8px 12px;
  z-index: 5;
  font-size: 13px;
  box-shadow: var(--app-shadow-xs);
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
  max-height: 160px;
  min-height: 28px;
  overflow-y: auto;
  overflow-wrap: anywhere;
  word-wrap: break-word;
  white-space: pre-wrap;
}

/* 强制停止按钮样式已移除，现在使用主录音按钮 */

/* 输入框样式 */
.message-input {
  flex-grow: 1; /* 占满容器宽度 */
  position: relative; /* 确保输入框不会影响按钮定位 */
  z-index: 0; /* 降低输入框层级 */
}

:deep(.message-input .el-textarea__inner) {
  border-radius: 12px;
  border: 1px solid var(--app-border);
  background-color: var(--app-surface);
  color: var(--app-text);
  padding: 10px 16px;
  padding-left: 72px; /* 给录音按钮留出空间 */
  padding-right: 72px;
  min-height: 44px;
  max-height: 160px; /* 限制最大高度 */
  box-sizing: border-box;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  background-image: none;
  transition: border-color 160ms ease, box-shadow 160ms ease, background-color 160ms ease;
}

:deep(.message-input .el-textarea__inner::placeholder) {
  color: var(--app-muted-2);
  font-size: 14px;
  opacity: 1;
}

:deep(.message-input .el-textarea__inner:hover) {
  border-color: var(--app-border-strong);
}

:deep(.message-input .el-textarea__inner:focus) {
  border-color: var(--app-primary);
  box-shadow: var(--app-ring);
  outline: none;
}

.el-input:hover .el-input__inner {
  border-color: var(--app-border-strong);
  box-shadow: 0 0 0 2px rgba(var(--app-primary-rgb), 0.06);
}

.el-input:focus-within .el-input__inner {
  border-color: var(--app-primary);
  box-shadow: var(--app-ring);
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
  background: linear-gradient(135deg, var(--app-primary) 0%, var(--app-primary-hover) 100%);
  border: none;
  box-shadow: 0 2px 10px rgba(var(--app-primary-rgb), 0.22);
  transition: all 0.2s ease;
  z-index: 50; /* 提高发送按钮的层级 */
}

.send-btn:hover {
  box-shadow: 0 8px 18px rgba(var(--app-primary-rgb), 0.28);
  background: linear-gradient(135deg, var(--app-primary-hover) 0%, var(--app-primary-active) 100%);
}

.send-btn:active {
  box-shadow: 0 2px 10px rgba(var(--app-primary-rgb), 0.22);
}

.send-btn:disabled {
  background: var(--app-surface-2);
  color: var(--app-muted-2);
  box-shadow: none;
  transform: translateY(-50%);
}

.send-btn .send-icon.is-loading {
  animation: send-spin 1.2s linear infinite;
}

@keyframes send-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
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
  background-color: var(--app-muted);
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

/* 流式文本样式 */
.streaming-text {
  color: inherit;
  white-space: pre-wrap;
  word-wrap: break-word;
  line-height: 1.6;
  font-family: inherit;
  background: linear-gradient(135deg, rgba(var(--app-primary-rgb), 0.12) 0%, rgba(var(--app-primary-rgb), 0.06) 100%);
  border-radius: 12px;
  padding: 12px 16px;
  border-left: 3px solid var(--app-primary);
  animation: none;
}

/* ===== 现代化Markdown样式 ===== */

/* 基础文本样式 */
.markdown-content {
  line-height: 1.75;
  color: inherit;
  font-size: 15px;
  overflow-wrap: anywhere;
  word-break: break-word;
  font-family: inherit;
  white-space: normal;
  --md-task-check-icon: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3E%3Cpath d='M3 8.5l3 3 7-7' fill='none' stroke='%23111827' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
}

:global(html.dark) .markdown-content {
  --md-task-check-icon: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3E%3Cpath d='M3 8.5l3 3 7-7' fill='none' stroke='%23E2E8F0' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
}

/* Markdown 子元素需要使用 :deep 才能在 v-html 中生效（scoped 样式） */
.markdown-content :deep(p) {
  margin: 12px 0;
  line-height: 1.75;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin: 18px 0 10px;
  font-weight: 700;
  line-height: 1.25;
  scroll-margin-top: 16px;
}

.markdown-content :deep(h1) {
  font-size: 22px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--app-border);
}

.markdown-content :deep(h2) {
  font-size: 18px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(var(--app-primary-rgb), 0.12);
}

.markdown-content :deep(h3) {
  font-size: 16px;
}

.markdown-content :deep(h4) {
  font-size: 15px;
}

.markdown-content :deep(h5) {
  font-size: 14px;
}

.markdown-content :deep(h6) {
  font-size: 13px;
  opacity: 0.9;
}

.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  display: block;
  margin: 12px 0;
  border-radius: 12px;
  border: 1px solid var(--app-border);
  box-shadow: var(--app-shadow-xs);
}

.markdown-content :deep(.md-image-alt) {
  color: var(--app-muted);
  font-size: 13px;
}

.markdown-content :deep(.code-block-wrapper) {
  margin: 14px 0;
  border: 1px solid var(--app-border);
  border-radius: 14px;
  overflow: hidden;
  background: var(--app-surface);
  box-shadow: var(--app-shadow-xs);
}

.markdown-content :deep(.code-block-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  background: var(--app-surface-2);
  border-bottom: 1px solid var(--app-border);
}

.markdown-content :deep(.code-language) {
  font-size: 12px;
  font-weight: 600;
  color: var(--app-muted);
  letter-spacing: 0.02em;
  text-transform: uppercase;
  user-select: none;
}

.markdown-content :deep(.code-actions) {
  display: flex;
  align-items: center;
  gap: 8px;
}

.markdown-content :deep(.copy-btn) {
  appearance: none;
  border: 1px solid var(--app-border);
  background: transparent;
  color: var(--app-muted);
  border-radius: 10px;
  width: 32px;
  height: 32px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.markdown-content :deep(.copy-btn:hover) {
  background: rgba(var(--app-primary-rgb), 0.08);
  color: var(--app-primary);
  border-color: rgba(var(--app-primary-rgb), 0.35);
}

.markdown-content :deep(.copy-btn.copied) {
  color: #10b981;
  border-color: rgba(16, 185, 129, 0.35);
  background: rgba(16, 185, 129, 0.08);
}

.markdown-content :deep(pre.code-block) {
  margin: 0;
  background: transparent;
  border: none;
  overflow: auto;
}

.markdown-content :deep(.code-content) {
  display: block;
  padding: 14px 16px;
  overflow-x: auto;
  line-height: 1.7;
  font-size: 13px;
  color: inherit;
  background: transparent;
  border: none;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  white-space: pre;
  word-wrap: normal;
  tab-size: 4;
}

.markdown-content :deep(.hljs) {
  background: transparent;
}

.markdown-content :deep(:not(pre) > code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.92em;
  padding: 0.16em 0.38em;
  border-radius: 8px;
  border: 1px solid var(--app-border);
  background: var(--app-surface-2);
}

.markdown-content :deep(.hljs-comment),
.markdown-content :deep(.hljs-quote) {
  color: #6b7280;
  font-style: italic;
}

.markdown-content :deep(.hljs-keyword),
.markdown-content :deep(.hljs-selector-tag),
.markdown-content :deep(.hljs-literal) {
  color: #7c3aed;
  font-weight: 650;
}

.markdown-content :deep(.hljs-string),
.markdown-content :deep(.hljs-title),
.markdown-content :deep(.hljs-name),
.markdown-content :deep(.hljs-type),
.markdown-content :deep(.hljs-attribute) {
  color: #0f766e;
}

.markdown-content :deep(.hljs-number),
.markdown-content :deep(.hljs-symbol),
.markdown-content :deep(.hljs-bullet) {
  color: #b45309;
}

.markdown-content :deep(.hljs-built_in),
.markdown-content :deep(.hljs-class .hljs-title) {
  color: #1d4ed8;
}

:global(html.dark) .markdown-content :deep(.hljs-comment),
:global(html.dark) .markdown-content :deep(.hljs-quote) {
  color: #94a3b8;
}

:global(html.dark) .markdown-content :deep(.hljs-keyword),
:global(html.dark) .markdown-content :deep(.hljs-selector-tag),
:global(html.dark) .markdown-content :deep(.hljs-literal) {
  color: #c4b5fd;
}

:global(html.dark) .markdown-content :deep(.hljs-string),
:global(html.dark) .markdown-content :deep(.hljs-title),
:global(html.dark) .markdown-content :deep(.hljs-name),
:global(html.dark) .markdown-content :deep(.hljs-type),
:global(html.dark) .markdown-content :deep(.hljs-attribute) {
  color: #5eead4;
}

:global(html.dark) .markdown-content :deep(.hljs-number),
:global(html.dark) .markdown-content :deep(.hljs-symbol),
:global(html.dark) .markdown-content :deep(.hljs-bullet) {
  color: #fbbf24;
}

:global(html.dark) .markdown-content :deep(.hljs-built_in),
:global(html.dark) .markdown-content :deep(.hljs-class .hljs-title) {
  color: #93c5fd;
}

.markdown-content p {
  margin: 16px 0;
  line-height: 1.7;
}

/* 标题样式 - 渐变效果 */
.markdown-content h1,
.markdown-content h2,
.markdown-content h3,
.markdown-content h4,
.markdown-content h5,
.markdown-content h6 {
  margin: 24px 0 16px;
  font-weight: 700;
  line-height: 1.3;
  position: relative;
  scroll-margin-top: 20px;
}

.markdown-content h1 {
  font-size: 28px;
  background: linear-gradient(135deg, var(--app-primary) 0%, var(--app-primary-deep) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  padding-bottom: 12px;
  margin-bottom: 24px;
}

.markdown-content h1::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 60px;
  height: 3px;
  background: linear-gradient(135deg, var(--app-primary) 0%, var(--app-primary-deep) 100%);
  border-radius: 2px;
}

.markdown-content h2 {
  font-size: 22px;
  color: inherit;
  padding-bottom: 8px;
  border-bottom: 2px solid var(--app-border);
  position: relative;
}

.markdown-content h2::before {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 40px;
  height: 2px;
  background: linear-gradient(135deg, var(--app-primary) 0%, var(--app-primary-hover) 100%);
  border-radius: 1px;
}

.markdown-content h3 {
  font-size: 18px;
  color: inherit;
  display: flex;
  align-items: center;
}

.markdown-content h3::before {
  content: '▸';
  color: var(--app-primary);
  margin-right: 8px;
  font-size: 16px;
}

.markdown-content h4 {
  font-size: 16px;
  color: inherit;
  opacity: 0.95;
}

.markdown-content h5 {
  font-size: 15px;
  color: inherit;
  opacity: 0.9;
}

.markdown-content h6 {
  font-size: 14px;
  color: inherit;
  opacity: 0.85;
}

/* 现代化代码块样式 */
.markdown-content pre {
  background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
  border-radius: 12px;
  padding: 0;
  margin: 20px 0;
  overflow: hidden;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.3);
  border: 1px solid #475569;
  position: relative;
}

.markdown-content pre::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.1), transparent);
}

.markdown-content pre code {
  display: block;
  padding: 20px;
  overflow-x: auto;
  line-height: 1.6;
  font-size: 14px;
  color: #e2e8f0;
  background: transparent;
  border: none;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

/* 行内代码样式 */
.markdown-content code:not(pre code) {
  background: linear-gradient(135deg, #fef3c7 0%, #fed7aa 100%);
  color: #92400e;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  border: 1px solid #f59e0b;
  box-shadow: 0 1px 2px rgba(245, 158, 11, 0.2);
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

/* SQL代码块特殊样式 */
.markdown-content pre code[class*="language-sql"] {
  color: #86efac;
}

/* 现代化表格样式（卡片） */
.markdown-content :deep(.md-table-card) {
  margin: 18px 0;
  border: 1px solid var(--app-border);
  border-radius: 14px;
  overflow: hidden;
  background: var(--app-surface);
  box-shadow: var(--app-shadow-xs);
}

.markdown-content :deep(.md-table-card-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: var(--app-surface-2);
  border-bottom: 1px solid var(--app-border);
}

.markdown-content :deep(.md-table-card-title) {
  font-size: 14px;
  font-weight: 700;
  color: var(--app-text);
}

.markdown-content :deep(.md-table-copy-btn) {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--app-border);
  background: transparent;
  color: var(--app-muted);
  cursor: pointer;
  transition: all 0.2s ease;
}

.markdown-content :deep(.md-table-copy-btn:hover) {
  color: var(--app-primary);
  border-color: rgba(var(--app-primary-rgb), 0.35);
  background: rgba(var(--app-primary-rgb), 0.08);
}

.markdown-content :deep(.md-table-copy-btn.copied) {
  color: #10b981;
  border-color: rgba(16, 185, 129, 0.35);
  background: rgba(16, 185, 129, 0.08);
}

.markdown-content :deep(.md-table-card-body) {
  background: var(--app-surface);
}

.markdown-content :deep(.md-table-scroll) {
  overflow-x: auto;
}

.markdown-content :deep(.md-table-card table) {
  width: 100%;
  min-width: 520px;
  border-collapse: separate;
  border-spacing: 0;
}

.markdown-content :deep(.md-table-card th),
.markdown-content :deep(.md-table-card td) {
  padding: 14px 16px;
  line-height: 1.55;
  border-bottom: 1px solid var(--app-border);
  border-right: 1px solid rgba(var(--app-primary-rgb), 0.08);
  white-space: normal;
  word-break: break-word;
}

.markdown-content :deep(.md-table-card th) {
  background: rgba(var(--app-primary-rgb), 0.04);
  font-weight: 800;
  color: var(--app-text);
}

.markdown-content :deep(.md-table-card th:last-child),
.markdown-content :deep(.md-table-card td:last-child) {
  border-right: none;
}

.markdown-content :deep(.md-table-card tr:last-child td) {
  border-bottom: none;
}

.markdown-content :deep(.md-table-card tbody tr:nth-child(even)) {
  background-color: rgba(var(--app-primary-rgb), 0.03);
}

.markdown-content :deep(.md-table-card tbody tr:hover) {
  background-color: rgba(var(--app-primary-rgb), 0.06);
  transition: background-color 0.2s ease;
}

/* 现代化列表样式 */
.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 14px 0;
  padding-left: 26px;
  list-style-position: outside;
}

.markdown-content :deep(li) {
  margin: 6px 0;
  line-height: 1.7;
}

/* 避免列表项内部 <p> 产生过大间距 */
.markdown-content :deep(li > p) {
  margin: 0;
}

/* 嵌套列表缩进 & 间距 */
.markdown-content :deep(li > ul),
.markdown-content :deep(li > ol) {
  margin: 10px 0 0;
  padding-left: 20px;
}

/* 无序列表层级 */
.markdown-content :deep(ul) {
  list-style-type: disc;
}

.markdown-content :deep(ul ul) {
  list-style-type: circle;
}

.markdown-content :deep(ul ul ul) {
  list-style-type: square;
}

/* 有序列表层级 */
.markdown-content :deep(ol) {
  list-style-type: decimal;
}

/* 有序列表嵌套：1 / a / i */
.markdown-content :deep(ol ol) {
  list-style-type: lower-alpha;
}

.markdown-content :deep(ol ol ol) {
  list-style-type: lower-roman;
}

/* marker 颜色（区分层级） */
.markdown-content :deep(ul li::marker),
.markdown-content :deep(ol li::marker) {
  color: var(--app-primary);
  font-weight: 700;
}

.markdown-content :deep(ul ul li::marker),
.markdown-content :deep(ol ol li::marker) {
  color: #10b981;
}

.markdown-content :deep(ul ul ul li::marker),
.markdown-content :deep(ol ol ol li::marker) {
  color: #f59e0b;
}

/* 任务列表（GFM） */
.markdown-content :deep(ul.md-task-list),
.markdown-content :deep(ol.md-task-list) {
  padding-left: 0;
  margin: 14px 0;
  list-style: none;
}

.markdown-content :deep(.md-task-item) {
  list-style: none;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 10px 0;
}

.markdown-content :deep(.md-task-checkbox) {
  margin: 2px 0 0;
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
  border: 2px solid var(--app-border);
  border-radius: 5px;
  background: var(--app-surface);
  appearance: none;
  -webkit-appearance: none;
  display: inline-block;
}

.markdown-content :deep(.md-task-checkbox:checked) {
  background-image: var(--md-task-check-icon);
  background-repeat: no-repeat;
  background-position: center;
  background-size: 14px 14px;
}

.markdown-content :deep(.md-task-checkbox:disabled) {
  opacity: 1;
  cursor: default;
}

.markdown-content :deep(.md-task-content) {
  min-width: 0;
}

.markdown-content :deep(.md-task-content > p) {
  margin: 0;
}

.markdown-content :deep(.md-task-item.is-checked .md-task-content) {
  color: var(--app-muted);
  opacity: 0.75;
  text-decoration: line-through;
  text-decoration-thickness: 1px;
  text-decoration-color: rgba(148, 163, 184, 0.9);
}

/* 脚注（简单支持） */
.markdown-content :deep(.footnote-ref) {
  font-size: 0.85em;
  vertical-align: super;
  line-height: 0;
}

.markdown-content :deep(.footnote-ref a) {
  text-decoration: none;
  border-bottom: none;
  padding: 0 2px;
}

.markdown-content :deep(.footnotes) {
  margin-top: 18px;
  padding-top: 8px;
  border-top: 1px dashed var(--app-border);
  font-size: 13px;
  opacity: 0.95;
}

.markdown-content :deep(.footnotes hr) {
  display: none;
}

.markdown-content :deep(.footnotes ol) {
  margin: 8px 0 0;
  padding-left: 22px;
}

.markdown-content :deep(.footnotes li) {
  margin: 6px 0;
}

.markdown-content :deep(.footnote-content p) {
  margin: 8px 0;
}

.markdown-content :deep(.footnote-backref) {
  margin-left: 8px;
  font-size: 12px;
  opacity: 0.8;
}

/* 现代化引用块样式 */
.markdown-content :deep(blockquote) {
  margin: 12px 0;
  padding: 0 0 0 14px;
  border-left: 4px solid rgba(var(--app-primary-rgb), 0.22);
  background: transparent;
  color: inherit;
  border-radius: 0;
}

.markdown-content :deep(blockquote blockquote) {
  margin: 10px 0 0;
  padding-left: 14px;
  border-left-color: rgba(var(--app-primary-rgb), 0.16);
}

.markdown-content :deep(blockquote p) {
  margin: 8px 0;
}

.markdown-content :deep(blockquote > :first-child) {
  margin-top: 0;
}

.markdown-content :deep(blockquote > :last-child) {
  margin-bottom: 0;
}

/* 链接样式 */
.markdown-content :deep(a) {
  color: var(--app-primary);
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 2px;
  font-weight: 550;
  transition: color 0.15s ease;
}

.markdown-content :deep(a:hover) {
  color: var(--app-primary-hover);
}

/* 粗体和斜体 */
.markdown-content :deep(strong) {
  color: inherit;
  font-weight: 700;
}

.markdown-content :deep(em) {
  color: inherit;
  font-style: italic;
}

/* 水平分割线 */
.markdown-content :deep(hr) {
  border: 0;
  border-top: 1px solid var(--app-border);
  margin: 20px 0;
}

/* 纯文本样式 */
.plain-text {
  white-space: pre-wrap;
  word-wrap: break-word;
  line-height: 1.6;
  color: inherit;
  margin: 16px 0;
  font-family: inherit;
}

/* 防止文本选中 - 保持原有行为 */
.markdown-content * {
  -webkit-user-select: text;
  -moz-user-select: text;
  -ms-user-select: text;
  user-select: text;
}

/* 段落间距优化 */
.markdown-content :deep(p:first-child) {
  margin-top: 0;
}

.markdown-content :deep(p:last-child) {
  margin-bottom: 0;
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
    max-width: 90%;
  }
}

@media (max-width: 768px) {
  .session-sidebar {
    position: absolute;
    z-index: 10;
    left: 0;
    top: 0;
    bottom: 0;
    width: min(86vw, 320px) !important;
    height: 100vh;
    height: 100dvh;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.25);
  }

  .session-sidebar.show {
    transform: translateX(0);
  }

  .mobile-only {
    display: inline-flex !important;
  }

  .chat-header {
    padding: 0 16px;
  }

  :deep(.back-btn) {
    min-width: 32px !important;
    height: 32px !important;
    font-size: 16px !important;
    padding: 6px !important;
  }

  .chat-page-header .header-left {
    gap: 6px;
    align-items: center;
  }

  .chat-page-header {
    align-items: center;
  }

  .chat-page-header .title h2 {
    margin: 0;
  }

  .chat-page-header .sub {
    display: none;
  }

  .username {
    display: none;
  }

  .user-dropdown {
    padding: 6px 8px;
  }

  .chat-content {
    max-height: calc(100vh - 64px - 80px); /* 移动端输入区略窄 */
    max-height: calc(100dvh - 64px - 80px);
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

  .transcript-input-display {
    margin-left: 64px;
    margin-right: 64px;
  }

  .transcript-input-content {
    max-height: 110px;
  }

  .transcript-content {
    font-size: 13px;
  }

  .transcript-hint {
    font-size: 11px;
  }

  .message-content {
    max-width: 92%;
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

  :deep(.back-btn) {
    min-width: 28px !important;
    height: 28px !important;
    font-size: 14px !important;
    padding: 4px !important;
  }

  .chat-page-header .header-left {
    gap: 4px;
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

/* ===== 客服对话框样式 ===== */

.customer-service-container {
  display: flex;
  flex-direction: column;
  height: 400px;
  gap: 12px;
}

.customer-service-messages {
  flex: 1;
  overflow-y: auto;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  padding: 12px;
  background-color: var(--app-surface-2);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.empty-message {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.message-bubble {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: var(--app-radius-sm);
  max-width: 80%;
  word-wrap: break-word;
  word-break: break-word;
}

.message-bubble.user-msg {
  align-self: flex-end;
  background-color: var(--app-primary);
  color: white;
}

.message-bubble.admin-msg {
  align-self: flex-start;
  background-color: var(--app-surface);
  border: 1px solid var(--app-border);
  color: var(--app-text);
}

.message-content {
  font-size: 14px;
  line-height: 1.5;
}

.message-time {
  font-size: 12px;
  opacity: 0.7;
  text-align: right;
}

.message-bubble.user-msg .message-time {
  text-align: right;
  color: rgba(255, 255, 255, 0.8);
}

.message-bubble.admin-msg .message-time {
  text-align: left;
  color: var(--app-muted);
}

.topic-selector {
  display: flex;
  gap: 8px;
}

.topic-selector .el-select {
  flex: 1;
}

.message-input-container {
  display: flex;
  gap: 8px;
}

.message-input-container .el-input {
  flex: 1;
}

</style>
