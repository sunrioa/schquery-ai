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
        <el-header class="chat-header">
          <div class="chat-header-left">
            <div class="chat-title" v-if="currentSession">
              <h3>{{ currentSession.sessionName || '未命名会话' }}</h3>
              <el-button type="text" size="small" @click="showRenameDialog = true">
                <el-icon><Edit /></el-icon>
              </el-button>
            </div>
            <div class="chat-title" v-else>
              <h3>SchQueryAI 智能聊天</h3>
            </div>
          </div>

          <div class="chat-header-right">
            <el-dropdown @command="handleUserCommand" trigger="click">
              <span class="user-dropdown">
                <el-avatar :size="32" :src="userStore.getDisplayAvatar()" />
                <span class="username">{{ userStore.userInfo.userName || '用户' }}</span>
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">
                    <el-icon><User /></el-icon>
                    个人信息
                  </el-dropdown-item>
                  <el-dropdown-item command="password">
                    <el-icon><Lock /></el-icon>
                    修改密码
                  </el-dropdown-item>
                  <el-dropdown-item divided command="logout">
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
                    :size="40"
                    :src="message.messageType === 0 ? userStore.getDisplayAvatar() : getAIAvatar()"
                    :icon="null"
                    :class="{ 'user-avatar': message.messageType === 0, 'ai-avatar': message.messageType === 1 }"
                />
              </div>
              <div class="message-content">
                <div class="message-text" v-if="message.messageType === 0">{{ message.content }}</div>
                <div class="message-text" v-else-if="message.content">
                  <!-- 流式传输时显示预处理文本，完成后显示Markdown格式 -->
                  <div v-if="streamingMessageIds.has(message.id)" class="streaming-text" v-text="message.content"></div>
                  <div v-else class="markdown-content"
                       :key="`md-${message.id}-${message.renderVersion || 0}`"
                       v-html="renderMarkdown(message.content)"></div>
                </div>
                <div class="message-time">{{ formatTime(message.createdAt) }}</div>
              </div>
            </div>

            <div v-if="isTyping" class="message-item ai-message">
              <div class="message-avatar">
                <el-avatar :size="40" :src="getAIAvatar()" class="ai-avatar" />
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
import { ref, onMounted, onUnmounted, nextTick, watch, getCurrentInstance } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElEmpty, ElAvatar, ElDropdown, ElDropdownMenu, ElDropdownItem, ElButton, ElInput, ElDialog, ElForm, ElFormItem, ElIcon } from 'element-plus'
import { Plus, Setting, Edit, Delete, Service, Upload, Microphone, SwitchButton, User, Lock, ArrowDown } from '@element-plus/icons-vue'
import { chatApi } from '../api/chat'
import { userApi } from '../api/user'
import { useUserStore } from '../stores/userStore'
import { getAIAvatar } from '../utils/avatarUtils'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

// 路由实例和用户store
const router = useRouter()
const userStore = useUserStore()
const instance = getCurrentInstance() // 添加实例引用用于强制更新

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

// 用户头像 - 从store获取
const mediaStream = ref(null)
const processor = ref(null)
const recordingTimer = ref(null)
const chunkBuffer = ref(new Int16Array(0))
const realTimeTranscript = ref('')
const streamingSessionToken = ref('') // 流式识别会话令牌
let renderVersion = 0 // 渲染版本号，用于强制重新渲染
const streamingMessageIds = ref(new Set()) // 用于标记正在流式传输的消息ID

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
      .replace(/[ \t]+/g, ' ') // 合并多个空格和制表符
      .trim() // 去除首尾空白

  return cleaned
}

// 高质量语法高亮处理
const highlightCode = (code, lang) => {
  if (!code) return ''

  // 检测语言类型
  const detectedLang = detectLanguage(code, lang)
  const cleanLang = detectedLang.toLowerCase().replace(/[^a-z0-9]/g, '')

  // 应用语法高亮
  let highlightedCode = escapeHtml(code)

  // SQL特殊处理
  if (cleanLang === 'sql' || isSQLCode(code)) {
    highlightedCode = highlightSQL(code)
  }
  // JavaScript/TypeScript处理
  else if (cleanLang === 'javascript' || cleanLang === 'js' || cleanLang === 'typescript' || cleanLang === 'ts') {
    highlightedCode = highlightJavaScript(code)
  }
  // Python处理
  else if (cleanLang === 'python' || cleanLang === 'py') {
    highlightedCode = highlightPython(code)
  }
  // Java处理
  else if (cleanLang === 'java') {
    highlightedCode = highlightJava(code)
  }
  // CSS处理
  else if (cleanLang === 'css') {
    highlightedCode = highlightCSS(code)
  }
  // 通用处理
  else {
    highlightedCode = highlightGeneric(code)
  }

  return highlightedCode
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
  if (/\{[^}]*\}|#[a-zA-Z-]+\s*\{|\.color|background-color|font-size/.test(code)) return 'css'

  return 'text'
}

// 检测是否为SQL代码
const isSQLCode = (code) => {
  const sqlKeywords = ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE', 'ALTER', 'DROP', 'FROM', 'WHERE', 'GROUP BY', 'ORDER BY', 'HAVING', 'JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'INNER JOIN']
  const upperCode = code.toUpperCase()
  return sqlKeywords.some(keyword => upperCode.includes(keyword))
}

