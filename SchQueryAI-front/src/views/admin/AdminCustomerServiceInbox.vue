<template>
  <div class="admin-page admin-customer-inbox">
    <div class="page-header">
      <div class="header-left">
        <div>
          <h2>客服消息</h2>
          <p class="sub">先在此页预览/筛选会话，再进入对话页处理与回复</p>
        </div>
        <el-tag v-if="pendingCount > 0" type="danger" effect="dark" size="small">待处理 {{ pendingCount }}</el-tag>
      </div>

      <div class="header-actions">
        <el-input
          v-model="keyword"
          placeholder="搜索用户/消息"
          clearable
          style="width: 220px"
          @keyup.enter="reload"
          @clear="reload"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 140px" @change="noop">
          <el-option label="全部" value="all" />
          <el-option label="进行中" value="active" />
          <el-option label="已完成" value="completed" />
          <el-option label="未读" value="unread" />
        </el-select>
        <el-button type="primary" :disabled="!currentSession" @click="openChat()">进入对话</el-button>
        <el-button :loading="loading" @click="reload">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <el-row :gutter="12" class="stats-row">
      <el-col :xs="24" :sm="6">
        <el-card shadow="never">
          <el-statistic title="会话总数" :value="totalSessions" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="6">
        <el-card shadow="never">
          <el-statistic title="进行中" :value="activeSessions" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="6">
        <el-card shadow="never">
          <el-statistic title="未读消息" :value="unreadTotal" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="6">
        <el-card shadow="never">
          <el-statistic title="待处理会话" :value="pendingCount" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="workspace-row split-panels">
      <el-col :xs="24" :lg="10">
        <el-card class="table-card split-panel" shadow="never" v-loading="loading">
          <el-table
            :data="filteredSessions"
            stripe
            highlight-current-row
            :row-class-name="rowClassName"
            @row-click="selectSession"
          >
            <el-table-column prop="userName" label="用户" min-width="120" />
            <el-table-column prop="topic" label="主题" width="120">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ formatTopic(row.topic) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'completed' ? 'success' : 'warning'">
                  {{ row.status === 'completed' ? '已完成' : '处理中' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="unreadCount" label="未读" width="90" align="center">
              <template #default="{ row }">
                <el-badge v-if="(row.unreadCount || 0) > 0" :value="row.unreadCount" :max="99" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="lastMessage" label="最后消息" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="120" align="center" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click.stop="openChat(row)">进入</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="14">
        <el-card class="preview-card split-panel" shadow="never">
          <template #header>
            <div class="preview-header">
              <div class="preview-title">
                <span class="name">{{ currentSession?.userName || '会话预览' }}</span>
                <el-tag v-if="currentSession" size="small" type="info">{{ formatTopic(currentSession.topic) }}</el-tag>
                <el-tag
                  v-if="currentSession"
                  size="small"
                  :type="currentSession.status === 'completed' ? 'success' : 'warning'"
                >
                  {{ currentSession.status === 'completed' ? '已完成' : '处理中' }}
                </el-tag>
              </div>
              <div class="preview-actions">
                <el-button :disabled="!currentSession" type="primary" @click="openChat()">进入对话</el-button>
              </div>
            </div>
          </template>

          <div v-if="!currentSession" class="empty-state">
            <el-empty description="选择左侧会话进行预览" :image-size="120" />
          </div>

          <div v-else class="preview-body">
            <div ref="previewContainer" class="preview-messages">
              <div
                v-for="msg in previewMessages"
                :key="msg.id"
                class="preview-msg"
                :class="{ 'is-admin': msg.senderType === 2, 'is-user': msg.senderType === 1 }"
              >
                <div class="bubble">
                  <div class="meta">
                    <span class="sender">{{ msg.senderType === 1 ? currentSession.userName : '管理员' }}</span>
                    <span class="time">{{ formatTime(msg.createTime) }}</span>
                  </div>
                  <div class="text">{{ msg.messageContent }}</div>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { useUserStore } from '../../stores/userStore'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const sessions = ref([])
const currentSession = ref(null)
const keyword = ref('')
const statusFilter = ref('all')
const previewContainer = ref(null)

const totalSessions = computed(() => sessions.value.length)
const activeSessions = computed(() => sessions.value.filter((s) => s.status !== 'completed').length)
const unreadTotal = computed(() => sessions.value.reduce((sum, s) => sum + (s.unreadCount || 0), 0))
const pendingCount = computed(() => sessions.value.filter((s) => (s.unreadCount || 0) > 0).length)

const filteredSessions = computed(() => {
  const kw = keyword.value?.trim().toLowerCase()
  return sessions.value.filter((s) => {
    const matchesKeyword =
      !kw ||
      String(s.userName || '').toLowerCase().includes(kw) ||
      String(s.lastMessage || '').toLowerCase().includes(kw)

    let matchesStatus = true
    switch (statusFilter.value) {
      case 'active':
        matchesStatus = s.status !== 'completed'
        break
      case 'completed':
        matchesStatus = s.status === 'completed'
        break
      case 'unread':
        matchesStatus = (s.unreadCount || 0) > 0
        break
      default:
        matchesStatus = true
    }

    return matchesKeyword && matchesStatus
  })
})

const previewMessages = computed(() => {
  const msgs = currentSession.value?.messages || []
  const sorted = [...msgs].sort((a, b) => new Date(a.createTime) - new Date(b.createTime))
  return sorted.slice(Math.max(0, sorted.length - 30))
})

const formatTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  if (Number.isNaN(date.getTime())) return String(time)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const formatTopic = (topic) => {
  const topicMap = {
    general: '一般问题',
    account: '账户问题',
    feature: '功能问题',
    tech: '技术支持',
    other: '其他问题'
  }
  return topicMap[topic] || topic || '-'
}

const rowClassName = ({ row }) => {
  if (currentSession.value && row && row.userId == currentSession.value.userId) return 'is-current'
  return ''
}

const markUserMessagesRead = async (session) => {
  if (!session?.userId) return
  const token = localStorage.getItem('token')
  await fetch(`/api/customer-service/mark-read-by-user?userId=${session.userId}&senderType=1`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  })

  if (session.messages) {
    session.messages.forEach((msg) => {
      if (msg.senderType === 1) msg.readStatus = 1
    })
  }
}

const selectSession = async (session) => {
  currentSession.value = session
  try {
    if ((session.unreadCount || 0) > 0) {
      await markUserMessagesRead(session)
      session.unreadCount = 0
      window.dispatchEvent(new CustomEvent('admin-marked-read', { detail: { userId: session.userId } }))
    }
  } catch (e) {
    // 忽略标记失败，不阻塞预览
  } finally {
    nextTick(() => {
      if (previewContainer.value) {
        previewContainer.value.scrollTop = previewContainer.value.scrollHeight
      }
    })
  }
}

const openChat = (session) => {
  const s = session || currentSession.value
  if (!s?.userId) return
  router.push(`/admin/customer-service/chat/${s.userId}`)
}

const loadSessions = async (preferredUserId) => {
  try {
    loading.value = true
    const token = localStorage.getItem('token')
    const response = await fetch('/api/customer-service/pending-sessions', {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })

    if (!response.ok) return
    const result = await response.json()
    if (result.code !== 200) return

    sessions.value = result.data || []

    if (preferredUserId != null) {
      const target = sessions.value.find((s) => s.userId == preferredUserId)
      if (target) {
        await selectSession(target)
        return
      }
    }

    if (currentSession.value) {
      const same = sessions.value.find((s) => s.userId == currentSession.value.userId)
      if (same) currentSession.value = same
    }
  } catch (error) {
    console.error('加载会话失败:', error)
    ElMessage.error('加载会话失败')
  } finally {
    loading.value = false
  }
}

const reload = async () => {
  await loadSessions()
  ElMessage.success('已刷新')
}

const noop = () => {}

const handleRefreshEvent = async (event) => {
  await loadSessions(event?.detail?.userId)
}

const handleNewMessageEvent = async (event) => {
  const { userId, message } = event?.detail || {}
  if (!userId || !message) return

  const sessionInList = sessions.value.find((s) => s.userId == userId)
  if (sessionInList) {
    sessionInList.lastMessage = message.messageContent
    sessionInList.topic = message.topic
    if (currentSession.value && currentSession.value.userId == userId) {
      if (!currentSession.value.messages) currentSession.value.messages = []
      currentSession.value.messages.push(message)
      // 预览中视为已读：不累加红点
      nextTick(() => {
        if (previewContainer.value) previewContainer.value.scrollTop = previewContainer.value.scrollHeight
      })
    } else {
      sessionInList.unreadCount = (sessionInList.unreadCount || 0) + 1
    }
  } else {
    // 列表中不存在该用户会话，刷新一次
    loadSessions()
  }
}

const handleUserMarkedAdminRead = (event) => {
  const { userId } = event?.detail || {}
  if (!userId) return
  const sessionInList = sessions.value.find((s) => s.userId == userId)
  if (sessionInList && sessionInList.unreadCount > 0) {
    sessionInList.unreadCount--
    if (currentSession.value && currentSession.value.userId == userId) {
      currentSession.value.unreadCount = sessionInList.unreadCount
    }
  }
}

onMounted(async () => {
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }

  if (!userStore.userInfo || !userStore.userInfo.id) {
    await userStore.fetchUserInfo()
  }

  await loadSessions()

  window.addEventListener('refresh-customer-sessions', handleRefreshEvent)
  window.addEventListener('new-customer-message', handleNewMessageEvent)
  window.addEventListener('user-marked-admin-read', handleUserMarkedAdminRead)
})

