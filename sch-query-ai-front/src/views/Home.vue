<template>
  <div class="home-container">
    <el-container>
      <!-- 侧边栏 -->
      <el-aside width="250px" class="sidebar">
        <div class="logo">
          <h2>SchQueryAI</h2>
        </div>

        <el-menu
          :default-active="activeMenu"
          class="sidebar-menu"
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
          router
        >
          <el-menu-item index="/home">
            <el-icon><House /></el-icon>
            <span>首页</span>
          </el-menu-item>

          <el-sub-menu index="user">
            <template #title>
              <el-icon><User /></el-icon>
              <span>用户管理</span>
            </template>
            <el-menu-item index="/password">
              <el-icon><Lock /></el-icon>
              <span>密码管理</span>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="query">
            <template #title>
              <el-icon><Search /></el-icon>
              <span>查询功能</span>
            </template>
            <el-menu-item index="/query/scholar">
              <el-icon><Document /></el-icon>
              <span>学者查询</span>
            </el-menu-item>
            <el-menu-item index="/query/paper">
              <el-icon><Reading /></el-icon>
              <span>论文查询</span>
            </el-menu-item>
            <el-menu-item index="/query/institution">
              <el-icon><School /></el-icon>
              <span>机构查询</span>
            </el-menu-item>
          </el-sub-menu>

          <el-menu-item index="/statistics">
            <el-icon><TrendCharts /></el-icon>
            <span>统计分析</span>
          </el-menu-item>

          <el-menu-item index="/chat">
            <el-icon><ChatDotRound /></el-icon>
            <span>AI 聊天</span>
          </el-menu-item>

          <el-menu-item index="/settings">
            <el-icon><Setting /></el-icon>
            <span>系统设置</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <!-- 主内容区 -->
      <el-container>
        <!-- 顶部导航 -->
        <el-header class="header">
          <div class="header-left">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item v-if="currentRoute">{{ currentRoute }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>

          <div class="header-right">
            <el-dropdown @command="handleCommand">
              <span class="user-dropdown">
                <el-avatar :size="35" icon="UserFilled" />
                <span class="username">用户</span>
                <el-icon class="el-icon--right"><arrow-down /></el-icon>
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

        <!-- 主要内容 -->
        <el-main class="main-content">
          <div class="welcome-section">
            <el-card class="welcome-card">
              <template #header>
                <div class="card-header">
                  <h3>欢迎使用 SchQueryAI 系统</h3>
                </div>
              </template>

              <div class="welcome-content">
                <p>这是一个智能学术查询系统，为您提供以下功能：</p>

                <el-row :gutter="20" class="feature-cards">
                  <el-col :span="8">
                    <el-card class="feature-card" shadow="hover">
                      <div class="feature-icon">
                        <el-icon :size="40"><Search /></el-icon>
                      </div>
                      <h4>智能查询</h4>
                      <p>快速搜索学者、论文和机构信息</p>
                    </el-card>
                  </el-col>

                  <el-col :span="8">
                    <el-card class="feature-card" shadow="hover">
                      <div class="feature-icon">
                        <el-icon :size="40"><TrendCharts /></el-icon>
                      </div>
                      <h4>数据分析</h4>
                      <p>提供详细的学术数据统计分析</p>
                    </el-card>
                  </el-col>

                  <el-col :span="8">
                    <el-card class="feature-card" shadow="hover">
                      <div class="feature-icon">
                        <el-icon :size="40"><Document /></el-icon>
                      </div>
                      <h4>文献管理</h4>
                      <p>管理和组织学术文献资源</p>
                    </el-card>
                  </el-col>
                </el-row>

                <div class="quick-actions">
                  <h4>快速操作</h4>
                  <el-space wrap>
                    <el-button type="primary" @click="$router.push('/query/scholar')">
                      <el-icon><Search /></el-icon>
                      学者查询
                    </el-button>
                    <el-button type="success" @click="$router.push('/query/paper')">
                      <el-icon><Reading /></el-icon>
                      论文查询
                    </el-button>
                    <el-button type="warning" @click="$router.push('/statistics')">
                      <el-icon><TrendCharts /></el-icon>
                      统计分析
                    </el-button>
                    <el-button type="info" @click="$router.push('/password')">
                      <el-icon><Lock /></el-icon>
                      密码管理
                    </el-button>
                    <el-button type="primary" @click="$router.push('/chat')">
                      <el-icon><ChatDotRound /></el-icon>
                      AI 聊天
                    </el-button>
                  </el-space>
                </div>
              </div>
            </el-card>
          </div>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  House,
  User,
  Lock,
  Search,
  Document,
  Reading,
  School,
  TrendCharts,
  Setting,
  ChatDotRound,
  ArrowDown,
  SwitchButton
} from '@element-plus/icons-vue'

export default {
  name: 'HomePage',
  components: {
    House,
    User,
    Lock,
    Search,
    Document,
    Reading,
    School,
    TrendCharts,
    Setting,
    ChatDotRound,
    ArrowDown,
    SwitchButton
  },
  setup() {
    const router = useRouter()
    const route = useRoute()
    const activeMenu = ref('/home')

    const currentRoute = computed(() => {
      const routeMap = {
        '/home': '首页',
        '/password': '密码管理',
        '/query/scholar': '学者查询',
        '/query/paper': '论文查询',
        '/query/institution': '机构查询',
        '/statistics': '统计分析',
        '/chat': 'AI 聊天',
        '/settings': '系统设置'
      }
      return routeMap[route.path] || ''
    })

    // 处理用户下拉菜单命令
    const handleCommand = async (command) => {
      switch (command) {
        case 'profile':
          ElMessage.info('个人信息功能开发中...')
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
            ElMessage.success('退出登录成功')
            router.push('/login')
          } catch {
            // 用户取消操作
          }
          break
      }
    }

    onMounted(() => {
      activeMenu.value = route.path
    })

    return {
      activeMenu,
      currentRoute,
      handleCommand
    }
  }
}
</script>

<style scoped>
.home-container {
  height: 100vh;
}

.sidebar {
  background-color: #304156;
  height: 100vh;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b3a4b;
  color: #fff;
  margin-bottom: 20px;
}

.logo h2 {
  margin: 0;
  font-size: 18px;
}

.sidebar-menu {
  border: none;
  height: calc(100vh - 80px);
  overflow-y: auto;
}

.header {
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.header-left {
  flex: 1;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-dropdown {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.user-dropdown:hover {
  background-color: #f5f7fa;
}

.username {
  margin: 0 8px;
  color: #303133;
}

.main-content {
  background-color: #f0f2f5;
  padding: 20px;
}

.welcome-section {
  max-width: 1200px;
  margin: 0 auto;
}

.welcome-card {
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.card-header h3 {
  margin: 0;
  color: #303133;
  font-size: 20px;
}

.welcome-content p {
  color: #606266;
  line-height: 1.6;
  margin-bottom: 20px;
}

.feature-cards {
  margin: 30px 0;
}

.feature-card {
  text-align: center;
  border-radius: 8px;
  transition: transform 0.3s, box-shadow 0.3s;
}

.feature-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.feature-icon {
  color: #409EFF;
  margin-bottom: 15px;
}

.feature-card h4 {
  margin: 10px 0;
  color: #303133;
}

.feature-card p {
  color: #909399;
  margin: 0;
  font-size: 14px;
}

.quick-actions {
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
}

.quick-actions h4 {
  margin: 0 0 15px 0;
  color: #303133;
}
</style>