// SQL语法高亮
const highlightSQL = (code) => {
  const sqlPatterns = [
    // 关键字
    { pattern: /\b(SELECT|FROM|WHERE|GROUP BY|ORDER BY|HAVING|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TABLE|INDEX|VIEW|DATABASE|SCHEMA|PRIMARY|KEY|FOREIGN|REFERENCES|UNIQUE|NOT NULL|DEFAULT|AUTO_INCREMENT|VARCHAR|INT|BIGINT|TEXT|DATETIME|TIMESTAMP|BOOLEAN|CHAR|FLOAT|DOUBLE|DECIMAL|AS|ON|AND|OR|IN|EXISTS|BETWEEN|LIKE|IS|NULL|TRUE|FALSE|CASE|WHEN|THEN|ELSE|END|UNION|ALL|DISTINCT|COUNT|SUM|AVG|MIN|MAX|CAST|CONCAT|SUBSTRING|LENGTH|UPPER|LOWER|TRIM|COALESCE|IFNULL|ROUND|FLOOR|CEIL|MOD|ABS|POWER|SQRT)\b/gi, replacement: '<span class="sql-keyword">$1</span>' },

    // 函数名
    { pattern: /\b(COUNT|SUM|AVG|MIN|MAX|CAST|CONCAT|SUBSTRING|LENGTH|UPPER|LOWER|TRIM|COALESCE|IFNULL|ROUND|FLOOR|CEIL|MOD|ABS|POWER|SQRT|DATE_FORMAT|NOW|CURDATE|CURTIME)\s*\(/gi, replacement: '<span class="sql-function">$1</span>(' },

    // 字符串
    { pattern: /'([^']*)'/g, replacement: '<span class="sql-string">\'$1\'</span>' },
    { pattern: /"([^"]*)"/g, replacement: '<span class="sql-string">"$1"</span>' },

    // 数字
    { pattern: /\b(\d+)\b/g, replacement: '<span class="sql-number">$1</span>' },

    // 表名和列名
    { pattern: /\b([a-zA-Z_][a-zA-Z0-9_]*)\s*(?=\.|\s*(?:AS\s+|FROM|WHERE|GROUP|ORDER|HAVING|JOIN|$))/gi, replacement: '<span class="sql-identifier">$1</span>' },

    // 表别名
    { pattern: /\b([a-zA-Z_][a-zA-Z0-9_]*)\s+AS\s+/gi, replacement: '<span class="sql-identifier">$1</span> AS ' },

    // 操作符
    { pattern: /(=|!=|<>|<=|>=|<|>|\+|-|\*|\/|%) /g, replacement: ' <span class="sql-operator">$1</span> ' },
    { pattern: / (=|!=|<>|<=|>=|<|>|\+|-|\*|\/|%)$/g, replacement: ' <span class="sql-operator">$1</span>' },

    // 逗号
    { pattern: /,/g, replacement: '<span class="sql-comma">,</span>' },

    // 括号
    { pattern: /\(/g, replacement: '<span class="sql-bracket">(</span>' },
    { pattern: /\)/g, replacement: '<span class="sql-bracket">)</span>' }
  ]

  let highlightedCode = code
  sqlPatterns.forEach(({ pattern, replacement }) => {
    highlightedCode = highlightedCode.replace(pattern, replacement)
  })

  return highlightedCode
}

