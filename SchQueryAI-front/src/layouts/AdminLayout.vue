<template>
  <div class="admin-layout">
      <el-container class="layout-container">
      <el-aside :width="sidebarWidth" class="sidebar" :class="{ 'is-mobile': isMobile, open: mobileSidebarVisible }">
        <div class="sidebar-brand" @click="goHome">
          <div class="brand-icon">AI</div>
          <div v-if="!menuCollapsed" class="brand-text">
            <p class="brand-name">SchQueryAI</p>
            <span class="brand-sub">Admin Console</span>
          </div>
        </div>

        <el-scrollbar class="sidebar-scroll">
          <el-menu
            class="sidebar-menu"
            :default-active="activeMenu"
            :collapse="menuCollapsed"
            @select="handleMenuSelect"
            router
            background-color="transparent"
            :text-color="menuTextColor"
            :active-text-color="menuActiveTextColor"
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
              <el-menu-item index="/admin/ai/asr-model">
                <el-icon><Collection /></el-icon>
                <span>语音识别配置</span>
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
        </el-scrollbar>
      </el-aside>

      <div
        v-if="isMobile && mobileSidebarVisible"
        class="mobile-sidebar-mask"
        @click="mobileSidebarVisible = false"
      />

      <el-container class="main">
        <el-header class="topbar">
          <div class="topbar-left">
            <el-button
              text
              class="toolbar-icon"
              @click="toggleSidebar"
              :title="isMobile ? (mobileSidebarVisible ? '关闭菜单' : '打开菜单') : (collapsed ? '展开侧栏' : '收起侧栏')"
            >
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
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clearUserInfo, getCurrentUser } from '../utils/auth'
import { applyTheme, isDarkTheme } from '../utils/theme'

const router = useRouter()
const route = useRoute()

const collapsed = ref(false)
const isDarkMode = ref(false)
const currentUser = ref(getCurrentUser())

const isMobile = ref(false)
const mobileSidebarVisible = ref(false)

const menuTextColor = computed(() => 'var(--app-muted)')
const menuActiveTextColor = computed(() => 'var(--app-text)')

const menuCollapsed = computed(() => (isMobile.value ? false : collapsed.value))
const sidebarWidth = computed(() => (isMobile.value ? '220px' : (collapsed.value ? '64px' : '220px')))

const activeMenu = computed(() => {
  const p = route.path || ''
  if (p.startsWith('/admin/ai/knowledge/')) return '/admin/ai/knowledge'
  if (p.startsWith('/admin/customer-service/chat')) return '/admin/customer-service'
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

const updateIsMobile = () => {
  isMobile.value = window.innerWidth <= 768
  if (!isMobile.value) {
    mobileSidebarVisible.value = false
  }
}

const toggleSidebar = () => {
  if (isMobile.value) {
    mobileSidebarVisible.value = !mobileSidebarVisible.value
    return
  }
  collapsed.value = !collapsed.value
}

const handleMenuSelect = () => {
  if (isMobile.value) {
    mobileSidebarVisible.value = false
  }
}

const toggleDarkMode = () => {
  isDarkMode.value = !isDarkMode.value
  applyTheme(isDarkMode.value ? 'dark' : 'light')
}

const logout = () => {
  clearUserInfo()
  ElMessage.success('退出登录成功')
  router.push('/login')
}

onMounted(() => {
  currentUser.value = getCurrentUser()
  isDarkMode.value = isDarkTheme()
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateIsMobile)
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
  height: 100dvh;
}

.sidebar {
  background: var(--app-surface);
  border-right: 1px solid var(--app-border);
  overflow: hidden;
  height: 100vh;
  height: 100dvh;
  display: flex;
  flex-direction: column;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--app-border);
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
  background: linear-gradient(135deg, var(--app-primary) 0%, var(--app-primary-deep) 100%);
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
  color: var(--app-text);
}

.brand-sub {
  font-size: 12px;
  color: var(--app-muted);
}

.sidebar-menu {
  border-right: none;
}

.sidebar-scroll {
  flex: 1;
}

.sidebar-scroll :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.sidebar-menu :deep(.el-menu-item),
.sidebar-menu :deep(.el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  margin: 4px 10px;
  border-radius: 10px;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: var(--app-primary-soft);
}

.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background: var(--app-primary-soft-2);
}

.main {
  background: var(--app-bg);
}

.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  background: var(--app-surface);
  border-bottom: 1px solid var(--app-border);
  color: var(--app-text);
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
  background: var(--app-surface-2);
  color: var(--app-text);
  cursor: pointer;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
}

.content {
  padding: 0;
  overflow: auto;
}

.mobile-sidebar-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
  z-index: 98;
}

@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    width: min(86vw, 280px) !important;
    transform: translateX(-100%);
    transition: transform 0.28s ease;
    z-index: 99;
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.25);
  }

  .sidebar.open {
    transform: translateX(0);
  }

  .topbar {
    padding: 0 10px;
  }

  .user-name {
    display: none;
  }

  .content {
    padding-bottom: 12px;
  }
}
</style>
