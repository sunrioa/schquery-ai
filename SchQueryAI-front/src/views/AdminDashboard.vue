<template>
  <div :class="inAdminLayout ? 'admin-page admin-dashboard' : 'admin-dashboard'">
    <div v-if="!inAdminLayout" class="dashboard-header">
      <h1>{{ getDashboardTitle() }}</h1>
      <div class="user-info">
        <span>欢迎，{{ currentUser.userName }}</span>
        <el-tag :type="getRoleTagType(currentUser.role)">
          {{ getRoleDisplayName(currentUser.role) }}
        </el-tag>
        <el-button @click="logout" type="danger" size="small">退出登录</el-button>
      </div>
    </div>
    <div v-else class="page-header">
      <div class="header-left">
        <h2>{{ getDashboardTitle() }}</h2>
        <p class="sub">系统与 AI 能力的统一入口</p>
      </div>
    </div>

    <div class="dashboard-content">
      <!-- 管理员专属功能 -->
      <div v-if="isAdminUser" class="admin-only-section">
        <h2>系统管理</h2>
        <el-card class="admin-card">
          <div class="card-actions">
            <el-button type="danger" @click="goToSensitiveWords">
              <el-icon><Setting /></el-icon>
              敏感词管理
            </el-button>
            <el-button type="success" @click="goToSegmentation">
              <el-icon><Collection /></el-icon>
              分词管理
            </el-button>
            <el-button type="warning" @click="goToUserManagement">
              <el-icon><User /></el-icon>
              用户管理
            </el-button>
          </div>
          <p>管理系统敏感词库、分词词库和用户权限，维护系统安全和秩序。</p>
        </el-card>

        <h2>AI 管理</h2>
        <el-card class="admin-card">
          <div class="card-actions">
            <el-button type="primary" @click="goToAiChatConfig">
              <el-icon><Setting /></el-icon>
              默认对话参数
            </el-button>
            <el-button type="success" @click="goToAiKnowledge">
              <el-icon><UploadFilled /></el-icon>
              知识库管理
            </el-button>
            <el-button type="warning" @click="goToAiChatModel">
              <el-icon><Collection /></el-icon>
              模型管理
            </el-button>
            <el-button type="info" @click="goToAiMcp">
              <el-icon><View /></el-icon>
              MCP 管理
            </el-button>
          </div>
          <p>配置默认模型/默认知识库/MCP 策略，并维护知识库与模型。</p>
        </el-card>
      </div>

      <!-- 员工功能 -->
      <div v-if="isWorkerUser" class="worker-section">
        <h2>员工工作台</h2>
        <el-card class="worker-card">
          <div class="card-actions">
            <el-button type="primary" @click="goToChat">
              <el-icon><ChatDotRound /></el-icon>
              智能问答
            </el-button>
            <el-button type="info" @click="goToDataAnalysis">
              <el-icon><DataAnalysis /></el-icon>
              数据统计
            </el-button>
            <el-button type="warning" @click="goToContentReview">
              <el-icon><View /></el-icon>
              内容审核
            </el-button>
          </div>
          <p>使用AI智能问答功能，查看系统数据统计，审核用户生成内容。</p>
        </el-card>
      </div>

      <!-- 普通用户功能 -->
      <div v-if="isNormalUser" class="user-section">
        <h2>用户中心</h2>
        <el-card class="user-card">
          <div class="card-actions">
            <el-button type="primary" @click="goToChat">
              <el-icon><ChatDotRound /></el-icon>
              智能聊天
            </el-button>
            <el-button type="info" @click="goToProfile">
              <el-icon><User /></el-icon>
              个人资料
            </el-button>
            <el-button type="success" @click="goToHistory">
              <el-icon><Clock /></el-icon>
              历史记录
            </el-button>
          </div>
          <p>使用AI智能聊天功能，管理个人资料，查看历史记录。</p>
        </el-card>
      </div>

      <!-- 基于角色权限的功能显示 -->
      <div class="role-based-features">
        <h2>权限控制功能示例</h2>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-card class="feature-card">
              <template #header>
                <span>系统配置</span>
                <el-tag v-if="isAdminUser" type="danger" size="small">管理员</el-tag>
                <el-tag v-else type="info" size="small">不可见</el-tag>
              </template>
              <div v-if="isAdminUser">
                <p>配置系统参数和全局设置</p>
                <el-button type="danger" size="small">系统配置</el-button>
              </div>
              <div v-else>
                <p style="color: #999;">此功能需要管理员权限</p>
              </div>
            </el-card>
          </el-col>

          <el-col :span="8">
            <el-card class="feature-card">
              <template #header>
                <span>内容审核</span>
                <el-tag v-if="isStaffMember" type="warning" size="small">工作人员</el-tag>
                <el-tag v-else type="info" size="small">不可见</el-tag>
              </template>
              <div v-if="isStaffMember">
                <p>审核和管理用户生成的内容</p>
                <el-button type="warning" size="small">内容审核</el-button>
              </div>
              <div v-else>
                <p style="color: #999;">此功能需要员工以上权限</p>
              </div>
            </el-card>
          </el-col>

          <el-col :span="8">
            <el-card class="feature-card">
              <template #header>
                <span>数据分析</span>
                <el-tag v-if="isStaffMember" type="success" size="small">工作人员</el-tag>
                <el-tag v-else type="info" size="small">不可见</el-tag>
              </template>
              <div v-if="isStaffMember">
                <p>查看系统使用统计和数据报表</p>
                <el-button type="success" size="small">数据分析</el-button>
              </div>
              <div v-else>
                <p style="color: #999;">此功能需要工作人员权限</p>
              </div>
            </el-card>
          </el-col>

          <el-col :span="8">
            <el-card class="feature-card">
              <template #header>
                <span>智能聊天</span>
                <el-tag type="success" size="small">所有用户</el-tag>
              </template>
              <div>
                <p>使用AI智能问答功能</p>
                <el-button type="primary" size="small">开始聊天</el-button>
              </div>
            </el-card>
          </el-col>

          <el-col :span="8">
            <el-card class="feature-card">
              <template #header>
                <span>权限管理</span>
                <el-tag v-if="isAdminUser" type="danger" size="small">管理员</el-tag>
                <el-tag v-else type="info" size="small">不可见</el-tag>
              </template>
              <div v-if="isAdminUser">
                <p>管理用户权限和角色分配</p>
                <el-button type="danger" size="small">权限管理</el-button>
              </div>
              <div v-else>
                <p style="color: #999;">此功能需要管理员权限</p>
              </div>
            </el-card>
          </el-col>

          <el-col :span="8">
            <el-card class="feature-card">
              <template #header>
                <span>用户资料</span>
                <el-tag type="primary" size="small">所有用户</el-tag>
              </template>
              <div>
                <p>查看和管理个人资料信息</p>
                <el-button type="primary" size="small">个人资料</el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Setting,
  Collection,
  ChatDotRound,
  User,
  DataAnalysis,
  View,
  Clock,
  UploadFilled
} from '@element-plus/icons-vue'
import {
  getUserRole,
  isAdmin,
  isUser,
  isWorker,
  isStaff,
  hasRole,
  getCurrentUser,
  clearUserInfo,
  getRoleDisplayName,
  USER_ROLES
} from '../utils/auth'