// JavaScript语法高亮
const highlightJavaScript = (code) => {
  const jsPatterns = [
    { pattern: /\b(function|const|let|var|return|if|else|for|while|do|switch|case|break|continue|try|catch|finally|throw|new|class|extends|import|export|default|async|await|yield|this|super)\b/g, replacement: '<span class="js-keyword">$1</span>' },
    { pattern: /'([^']*)'/g, replacement: '<span class="js-string">\'$1\'</span>' },
    { pattern: /"([^"]*)"/g, replacement: '<span class="js-string">"$1"</span>' },
    { pattern: /`([^`]*)`/g, replacement: '<span class="js-template">`$1`</span>' },
    { pattern: /\b(\d+)\b/g, replacement: '<span class="js-number">$1</span>' },
    { pattern: /\b(true|false|null|undefined)\b/g, replacement: '<span class="js-boolean">$1</span>' },
    { pattern: /\/\/(.*)$/gm, replacement: '<span class="js-comment">//$1</span>' },
    { pattern: /\/\*([\s\S]*?)\*\//g, replacement: '<span class="js-comment">/*$1*/</span>' }
  ]

  let highlightedCode = code
  jsPatterns.forEach(({ pattern, replacement }) => {
    highlightedCode = highlightedCode.replace(pattern, replacement)
  })

  return highlightedCode
}

// Python语法高亮
const highlightPython = (code) => {
  const pyPatterns = [
    { pattern: /\b(def|class|if|elif|else|for|while|try|except|finally|return|yield|import|from|as|global|nonlocal|lambda|with|pass|break|continue|and|or|not|in|is|None|True|False)\b/g, replacement: '<span class="py-keyword">$1</span>' },
    { pattern: /'([^']*)'/g, replacement: '<span class="py-string">\'$1\'</span>' },
    { pattern: /"([^"]*)"/g, replacement: '<span class="py-string">"$1"</span>' },
    { pattern: /\b(\d+)\b/g, replacement: '<span class="py-number">$1</span>' },
    { pattern: /#(.*)$/gm, replacement: '<span class="py-comment">#$1</span>' }
  ]

  let highlightedCode = code
  pyPatterns.forEach(({ pattern, replacement }) => {
    highlightedCode = highlightedCode.replace(pattern, replacement)
  })

  return highlightedCode
}

// Java语法高亮
const highlightJava = (code) => {
  const javaPatterns = [
    { pattern: /\b(public|private|protected|static|final|abstract|synchronized|volatile|transient|native|strictfp|class|interface|enum|extends|implements|import|package|void|boolean|byte|char|short|int|long|float|double|String|Object|System|out|print|println|return|if|else|for|while|do|try|catch|finally|throw|new|this|super)\b/g, replacement: '<span class="java-keyword">$1</span>' },
    { pattern: /@Override|@Deprecated|@SuppressWarnings/g, replacement: '<span class="java-annotation">$1</span>' },
    { pattern: /"([^"]*)"/g, replacement: '<span class="java-string">"$1"</span>' },
    { pattern: /'([^']*)'/g, replacement: '<span class="java-char">\'$1\'</span>' },
    { pattern: /\b(\d+)\b/g, replacement: '<span class="java-number">$1</span>' },
    { pattern: /\/\/(.*)$/gm, replacement: '<span class="java-comment">//$1</span>' },
    { pattern: /\/\*([\s\S]*?)\*\//g, replacement: '<span class="java-comment">/*$1*/</span>' }
  ]

  let highlightedCode = code
  javaPatterns.forEach(({ pattern, replacement }) => {
    highlightedCode = highlightedCode.replace(pattern, replacement)
  })

  return highlightedCode
}

// CSS语法高亮
const highlightCSS = (code) => {
  const cssPatterns = [
    { pattern: /([a-zA-Z-]+)\s*:/g, replacement: '<span class="css-property">$1</span>:' },
    { pattern: /#[a-zA-Z0-9_-]+/g, replacement: '<span class="css-id">$1</span>' },
    { pattern: /\.[a-zA-Z0-9_-]+/g, replacement: '<span class="css-class">$1</span>' },
    { pattern: /:([a-zA-Z-]+)(?=\s*[;{])/g, replacement: ':<span class="css-pseudo">$1</span>' },
    { pattern: /"([^"]*)"/g, replacement: '<span class="css-string">"$1"</span>' },
    { pattern: /'([^']*)'/g, replacement: '<span class="css-string">\'$1\'</span>' },
    { pattern: /\b(\d+\.?\d*(px|em|rem|%|vh|vw|pt|pc|in|cm|mm|ex|ch|vw|vh|vmin|vmax))\b/g, replacement: '<span class="css-number">$1$2</span>' },
    { pattern: /#[0-9a-fA-F]{3,6}\b/g, replacement: '<span class="css-color">$&</span>' },
    { pattern: /rgb\((\d+,\s*\d+,\s*\d+)\)/g, replacement: 'rgb(<span class="css-number">$1</span>, <span class="css-number">$2</span>, <span class="css-number">$3</span>)' },
    { pattern: /rgba\((\d+,\s*\d+,\s*\d+,\s*[\d.]+)\)/g, replacement: 'rgba(<span class="css-number">$1</span>, <span class="css-number">$2</span>, <span class="css-number">$3</span>, <span class="css-number">$4</span>)' },
    { pattern: /\/\*([\s\S]*?)\*\//g, replacement: '<span class="css-comment">/*$1*/</span>' }
  ]

  let highlightedCode = code
  cssPatterns.forEach(({ pattern, replacement }) => {
    highlightedCode = highlightedCode.replace(pattern, replacement)
  })

  return highlightedCode
}

// 通用语法高亮
const highlightGeneric = (code) => {
  const genericPatterns = [
    { pattern: /'([^']*)'/g, replacement: '<span class="code-string">\'$1\'</span>' },
    { pattern: /"([^"]*)"/g, replacement: '<span class="code-string">"$1"</span>' },
    { pattern: /\b(\d+)\b/g, replacement: '<span class="code-number">$1</span>' },
    { pattern: /\/\/(.*)$/gm, replacement: '<span class="code-comment">//$1</span>' },
    { pattern: /\/\*([\s\S]*?)\*\//g, replacement: '<span class="code-comment">/*$1*/</span>' }
  ]

  let highlightedCode = code
  genericPatterns.forEach(({ pattern, replacement }) => {
    highlightedCode = highlightedCode.replace(pattern, replacement)
  })

  return highlightedCode
}

// 改进的Markdown渲染方法
const renderMarkdown = (text) => {
  if (!text) return ''

  try {
    // 基本文本清理
    let processedText = text.trim()

    // 简单检查：如果文本很短且没有明显Markdown特征，直接返回纯文本
    if (processedText.length < 50 &&
        !processedText.includes('```') &&
        !processedText.includes('#') &&
        !processedText.includes('**') &&
        !processedText.includes('* ') &&
        !processedText.includes('|')) {
      return `<div class="plain-text">${escapeHtml(processedText)}</div>`
    }

    // 预处理文本
    processedText = preprocessMarkdown(processedText)

    marked.setOptions({
      breaks: true,
      gfm: true,
      headerIds: false, // 禁用header id避免冲突
      mangle: false,  // 禁用email mangling
      sanitize: false, // 允许HTML
      highlight: (code, lang) => {
        try {
          const highlightedCode = highlightCode(code, lang)
          const detectedLang = detectLanguage(code, lang)
          const cleanLang = detectedLang.toLowerCase().replace(/[^a-z0-9]/g, '')
          const displayLang = detectedLang || 'text'
          const escapedCode = escapeHtml(code).replace(/"/g, '&quot;')

          return `<div class="code-block-wrapper" data-language="${cleanLang}" data-code="${escapedCode}">
            <div class="code-block-header">
              <div class="code-language">
                <span class="code-language-dot"></span>
                ${displayLang}
              </div>
              <div class="code-actions">
                <button class="copy-btn" onclick="copyCode(this, '${escapedCode}')">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                    <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"></path>
                  </svg>
                  <span>复制</span>
                </button>
              </div>
            </div>
            <pre class="code-block ${cleanLang ? `language-${cleanLang}` : ''}"><code class="code-content">${highlightedCode}</code></pre>
          </div>`
        } catch (e) {
          return `<pre><code>${escapeHtml(code)}</code></pre>`
        }
      }
    })

    const result = marked.parse(processedText)
    return result
  } catch (error) {
    console.error('Markdown rendering error:', error)
    // 如果Markdown解析失败，返回安全的HTML文本
    return `<div class="plain-text">${escapeHtml(text)}</div>`
  }
}