onUnmounted(() => {
  window.removeEventListener('refresh-customer-sessions', handleRefreshEvent)
  window.removeEventListener('new-customer-message', handleNewMessageEvent)
  window.removeEventListener('user-marked-admin-read', handleUserMarkedAdminRead)
})
</script>

<style scoped>
.stats-row {
  margin-bottom: 16px;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.preview-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.preview-title .name {
  font-weight: 700;
  color: var(--admin-text, #111827);
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-state {
  padding: 12px 0;
}

.preview-card.split-panel :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.preview-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.preview-messages {
  height: 100%;
  overflow-y: auto;
  padding: 4px 0;
}

.preview-msg {
  display: flex;
  margin-bottom: 10px;
}

.preview-msg.is-user {
  justify-content: flex-start;
}

.preview-msg.is-admin {
  justify-content: flex-end;
}

.bubble {
  max-width: 86%;
  border-radius: 10px;
  padding: 10px 12px;
  border: 1px solid var(--admin-border, #eef2f7);
  background: var(--admin-card-bg, #fff);
}

.preview-msg.is-admin .bubble {
  background: rgba(59, 130, 246, 0.08);
}

.meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  color: var(--admin-muted, #6b7280);
  font-size: 12px;
}

.text {
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--admin-text, #111827);
  font-size: 13px;
}

[data-theme="dark"] .preview-msg.is-admin .bubble {
  background: rgba(96, 165, 250, 0.14);
}
</style>
