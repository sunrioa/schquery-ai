<template>
  <div class="admin-layout">
    <el-container class="layout-container">
      <el-aside :width="collapsed ? '64px' : '220px'" class="sidebar">
        <div class="sidebar-brand" @click="goHome">
          <div class="brand-icon">AI</div>
          <div v-if="!collapsed" class="brand-text">
            <p class="brand-name">SchQueryAI</p>
            <span class="brand-sub">Admin Console</span>
          </div>
        </div>

        <el-menu
          class="sidebar-menu"
          :default-active="activeMenu"
          :collapse="collapsed"
          router
          background-color="transparent"
          text-color="#6b7280"
          active-text-color="#111827"
        >
          <el-menu-item index="/dashboard">
            <el-icon><House /></el-icon>
            <span>控制台</span>
          </el-menu-item>

          <el-sub-menu index="ai">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>AI 管理</span>
            </template>
            <el-menu-item index="/admin/ai/chat-config">
              <el-icon><Setting /></el-icon>
              <span>默认对话参数</span>
            </el-menu-item>
            <el-menu-item index="/admin/ai/knowledge">
              <el-icon><UploadFilled /></el-icon>
              <span>知识库管理</span>
            </el-menu-item>
            <el-menu-item index="/admin/ai/chat-model">
              <el-icon><Collection /></el-icon>
              <span>模型管理</span>
            </el-menu-item>
            <el-menu-item index="/admin/ai/mcp">
              <el-icon><Monitor /></el-icon>
              <span>MCP 管理</span>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="system">
            <template #title>
              <el-icon><User /></el-icon>
              <span>系统管理</span>
            </template>
            <el-menu-item index="/admin/user-management">
              <el-icon><User /></el-icon>
              <span>用户管理</span>
            </el-menu-item>
            <el-menu-item index="/admin/sensitive-words">
              <el-icon><Warning /></el-icon>
              <span>敏感词管理</span>
            </el-menu-item>
            <el-menu-item index="/admin/segmentation-words">
              <el-icon><Collection /></el-icon>
              <span>分词管理</span>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="ops">
            <template #title>
              <el-icon><Service /></el-icon>
              <span>运营客服</span>
            </template>
            <el-menu-item index="/admin/customer-service">
              <el-icon><Service /></el-icon>
              <span>客服消息</span>
            </el-menu-item>
            <el-menu-item index="/admin/system-logs">
              <el-icon><Document /></el-icon>
              <span>系统日志</span>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="monitor">
            <template #title>
              <el-icon><Monitor /></el-icon>
              <span>系统监控</span>
            </template>
            <el-menu-item index="/admin/system-monitor">
              <el-icon><Monitor /></el-icon>
              <span>监控总览</span>
            </el-menu-item>
            <el-menu-item index="/admin/server-monitor">
              <el-icon><Cpu /></el-icon>
              <span>服务器监控</span>
            </el-menu-item>
            <el-menu-item index="/admin/mysql-monitor">
              <el-icon><Coin /></el-icon>
              <span>MySQL 监控</span>
            </el-menu-item>
            <el-menu-item index="/admin/redis-monitor">
              <el-icon><Coin /></el-icon>
              <span>Redis 监控</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-aside>

      <el-container class="main">
        <el-header class="topbar">
          <div class="topbar-left">
            <el-button text class="toolbar-icon" @click="toggleCollapse" :title="collapsed ? '展开侧栏' : '收起侧栏'">
              <el-icon><Menu /></el-icon>
            </el-button>
            <el-button text class="toolbar-icon" @click="goChat" title="返回聊天">
              <el-icon><ChatDotRound /></el-icon>
            </el-button>
          </div>

          <div class="topbar-right">
            <el-button text class="toolbar-icon" @click="toggleDarkMode" :title="isDarkMode ? '切换浅色模式' : '切换暗夜模式'">
              <el-icon><Moon v-if="!isDarkMode" /><Sunny v-else /></el-icon>
            </el-button>

            <el-dropdown>
              <span class="user-chip">
                <span class="user-name">{{ currentUser.userName || 'Admin' }}</span>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="goProfile">个人资料</el-dropdown-item>
                  <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>

        <el-main class="content">
          <slot />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clearUserInfo, getCurrentUser } from '../utils/auth'

const router = useRouter()
const route = useRoute()

const collapsed = ref(false)
const isDarkMode = ref(false)
const currentUser = ref(getCurrentUser())

const activeMenu = computed(() => {
  const p = route.path || ''
  if (p.startsWith('/admin/ai/knowledge/')) return '/admin/ai/knowledge'
  return p
})

const goHome = () => {
  router.push('/dashboard')
}

const goChat = () => {
  router.push('/chat')
}

const goProfile = () => {
  router.push('/profile')
}

const toggleCollapse = () => {
  collapsed.value = !collapsed.value
}

const applyTheme = (dark) => {
  const html = document.documentElement
  if (dark) {
    html.setAttribute('data-theme', 'dark')
    localStorage.setItem('theme', 'dark')
  } else {
    html.removeAttribute('data-theme')
    localStorage.setItem('theme', 'light')
  }
}

const toggleDarkMode = () => {
  isDarkMode.value = !isDarkMode.value
  applyTheme(isDarkMode.value)
  window.dispatchEvent(new CustomEvent('theme-change', { detail: { isDark: isDarkMode.value } }))
}

const logout = () => {
  clearUserInfo()
  ElMessage.success('退出登录成功')
  router.push('/login')
}

onMounted(() => {
  currentUser.value = getCurrentUser()
  const storedTheme = localStorage.getItem('theme')
  isDarkMode.value = storedTheme === 'dark'
  applyTheme(isDarkMode.value)
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background: #ffffff;
  border-right: 1px solid #eef2f7;
  overflow: hidden;
}

[data-theme="dark"] .sidebar {
  background: #1f2937;
  border-right-color: #374151;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  border-bottom: 1px solid #eef2f7;
}

[data-theme="dark"] .sidebar-brand {
  border-bottom-color: #374151;
}

.brand-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.brand-name {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

[data-theme="dark"] .brand-name {
  color: #e5e7eb;
}

.brand-sub {
  font-size: 12px;
  color: #6b7280;
}

[data-theme="dark"] .brand-sub {
  color: #9ca3af;
}

.sidebar-menu {
  border-right: none;
}

.main {
  background: #f0f2f5;
}

[data-theme="dark"] .main {
  background: #111827;
}

.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  background: #ffffff;
  border-bottom: 1px solid #eef2f7;
}

[data-theme="dark"] .topbar {
  background: #1f2937;
  border-bottom-color: #374151;
}

.topbar-left,
.topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-icon {
  color: inherit;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #111827;
  cursor: pointer;
}

[data-theme="dark"] .user-chip {
  background: #374151;
  color: #e5e7eb;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
}

.content {
  padding: 0;
  overflow: auto;
}
</style>