// 预处理Markdown文本，修复流式传输问题
const preprocessMarkdown = (text) => {
  const backtick3 = '```'
  const pipe = '|'

  return text
    // 修复代码块格式
    .replace(/```(\s*[a-zA-Z0-9]+)?/g, (match, lang) => {
      const cleanLang = lang ? lang.trim() : ''
      return cleanLang ? `${backtick3}${cleanLang}\n` : `${backtick3}\n`
    })
    // 修复SQL关键字和变量之间的空格问题
    .replace(/(\w+)(\n+[A-Z_]+)/g, '$1 $2')
    // 修复变量名和运算符之间的空格
    .replace(/([a-zA-Z_])([<>=!])/g, '$1 $2')
    .replace(/([<>=!])([a-zA-Z_])/g, '$1 $2')
    // 修复数字和关键字之间的空格
    .replace(/(\d+)([A-Za-z_]+)/g, '$1 $2')
    // 修复SQL中的特殊字符问题
    .replace(/COALESCE\(SUM\(CASE WHEN([^)]+)THEN(\d+) ELSE(\d+) END\), (\d+)\)/g,
      'COALESCE(SUM(CASE WHEN$1THEN $2 ELSE $3 END), $4)')
    // 修复表格格式，确保表格分隔符存在
    .replace(/\|([^|]+)\|/g, (match, content, offset, string) => {
      const nextLineIndex = string.indexOf('\n', offset)
      if (nextLineIndex === -1) return match

      const currentLine = string.substring(offset, nextLineIndex)
      const hasSeparator = currentLine.includes('---') || currentLine.includes('===')

      // 如果当前行是表头且下一行没有分隔符，添加分隔符
      if (!hasSeparator && offset > 0 && string[offset - 1] === '\n') {
        const columnCount = (match.match(/\|/g) || []).length - 1
        const separator = '\n' + Array(columnCount).fill('---').join('|') + '|\n'
        return match + separator
      }

      return match
    })
    // 修复标题格式
    .replace(/^(#{1,6})\s*/gm, '$1 ')
    // 修复多余的空格和换行
    .replace(/\n{3,}/g, '\n\n')
    .replace(/[ \t]+$/gm, '')
}

// 增强的文本渲染，处理基本的格式
const renderEnhancedText = (text) => {
  return text
    // HTML转义
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    // 保留换行
    .replace(/\n/g, '<br>')
    // 处理粗体（已完成的）
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    // 处理斜体（已完成的）
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    // 处理行内代码（已完成的）
    .replace(/`([^`]+)`/g, '<code>$1</code>')
}

// HTML转义函数
const escapeHtml = (text) => {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

// 添加一个观察器来监视消息变化
watch(messages, (newMessages, oldMessages) => {
  console.log('Messages array changed, length:', newMessages.length)
  // 深度监听消息变化，确保渲染更新
  nextTick(() => {
    console.log('Watch nextTick executed')
  })
}, { deep: true, immediate: false })

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
  await loadMessages(session.id)
}

// 加载消息历史
const loadMessages = async (sessionId) => {
  try {
    // 清理之前的流式标记
    streamingMessageIds.value.clear()

    const response = await chatApi.getMessages(sessionId)
    if (response?.code === 200) {
      messages.value = (response.data || []).map(msg => {
        // 清理AI消息内容中的多余字符
        if (msg.messageType === 1 && msg.content) {
          msg.content = cleanAIResponse(msg.content)
          msg.renderVersion = ++renderVersion // 关键添加
        }
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

  // 创建AI消息对象，提前定义以便在catch块中访问
  const aiMessageObj = {
    id: Date.now() + 1,
    content: '',
    messageType: 1,
    createdAt: new Date().toISOString(),
    renderVersion: ++renderVersion // 添加版本号字段
  }

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
      // 移除所有的data:前缀，包括全局匹配
      chunk = chunk.replace(/^data:\s*/gm, '')
      // 保留换行符以支持Markdown格式，只清理多余的空白字符
      const cleanChunk = chunk.replace(/\r+/g, '').replace(/[ \t]+/g, ' ').trim()

      if (cleanChunk) {
        aiMessageText += cleanChunk
      }

      const aiMsgIndex = messages.value.findIndex(msg => msg.id === aiMessageObj.id)
      if (aiMsgIndex > -1) {
        // 在流式传输过程中也更新内容，但保持流式标记
        messages.value[aiMsgIndex].content = aiMessageText
      }

      nextTick(() => {
        if (messagesContainer.value) {
          messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
        }
      })
    }

    // 流式数据读取完成后，处理最终内容
    const finalMsgIndex = messages.value.findIndex(msg => msg.id === aiMessageObj.id)
    if (finalMsgIndex > -1) {
      // 清理最终收集到的完整内容
      const cleanedContent = cleanAIResponse(aiMessageText)

      console.log('流式完成，内容长度:', cleanedContent.length)

      // 移除流式传输标记，切换到Markdown渲染
      streamingMessageIds.value.delete(aiMessageObj.id)

      // 最终更新内容（触发Markdown渲染）
      messages.value[finalMsgIndex].content = cleanedContent
      messages.value[finalMsgIndex].renderVersion = ++renderVersion

      // 等待Vue更新完成
      await nextTick()
    }

    await loadSessions()
  } catch (error) {
    console.error('Send message error:', error)
    isTyping.value = false

    // 确保清理流式标记
    streamingMessageIds.value.delete(aiMessageObj.id)

    handleApiError(error, error.message || '发送消息失败')
    const index = messages.value.findIndex(msg => msg.id === userMessageObj.id)
    if (index > -1) messages.value.splice(index, 1)

    // 也清理AI消息
    const aiIndex = messages.value.findIndex(msg => msg.id === aiMessageObj.id)
    if (aiIndex > -1) messages.value.splice(aiIndex, 1)
  } finally {
    isTyping.value = false
    // 确保在所有情况下都清理流式状态
    streamingMessageIds.value.delete(aiMessageObj.id)
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
    case 'profile':
      router.push('/profile')
      break
    case 'password':
      router.push('/password')
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
    // 1. 启动流式识别会话
    const sessionId = currentSessionId.value || 'session_' + Date.now()
    const response = await fetch('http://localhost:8080/user/streaming/start', {
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
    console.log('流式识别会话已启动，令牌：', streamingSessionToken.value)

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
      await sendStreamingMediaRecorderChunk()
    }
  }, 1000)

  recorder.start(1000) // 每1秒触发一次dataavailable事件
}

// 停止录音
const stopRecording = async () => {
  if (!isRecording.value) return

  console.log('停止录音...')

  // 保存当前的转录文本，避免后续被覆盖
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
      console.log('发送停止请求到后端...')

      // 停止流式识别会话并获取最终结果
      if (streamingSessionToken.value) {
        const response = await fetch('http://localhost:8080/user/streaming/stop', {
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
              if (userMessage.value && !userMessage.value.endsWith(' ')) {
                userMessage.value += ' '
              }
              userMessage.value += finalTranscript
              ElMessage.success(`语音识别完成: ${finalTranscript}`)
            } else {
              // 如果最终结果为空，使用保存的实时转录结果
              if (currentTranscript) {
                if (userMessage.value && !userMessage.value.endsWith(' ')) {
                  userMessage.value += ' '
                }
                userMessage.value += currentTranscript
                ElMessage.success(`语音识别完成: ${currentTranscript}`)
              }
            }
          } else {
            console.warn('停止语音识别会话失败:', response.status)
            // 失败时也使用保存的实时转录结果
            if (currentTranscript) {
              if (userMessage.value && !userMessage.value.endsWith(' ')) {
                userMessage.value += ' '
              }
              userMessage.value += currentTranscript
              ElMessage.success(`语音识别完成: ${currentTranscript}`)
            }
          }
        } else {
          console.warn('停止语音识别会话失败:', response.status)
          // 失败时也使用保存的实时转录结果
          if (currentTranscript) {
            if (userMessage.value && !userMessage.value.endsWith(' ')) {
              userMessage.value += ' '
            }
            userMessage.value += currentTranscript
            ElMessage.success(`语音识别完成: ${currentTranscript}`)
          }
        }
      } else {
        // 没有会话令牌时使用保存的实时转录结果
        if (currentTranscript) {
          if (userMessage.value && !userMessage.value.endsWith(' ')) {
            userMessage.value += ' '
          }
          userMessage.value += currentTranscript
          ElMessage.success(`语音识别完成: ${currentTranscript}`)
        }
      }

    } catch (error) {
      console.error('停止录音时发生错误:', error)
      // 发生错误时仍然尝试使用保存的实时转录结果
      if (currentTranscript) {
        if (userMessage.value && !userMessage.value.endsWith(' ')) {
          userMessage.value += ' '
        }
        userMessage.value += currentTranscript
        ElMessage.success(`语音识别完成: ${currentTranscript}`)
      }
    }

    // 最终清理状态
    realTimeTranscript.value = ''
    streamingSessionToken.value = ''

    // 清理录音资源
    cleanupRecording()

    console.log('录音处理完成')
  }, 1500) // 延迟1.5秒发送停止请求
}

