import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import PasswordManager from '../views/PasswordManager.vue'
import Password from '../views/Password.vue'
import ForgotPassword from '../views/ForgotPassword.vue'
import Profile from '../views/Profile.vue'
import Chat from '../views/Chat.vue'
import ConversationManagement from '../views/worker/ConversationManagement.vue'
import SystemManagement from '../views/admin/SystemManagement.vue'
import SensitiveWordsManagement from '../views/admin/sensitive-words.vue'
import SegmentationWordsManagement from '../views/admin/segmentation-words.vue'
import AdminDashboard from '../views/AdminDashboard.vue'
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
    path: '/worker/conversation-management',
    name: 'ConversationManagement',
    component: ConversationManagement,
    meta: { requiresAuth: true, requiresRole: ['worker', 'admin'] }
  },
  {
    path: '/dashboard',
    name: 'AdminDashboard',
    component: AdminDashboard,
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/sensitive-words',
    name: 'SensitiveWordsManagement',
    component: SensitiveWordsManagement,
    meta: { requiresAuth: true, requiresRole: ['admin'] }
  },
  {
    path: '/admin/segmentation-words',
    name: 'SegmentationWordsManagement',
    component: SegmentationWordsManagement,
    meta: { requiresAuth: true, requiresRole: ['admin'] }
  },
  {
    path: '/admin/system-management',
    name: 'SystemManagement',
    component: SystemManagement,
    meta: { requiresAuth: true, requiresRole: ['admin'] }
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
  history: createWebHistory(),
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