const router = useRouter()
const route = useRoute()

// 响应式数据
const currentUser = ref({})

// 计算属性
const isAdminUser = computed(() => isAdmin())
const isWorkerUser = computed(() => isWorker())
const isNormalUser = computed(() => isUser())
const isStaffMember = computed(() => isStaff()) // admin或worker组合，表示工作人员
const inAdminLayout = computed(() => route.meta?.layout === 'admin')

// 获取角色标签类型
const getRoleTagType = (role) => {
  switch (role) {
    case USER_ROLES.ADMIN:
      return 'danger'
    case USER_ROLES.WORKER:
      return 'warning'
    case USER_ROLES.USER:
      return 'primary'
    default:
      return 'info'
  }
}

// 获取仪表板标题
const getDashboardTitle = () => {
  const role = getUserRole()
  switch (role) {
    case USER_ROLES.ADMIN:
      return '管理员控制台'
    case USER_ROLES.WORKER:
      return '员工工作台'
    case USER_ROLES.USER:
      return '用户中心'
    default:
      return '系统面板'
  }
}

// 页面加载时获取用户信息
onMounted(() => {
  currentUser.value = getCurrentUser()
  if (!currentUser.value.token) {
    ElMessage.warning('您尚未登录，请先登录')
    router.push('/login')
  }
})

// 退出登录
const logout = () => {
  clearUserInfo()
  ElMessage.success('退出登录成功')
  router.push('/login')
}

// 导航方法
const goToSensitiveWords = () => {
  // 跳转到敏感词管理页面
  router.push('/admin/sensitive-words')
}

const goToSegmentation = () => {
  // 跳转到分词管理页面
  router.push('/admin/segmentation-words')
}

const goToUserManagement = () => {
  // 跳转到用户管理页面
  router.push('/admin/user-management')
}

const goToAiKnowledge = () => {
  router.push('/admin/ai/knowledge')
}

const goToAiChatConfig = () => {
  router.push('/admin/ai/chat-config')
}

const goToAiChatModel = () => {
  router.push('/admin/ai/chat-model')
}

const goToAiMcp = () => {
  router.push('/admin/ai/mcp')
}

const goToChat = () => {
  // 跳转到聊天页面
  router.push('/chat')
}

const goToProfile = () => {
  // 跳转到个人资料页面
  router.push('/profile')
}

const goToHistory = () => {
  // 跳转到历史记录页面
  router.push('/history')
}

const goToDataAnalysis = () => {
  // 跳转到数据分析页面
  router.push('/worker/analytics')
}

const goToContentReview = () => {
  // 跳转到内容审核页面
  router.push('/worker/review')
}
</script>

<style scoped>
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 10px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
}

.dashboard-header h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 15px;
}

.user-info span {
  font-size: 16px;
  font-weight: 500;
}

.dashboard-content h2 {
  color: #333;
  margin-bottom: 20px;
  font-size: 20px;
  font-weight: 600;
}

.admin-only-section, .worker-section, .user-section, .role-based-features {
  margin-bottom: 40px;
}

.admin-card, .worker-card, .user-card, .feature-card {
  margin-bottom: 20px;
}

/* 管理员卡片样式 */
.admin-card {
  border-left: 4px solid #F56C6C;
}

/* 员工卡片样式 */
.worker-card {
  border-left: 4px solid #E6A23C;
}

/* 用户卡片样式 */
.user-card {
  border-left: 4px solid #409EFF;
}

.card-actions {
  display: flex;
  gap: 15px;
  margin-bottom: 15px;
  flex-wrap: wrap;
}

.feature-card p {
  margin: 10px 0;
  line-height: 1.5;
}

.feature-card .el-button {
  margin-top: 10px;
}

@media (max-width: 768px) {
  .dashboard-header {
    flex-direction: column;
    gap: 15px;
    text-align: center;
  }

  .user-info {
    justify-content: center;
  }

  .card-actions {
    justify-content: center;
  }
}
</style>