// 强制停止录音（紧急情况使用）
const forceStopRecording = async () => {
  console.log('强制停止录音...')

  // 立即停止UI状态
  isRecording.value = false

  // 停止定时器
  if (recordingTimer.value) {
    clearInterval(recordingTimer.value)
    recordingTimer.value = null
  }

  // 保存当前转录文本
  const currentTranscript = realTimeTranscript.value.trim()

  try {
    // 立即强制停止后端流式识别会话
    if (streamingSessionToken.value) {
      await fetch('http://localhost:8080/user/streaming/forceStop', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ sessionToken: streamingSessionToken.value })
      })
    }
  } catch (error) {
    console.warn('强制停止后端会话失败:', error)
  }

  // 如果有转录文本，添加到输入框
  if (currentTranscript) {
    if (userMessage.value && !userMessage.value.endsWith(' ')) {
      userMessage.value += ' '
    }
    userMessage.value += currentTranscript
    ElMessage.success(`语音识别已保存: ${currentTranscript}`)
  }

  // 立即清理所有资源
  cleanupRecording()
  realTimeTranscript.value = ''
  streamingSessionToken.value = ''

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

    const response = await fetch('http://localhost:8080/user/streaming/audio', {
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
        realTimeTranscript.value = result.data.trim()
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

    const response = await fetch('http://localhost:8080/user/streaming/audio', {
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
        realTimeTranscript.value = result.data.trim()
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
  try {
    // 解码HTML实体
    const decodedCode = code
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
      <span>已复制</span>
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
    textArea.value = code
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&amp;/g, '&')
      .replace(/&quot;/g, '"')
      .replace(/&#39;/g, "'")
      .replace(/&#x2F;/g, '/')

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
        <span>已复制</span>
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

// 挂载时加载
onMounted(() => {
  if (checkToken()) {
    loadSessions()
    loadUserInfo()
  }
})

// 组件卸载时清理资源
onUnmounted(() => {
  cleanupRecording()
  console.log('Component unmounted, cleanup completed')
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
  justify-content: space-between;
  height: 64px; /* 固定高度 */
  flex-shrink: 0; /* 禁止收缩 */
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.chat-header-left {
  flex: 1;
}

.chat-header-right {
  display: flex;
  align-items: center;
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
  background-color: #f3f4f6;
}

.username {
  margin: 0 8px;
  color: #374151;
  font-size: 14px;
  font-weight: 500;
}

.user-dropdown .el-icon--right {
  color: #9ca3af;
  font-size: 12px;
  transition: transform 0.2s ease;
}

.user-dropdown:hover .el-icon--right {
  transform: rotate(180deg);
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
  border: 1px solid #e5e7eb;
}

.ai-avatar {
  border: 1px solid #e5e7eb;
}

/* 消息气泡 - 现代化样式 */
.message-content {
  max-width: 70%;
  padding: 16px 20px;
  position: relative;
  line-height: 1.6;
  font-size: 15px;
  border-radius: 18px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  animation: messageSlideIn 0.3s ease-out;
  /* backdrop-filter: blur(10px); 临时禁用以测试文本选择问题 */
}

@keyframes messageSlideIn {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.message-content:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.12);
}

.user-message .message-content {
  margin-right: 16px;
  margin-left: auto;
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  color: white;
  border-radius: 18px 18px 4px 18px;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.user-message .message-content::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(255,255,255,0.1) 0%, transparent 100%);
  border-radius: inherit;
  pointer-events: none; /* 允许文本选择穿透伪元素 */
}

.ai-message .message-content {
  margin-left: 16px;
  margin-right: auto;
  background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
  color: #1f2937;
  border-radius: 18px 18px 18px 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(229, 231, 235, 0.8);
}

.ai-message .message-content::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.02) 0%, transparent 100%);
  border-radius: inherit;
  pointer-events: none; /* 允许文本选择穿透伪元素 */
}

/* 消息时间样式优化 */
.message-time {
  font-size: 11px;
  opacity: 0.6;
  margin-top: 8px;
  text-align: right;
  color: #6b7280;
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

/* 代码块容器 */
.code-block-wrapper {
  background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
  border-radius: 12px;
  margin: 16px 0;
  overflow: hidden;
  position: relative;
  border: 1px solid #475569;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.markdown-content pre {
  background: transparent;
  border-radius: 0;
  padding: 0;
  margin: 0;
  overflow: hidden;
  position: relative;
  border: none;
  box-shadow: none;
}

/* 代码块头部 */
.code-block-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: rgba(0, 0, 0, 0.2);
  border-bottom: 1px solid #475569;
  min-height: 44px;
}

.code-language {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.code-language-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.4);
}

.code-actions {
  display: flex;
  gap: 8px;
}

.copy-btn {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #94a3b8;
  padding: 6px 12px;
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
  border-color: rgba(255, 255, 255, 0.2);
  color: #e2e8f0;
  transform: translateY(-1px);
}

.copy-btn.copied {
  background: #10b981;
  border-color: #10b981;
  color: white;
}

/* 代码内容区域 */
.markdown-content pre code {
  display: block;
  padding: 20px;
  background: transparent;
  color: #e2e8f0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  overflow-x: auto;
  border-radius: 0;
  white-space: pre;
  word-wrap: normal;
}

/* 内联代码 */
.markdown-content :not(pre) > code {
  background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
  border: 1px solid #cbd5e1;
  padding: 3px 6px;
  border-radius: 6px;
  font-size: 13px;
  color: #1e293b;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-weight: 500;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

/* SQL语法高亮 */
.sql-keyword {
  color: #f472b6;
  font-weight: 600;
  text-shadow: 0 0 8px rgba(244, 114, 182, 0.3);
}

.sql-function {
  color: #60a5fa;
  font-weight: 500;
}

.sql-string {
  color: #34d399;
  font-style: italic;
}

.sql-number {
  color: #fbbf24;
  font-weight: 500;
}

.sql-operator {
  color: #a78bfa;
  font-weight: 600;
}

.sql-comment {
  color: #64748b;
  font-style: italic;
  opacity: 0.8;
}

.sql-type {
  color: #fb923c;
  font-weight: 500;
}

/* JavaScript语法高亮 */
.js-keyword {
  color: #c084fc;
  font-weight: 600;
  text-shadow: 0 0 8px rgba(192, 132, 252, 0.3);
}

.js-function {
  color: #60a5fa;
  font-weight: 500;
}

.js-string {
  color: #34d399;
  font-style: italic;
}

.js-number {
  color: #fbbf24;
  font-weight: 500;
}

.js-comment {
  color: #64748b;
  font-style: italic;
  opacity: 0.8;
}

.js-regexp {
  color: #f87171;
  font-style: italic;
}

/* Python语法高亮 */
.python-keyword {
  color: #c084fc;
  font-weight: 600;
  text-shadow: 0 0 8px rgba(192, 132, 252, 0.3);
}

.python-function {
  color: #60a5fa;
  font-weight: 500;
}

.python-string {
  color: #34d399;
  font-style: italic;
}

.python-number {
  color: #fbbf24;
  font-weight: 500;
}

.python-comment {
  color: #64748b;
  font-style: italic;
  opacity: 0.8;
}

.python-builtin {
  color: #fb923c;
  font-weight: 500;
}

/* Java语法高亮 */
.java-keyword {
  color: #c084fc;
  font-weight: 600;
  text-shadow: 0 0 8px rgba(192, 132, 252, 0.3);
}

.java-annotation {
  color: #f472b6;
  font-weight: 500;
}

.java-string {
  color: #34d399;
  font-style: italic;
}

.java-number {
  color: #fbbf24;
  font-weight: 500;
}

.java-comment {
  color: #64748b;
  font-style: italic;
  opacity: 0.8;
}

.java-type {
  color: #60a5fa;
  font-weight: 500;
}

/* CSS语法高亮 */
.css-selector {
  color: #f472b6;
  font-weight: 600;
}

.css-property {
  color: #60a5fa;
  font-weight: 500;
}

.css-value {
  color: #34d399;
  font-style: italic;
}

.css-unit {
  color: #fbbf24;
  font-weight: 500;
}

.css-important {
  color: #f87171;
  font-weight: 700;
  text-transform: uppercase;
}

/* 通用语法高亮 */
.syntax-bracket {
  color: #94a3b8;
  font-weight: 600;
}

.syntax-punctuation {
  color: #64748b;
}

.syntax-variable {
  color: #38bdf8;
  font-weight: 500;
}

.syntax-class {
  color: #fb923c;
  font-weight: 500;
}

.syntax-method {
  color: #60a5fa;
  font-weight: 500;
}

/* 代码块动画效果 */
.code-block-wrapper {
  animation: codeBlockFadeIn 0.4s ease-out;
}

@keyframes codeBlockFadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 代码块内容增强效果 */
.markdown-content pre code {
  position: relative;
  z-index: 1;
}

.markdown-content pre code::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.02) 0%, transparent 100%);
  z-index: -1;
  pointer-events: none;
}

