import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import PasswordManager from '../views/PasswordManager.vue'
import Password from '../views/Password.vue'
import ForgotPassword from '../views/ForgotPassword.vue'
import Profile from '../views/Profile.vue'
import Chat from '../views/Chat.vue'
import ChatWindow from '../views/ChatWindow.vue'
import WidgetChat from '../views/WidgetChat.vue'
import AdminCustomerService from '../views/admin/AdminCustomerService.vue'
import AdminCustomerServiceInbox from '../views/admin/AdminCustomerServiceInbox.vue'
import SystemManagement from '../views/admin/SystemManagement.vue'
import SystemMonitor from '../views/admin/SystemMonitor.vue'
import ConversationManagement from '../views/worker/ConversationManagement.vue'
import SensitiveWordsManagement from '../views/admin/sensitive-words.vue'
import SegmentationWordsManagement from '../views/admin/segmentation-words.vue'
import UserManagement from '../views/admin/UserManagement.vue'
import AdminDashboard from '../views/AdminDashboard.vue'
import ChatConfig from '../views/admin/ai/ChatConfig.vue'
import ChatModel from '../views/admin/ai/ChatModel.vue'
import ChatManagement from '../views/admin/ai/ChatManagement.vue'
import AsrModel from '../views/admin/ai/AsrModel.vue'
import KnowledgeManage from '../views/admin/ai/KnowledgeManage.vue'
import KnowledgeDetail from '../views/admin/ai/KnowledgeDetail.vue'
import McpManage from '../views/admin/ai/McpManage.vue'
import SystemLogsManagement from '../views/admin/system-logs.vue'
import ServerMonitor from '../views/admin/ServerMonitor.vue'
import MySQLMonitor from '../views/admin/MySQLMonitor.vue'
import RedisMonitor from '../views/admin/RedisMonitor.vue'
import NotFound from '../views/NotFound.vue'
import { hasAnyRole } from '../utils/auth'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/register',
    name: 'Register',
    component: Register
  },
  {
    path: '/password',
    name: 'Password',
    component: Password
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: ForgotPassword
  },
  {
    path: '/profile',
    name: 'Profile',
    component: Profile,
    meta: { requiresAuth: true }
  },
  {
    path: '/password-manager',
    name: 'PasswordManager',
    component: PasswordManager,
    meta: { requiresAuth: true }
  },
  {
    path: '/chat',
    name: 'Chat',
    component: Chat,
    meta: { requiresAuth: true }
  },
  {
    path: '/chat-window',
    name: 'ChatWindow',
    component: ChatWindow,
    meta: { requiresAuth: true }
  },
  {
    path: '/widget/chat',
    name: 'WidgetChat',
    component: WidgetChat
  },
  {
    path: '/worker/conversation-management',
    name: 'ConversationManagement',
    component: ConversationManagement,
    meta: { requiresAuth: true, requiresRole: ['worker', 'admin'] }
  },
  {
    path: '/dashboard',
    name: 'AdminDashboard',
    component: AdminDashboard,
    meta: { requiresAuth: true, layout: 'admin' }
  },
  {
    path: '/admin/sensitive-words',
    name: 'SensitiveWordsManagement',
    component: SensitiveWordsManagement,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/segmentation-words',
    name: 'SegmentationWordsManagement',
    component: SegmentationWordsManagement,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/user-management',
    name: 'UserManagement',
    component: UserManagement,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/customer-service',
    name: 'AdminCustomerServiceInbox',
    component: AdminCustomerServiceInbox,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/customer-service/chat/:userId?',
    name: 'AdminCustomerServiceChat',
    component: AdminCustomerService,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/system-management',
    name: 'SystemManagement',
    component: SystemManagement,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/system-monitor',
    name: 'SystemMonitor',
    component: SystemMonitor,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/system-logs',
    name: 'SystemLogsManagement',
    component: SystemLogsManagement,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/server-monitor',
    name: 'ServerMonitor',
    component: ServerMonitor,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/mysql-monitor',
    name: 'MySQLMonitor',
    component: MySQLMonitor,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/redis-monitor',
    name: 'RedisMonitor',
    component: RedisMonitor,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/knowledge-base',
    redirect: '/admin/ai/knowledge',
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/ai/chat-config',
    name: 'ChatConfig',
    component: ChatConfig,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/ai/chat-management',
    name: 'ChatManagement',
    component: ChatManagement,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/ai/chat-model',
    name: 'ChatModel',
    component: ChatModel,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/ai/asr-model',
    name: 'AsrModel',
    component: AsrModel,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/ai/knowledge',
    name: 'KnowledgeManage',
    component: KnowledgeManage,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/ai/knowledge/:id',
    name: 'KnowledgeDetail',
    component: KnowledgeDetail,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/ai/mcp',
    name: 'McpManage',
    component: McpManage,
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/admin/ai/crawler',
    name: 'CrawlerManager',
    component: () => import('@/views/admin/ai/CrawlerManager.vue'),
    meta: { requiresAuth: true, requiresRole: ['admin'], layout: 'admin' }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: NotFound
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
]

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')

  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if ((to.path === '/login' || to.path === '/register') && token) {
    next('/chat')
  } else if (to.meta.requiresRole && !hasAnyRole(to.meta.requiresRole)) {
    // 如果用户没有所需角色，跳转到无权限页面或聊天页面
    next('/chat')
  } else {
    next()
  }
})

export default router
