<template>
  <div class="conversation-management">
    <div class="management-header">
      <h1>对话管理</h1>
      <el-button @click="goBack" type="default">
        <el-icon><ArrowLeft /></el-icon>
        返回聊天
      </el-button>
    </div>

    <div class="management-content">
      <!-- 对话统计 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-number">{{ totalConversations }}</div>
              <div class="stat-label">总对话数</div>
            </div>
            <el-icon class="stat-icon"><ChatDotRound /></el-icon>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-number">{{ activeConversations }}</div>
              <div class="stat-label">活跃对话</div>
            </div>
            <el-icon class="stat-icon"><Service /></el-icon>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-number">{{ waitingResponses }}</div>
              <div class="stat-label">等待回复</div>
            </div>
            <el-icon class="stat-icon"><Clock /></el-icon>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-number">{{ todayConversations }}</div>
              <div class="stat-label">今日新增</div>
            </div>
            <el-icon class="stat-icon"><Calendar /></el-icon>
          </el-card>
        </el-col>
      </el-row>

      <!-- 搜索和筛选 -->
      <el-card class="filter-card">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索用户名或对话内容"
              prefix-icon="Search"
              clearable
              @input="handleSearch"
            />
          </el-col>
          <el-col :span="6">
            <el-select v-model="statusFilter" placeholder="状态筛选" clearable>
              <el-option label="全部" value="" />
              <el-option label="进行中" value="active" />
              <el-option label="已结束" value="ended" />
              <el-option label="等待回复" value="waiting" />
            </el-select>
          </el-col>
          <el-col :span="6">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              @change="handleDateChange"
            />
          </el-col>
          <el-col :span="4">
            <el-button type="primary" @click="loadConversations">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
          </el-col>
        </el-row>
      </el-card>

      <!-- 对话列表 -->
      <el-card class="conversation-list-card">
        <template #header>
          <div class="card-header">
            <span>对话列表</span>
            <el-button type="text" @click="loadConversations">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </template>

        <el-table
          :data="conversations"
          v-loading="loading"
          stripe
          style="width: 100%"
        >
          <el-table-column prop="id" label="对话ID" width="80" />
          <el-table-column prop="userName" label="用户" width="120">
            <template #default="{ row }">
              <el-avatar :size="30" :src="row.userAvatar" />
              <span style="margin-left: 8px">{{ row.userName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastMessage" label="最后消息" show-overflow-tooltip />
          <el-table-column prop="messageCount" label="消息数" width="80" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastMessageTime" label="最后活跃" width="150">
            <template #default="{ row }">
              {{ formatTime(row.lastMessageTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button size="small" @click="viewConversation(row)">
                查看对话
              </el-button>
              <el-button
                size="small"
                type="primary"
                @click="joinConversation(row)"
                v-if="row.status === 'waiting'"
              >
                加入对话
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </el-card>
    </div>

    <!-- 对话详情对话框 -->
    <el-dialog
      v-model="conversationDialogVisible"
      title="对话详情"
      width="80%"
      top="5vh"
    >
      <div class="conversation-detail" v-if="currentConversation">
        <div class="conversation-info">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="用户">{{ currentConversation.userName }}</el-descriptions-item>
            <el-descriptions-item label="对话ID">{{ currentConversation.id }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(currentConversation.status)">
                {{ getStatusText(currentConversation.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatTime(currentConversation.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="最后活跃">{{ formatTime(currentConversation.lastMessageTime) }}</el-descriptions-item>
            <el-descriptions-item label="消息总数">{{ currentConversation.messageCount }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="messages-container">
          <h3>对话记录</h3>
          <div class="messages-list">
            <div
              v-for="message in currentConversation.messages"
              :key="message.id"
              class="message-item"
              :class="{ 'user-message': message.sender === 'user', 'ai-message': message.sender === 'ai' }"
            >
              <div class="message-header">
                <span class="sender">{{ message.sender === 'user' ? currentConversation.userName : 'AI助手' }}</span>
                <span class="time">{{ formatTime(message.timestamp) }}</span>
              </div>
              <div class="message-content">{{ message.content }}</div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ChatDotRound,
  Service,
  Clock,
  Calendar,
  Search,
  Refresh
} from '@element-plus/icons-vue'

const router = useRouter()

// 响应式数据
const loading = ref(false)
const conversations = ref([])
const currentConversation = ref(null)
const conversationDialogVisible = ref(false)

// 搜索和筛选
const searchKeyword = ref('')
const statusFilter = ref('')
const dateRange = ref([])

// 分页
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

// 统计数据
const totalConversations = ref(156)
const activeConversations = ref(23)
const waitingResponses = ref(8)
const todayConversations = ref(12)

// 状态相关
const getStatusType = (status) => {
  switch (status) {
    case 'active': return 'success'
    case 'waiting': return 'warning'
    case 'ended': return 'info'
    default: return 'info'
  }
}

const getStatusText = (status) => {
  switch (status) {
    case 'active': return '进行中'
    case 'waiting': return '等待回复'
    case 'ended': return '已结束'
    default: return '未知'
  }
}

// 时间格式化
const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

// 返回聊天界面
const goBack = () => {
  router.push('/chat')
}

// 加载对话列表
const loadConversations = async () => {
  loading.value = true
  try {
    // TODO: 调用实际的API接口
    // const response = await conversationApi.getConversations({
    //   page: currentPage.value,
    //   pageSize: pageSize.value,
    //   keyword: searchKeyword.value,
    //   status: statusFilter.value,
    //   startDate: dateRange.value?.[0],
    //   endDate: dateRange.value?.[1]
    // })

    // 模拟数据
    await new Promise(resolve => setTimeout(resolve, 1000))
    conversations.value = [
      {
        id: 1,
        userName: '张三',
        userAvatar: '',
        lastMessage: '请问贵校的计算机专业有什么特色？',
        messageCount: 12,
        status: 'active',
        lastMessageTime: new Date().toISOString(),
        startTime: new Date(Date.now() - 3600000).toISOString()
      },
      {
        id: 2,
        userName: '李四',
        userAvatar: '',
        lastMessage: '谢谢你的回答，很有帮助！',
        messageCount: 8,
        status: 'waiting',
        lastMessageTime: new Date(Date.now() - 1800000).toISOString(),
        startTime: new Date(Date.now() - 7200000).toISOString()
      },
      {
        id: 3,
        userName: '王五',
        userAvatar: '',
        lastMessage: '再见！',
        messageCount: 5,
        status: 'ended',
        lastMessageTime: new Date(Date.now() - 86400000).toISOString(),
        startTime: new Date(Date.now() - 90000000).toISOString()
      }
    ]
    total.value = 156
  } catch (error) {
    console.error('加载对话列表失败:', error)
    ElMessage.error('加载对话列表失败')
  } finally {
    loading.value = false
  }
}

// 查看对话详情
const viewConversation = async (conversation) => {
  try {
    // TODO: 调用实际的API接口获取对话详情
    // const response = await conversationApi.getConversationDetail(conversation.id)

    // 模拟对话详情数据
    currentConversation.value = {
      ...conversation,
      messages: [
        {
          id: 1,
          sender: 'user',
          content: '你好，我想了解一下贵校的情况',
          timestamp: new Date(Date.now() - 3600000).toISOString()
        },
        {
          id: 2,
          sender: 'ai',
          content: '您好！欢迎咨询，请问您想了解哪个方面呢？',
          timestamp: new Date(Date.now() - 3500000).toISOString()
        },
        {
          id: 3,
          sender: 'user',
          content: conversation.lastMessage,
          timestamp: conversation.lastMessageTime
        }
      ]
    }
    conversationDialogVisible.value = true
  } catch (error) {
    console.error('获取对话详情失败:', error)
    ElMessage.error('获取对话详情失败')
  }
}

// 加入对话
const joinConversation = (conversation) => {
  // TODO: 实现加入对话的逻辑
  ElMessage.success(`已加入与 ${conversation.userName} 的对话`)
  router.push('/chat')
}

// 搜索处理
const handleSearch = () => {
  currentPage.value = 1
  loadConversations()
}

// 日期范围变化
const handleDateChange = () => {
  currentPage.value = 1
  loadConversations()
}

// 分页处理
const handleSizeChange = (newSize) => {
  pageSize.value = newSize
  currentPage.value = 1
  loadConversations()
}

const handleCurrentChange = (newPage) => {
  currentPage.value = newPage
  loadConversations()
}

// 页面加载时获取数据
onMounted(() => {
  loadConversations()
})
</script>

<style scoped>
.conversation-management {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.management-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.management-header h1 {
  margin: 0;
  color: #333;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  position: relative;
  overflow: hidden;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-content {
  z-index: 2;
}

.stat-number {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 5px;
}

.stat-label {
  font-size: 14px;
  color: #666;
}

.stat-icon {
  font-size: 48px;
  color: rgba(64, 158, 255, 0.2);
  position: absolute;
  right: 20px;
  top: 50%;
  transform: translateY(-50%);
}

.filter-card {
  margin-bottom: 20px;
}

.conversation-list-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-wrapper {
  margin-top: 20px;
  text-align: center;
}

.conversation-detail {
  max-height: 70vh;
  overflow-y: auto;
}

.conversation-info {
  margin-bottom: 20px;
}

.messages-container h3 {
  margin-bottom: 15px;
  color: #333;
}

.messages-list {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 15px;
  max-height: 400px;
  overflow-y: auto;
}

.message-item {
  margin-bottom: 15px;
  padding: 10px;
  border-radius: 8px;
}

.message-item.user-message {
  background-color: #e3f2fd;
  margin-left: 20px;
}

.message-item.ai-message {
  background-color: #f5f5f5;
  margin-right: 20px;
}

.message-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
}

.sender {
  font-weight: bold;
  color: #333;
}

.time {
  color: #666;
}

.message-content {
  line-height: 1.5;
  word-break: break-word;
}

@media (max-width: 768px) {
  .management-header {
    flex-direction: column;
    gap: 15px;
    align-items: flex-start;
  }

  .stats-row :deep(.el-col) {
    margin-bottom: 10px;
  }

  .filter-card :deep(.el-row) :deep(.el-col) {
    margin-bottom: 10px;
  }
}
</style>