/* 代码块头部增强 */
.code-block-header {
  backdrop-filter: blur(8px);
}

/* 语言指示点动画 */
.code-language-dot {
  animation: pulseDot 2s ease-in-out infinite;
}

@keyframes pulseDot {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.1);
  }
}

/* 复制按钮动画 */
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

/* 代码块滚动条美化 */
.markdown-content pre code::-webkit-scrollbar {
  height: 8px;
}

.markdown-content pre code::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 4px;
}

.markdown-content pre code::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 4px;
}

.markdown-content pre code::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.3);
}

/* 代码块hover效果 */
.code-block-wrapper:hover {
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.4);
  transform: translateY(-2px);
  transition: all 0.2s ease;
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

/* 流式文本样式 */
.streaming-text {
  color: #374151;
  white-space: pre-wrap;
  word-wrap: break-word;
  line-height: 1.6;
  font-family: inherit;
  background-color: #f8fafc;
  border-radius: 8px;
  padding: 12px 16px;
  border-left: 3px solid #3b82f6;
}

/* ===== 现代化Markdown样式 ===== */

/* 基础文本样式 */
.markdown-content {
  line-height: 1.7;
  color: #374151;
  font-size: 15px;
  word-wrap: break-word;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Helvetica Neue', Arial, sans-serif;
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
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
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
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 2px;
}

.markdown-content h2 {
  font-size: 22px;
  color: #1f2937;
  padding-bottom: 8px;
  border-bottom: 2px solid #e5e7eb;
  position: relative;
}

.markdown-content h2::before {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 40px;
  height: 2px;
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  border-radius: 1px;
}

.markdown-content h3 {
  font-size: 18px;
  color: #374151;
  display: flex;
  align-items: center;
}

.markdown-content h3::before {
  content: '▸';
  color: #3b82f6;
  margin-right: 8px;
  font-size: 16px;
}

.markdown-content h4 {
  font-size: 16px;
  color: #4b5563;
}

.markdown-content h5 {
  font-size: 15px;
  color: #6b7280;
}

.markdown-content h6 {
  font-size: 14px;
  color: #9ca3af;
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
.markdown-content pre code[class*="language-sql"],
.markdown-content pre code:has("SELECT"),
.markdown-content pre code:has("INSERT"),
.markdown-content pre code:has("UPDATE"),
.markdown-content pre code:has("DELETE") {
  color: #86efac;
}

/* SQL关键字高亮 */
.markdown-content pre code:has("SELECT") span,
.markdown-content pre code:has("INSERT") span,
.markdown-content pre code:has("UPDATE") span,
.markdown-content pre code:has("DELETE") span {
  color: #fbbf24;
  font-weight: bold;
}

/* 现代化表格样式 */
.markdown-content table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  margin: 24px 0;
  font-size: 14px;
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
  border: 1px solid #e5e7eb;
}

.markdown-content th,
.markdown-content td {
  padding: 16px;
  text-align: left;
  line-height: 1.5;
  border-bottom: 1px solid #f3f4f6;
}

.markdown-content th {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  font-weight: 700;
  color: #1f2937;
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 2px solid #e5e7eb;
}

.markdown-content tr:last-child td {
  border-bottom: none;
}

.markdown-content tr:nth-child(even) {
  background-color: #fafbfc;
}

.markdown-content tr:hover {
  background-color: #f8fafc;
  transition: background-color 0.2s ease;
}

/* 现代化列表样式 */
.markdown-content ul,
.markdown-content ol {
  margin: 16px 0;
  padding-left: 0;
}

.markdown-content ul {
  list-style: none;
}

.markdown-content ul li {
  position: relative;
  padding: 8px 0 8px 28px;
  line-height: 1.7;
  margin: 4px 0;
}

.markdown-content ul li::before {
  content: '•';
  position: absolute;
  left: 8px;
  color: #3b82f6;
  font-size: 18px;
  font-weight: bold;
  top: 8px;
}

.markdown-content ol {
  padding-left: 24px;
}

.markdown-content ol li {
  padding: 8px 0 8px 8px;
  line-height: 1.7;
  margin: 4px 0;
  position: relative;
}

.markdown-content ol li::marker {
  color: #3b82f6;
  font-weight: 600;
}

/* 嵌套列表 */
.markdown-content ul ul,
.markdown-content ol ol,
.markdown-content ul ol,
.markdown-content ol ul {
  margin: 8px 0;
}

.markdown-content ul ul li::before {
  color: #10b981;
  font-size: 14px;
}

/* 现代化引用块样式 */
.markdown-content blockquote {
  margin: 24px 0;
  padding: 20px 24px;
  border-left: 5px solid;
  border-image: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%) 1;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  color: #1e40af;
  font-style: normal;
  border-radius: 0 12px 12px 0;
  box-shadow: 0 4px 6px -1px rgba(59, 130, 246, 0.1);
  position: relative;
}

.markdown-content blockquote p {
  margin: 0;
}

.markdown-content blockquote::before {
  content: '"';
  position: absolute;
  top: 8px;
  left: 12px;
  font-size: 48px;
  color: #3b82f6;
  opacity: 0.2;
  font-family: Georgia, serif;
}

/* 链接样式 */
.markdown-content a {
  color: #3b82f6;
  text-decoration: none;
  font-weight: 500;
  border-bottom: 1px solid transparent;
  transition: all 0.2s ease;
  position: relative;
}

.markdown-content a:hover {
  color: #1d4ed8;
  border-bottom-color: #1d4ed8;
}

.markdown-content a::after {
  content: '↗';
  font-size: 12px;
  margin-left: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.markdown-content a:hover::after {
  opacity: 1;
}

/* 粗体和斜体 */
.markdown-content strong {
  color: #1f2937;
  font-weight: 700;
}

.markdown-content em {
  color: #4b5563;
  font-style: italic;
}

/* 水平分割线 */
.markdown-content hr {
  border: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, #e5e7eb, transparent);
  margin: 32px 0;
  border-radius: 2px;
}

/* 纯文本样式 */
.plain-text {
  white-space: pre-wrap;
  word-wrap: break-word;
  line-height: 1.6;
  color: #374151;
  margin: 16px 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Helvetica Neue', Arial, sans-serif;
}

/* 防止文本选中 - 保持原有行为 */
.markdown-content * {
  -webkit-user-select: text;
  -moz-user-select: text;
  -ms-user-select: text;
  user-select: text;
}

/* 段落间距优化 */
.markdown-content p:first-child {
  margin-top: 0;
}

.markdown-content p:last-child {
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