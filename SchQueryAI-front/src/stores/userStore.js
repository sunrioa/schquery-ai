import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userApi } from '../api/user'
import { getUserAvatar, generateAvatarFromUsername, DEFAULT_USER_AVATAR } from '../utils/avatarUtils'
import { getCustomerServiceClient } from '../utils/customerServiceClient'
import { ElMessage, ElNotification } from 'element-plus'
import router from '../router'

export const useUserStore = defineStore('user', () => {
  // 用户信息
  const userInfo = ref({
    id: null,
    userName: '',
    email: '',
    avatar: null,
    role: null,
    createTime: null,
    updateTime: null
  })

  // 用户头像base64缓存
  const userAvatar = ref('')

  // 客服WebSocket客户端
  let customerServiceClient = null
  
  // 保存消息处理函数引用，避免重复绑定
  const handleUserMessage = (data) => {
    console.log('[userStore] 收到用户消息 - 原始数据:', data)
    
    // 提取用户名，优先使用 userName，如果没有则使用 fromUserId
    const userName = data.userName || data.fromUserId || '未知用户'
    const messageContent = data.content || ''
    const userId = data.fromUserId
    
    console.log('[userStore] 提取的用户名:', userName)
    console.log('[userStore] 消息内容:', messageContent)
    console.log('[userStore] 用户ID:', userId)
    
    // 触发新消息事件，让AdminCustomerService实时更新
    console.log('[userStore] 触发 new-customer-message 事件')
    window.dispatchEvent(new CustomEvent('new-customer-message', {
      detail: {
        userId: userId,
        message: {
          id: Date.now(),
          userId: userId,
          userName: userName,
          messageContent: messageContent,
          senderType: 1, // 1-用户
          createTime: new Date().toISOString()
        }
      }
    }))
    console.log('[userStore] new-customer-message 事件已触发')
    
    // 创建通知实例
    const notification = ElNotification({
      title: '新的客服消息',
      message: `${userName}：${messageContent}\n点击查看详情`,
      type: 'info',
      duration: 5000,  // 5秒后自动关闭
      position: 'top-right',
      onClick: () => {
        console.log('通知被点击，跳转到客服管理页面')
        // 关闭通知
        notification.close()
        
        // 先跳转到客服管理页面
        router.push('/admin/customer-service').then(() => {
          // 延迟触发刷新事件，确保页面已加载
          setTimeout(() => {
            console.log('触发刷新会话列表事件，用户ID:', userId)
            window.dispatchEvent(new CustomEvent('refresh-customer-sessions', {
              detail: { userId: userId }
            }))
          }, 200)
        })
      }
    })

    console.log('[userStore] 通知已显示')
  }

  // 获取用户信息
  const fetchUserInfo = async () => {
    try {
      console.log('[userStore] 开始获取用户信息...')
      const result = await userApi.getUserInfo()
      if (result.code === 200 && result.data) {
        userInfo.value = result.data
        console.log('[userStore] 用户信息获取成功:', result.data)
        console.log('[userStore] 用户角色:', result.data.role)

        // 如果用户有头像，获取头像数据
        if (result.data.avatar) {
          await fetchUserAvatar()
        } else {
          // 如果用户没有头像，清空头像缓存，使用默认头像
          userAvatar.value = ''
        }

        // 如果是管理员，自动连接客服WebSocket
        if (result.data.role === 'admin') {
          console.log('[userStore] 检测到管理员角色，准备初始化WebSocket...')
          initAdminWebSocket()
        } else {
          console.log('[userStore] 非管理员角色，跳过WebSocket初始化')
        }
      }
    } catch (error) {
      console.error('[userStore] 获取用户信息失败:', error)
    }
  }

  // 获取用户头像
  const fetchUserAvatar = async () => {
    try {
      const result = await userApi.getUserAvatar()
      if (result.code === 200 && result.data) {
        userAvatar.value = result.data
      } else {
        // 如果没有头像数据，清空头像缓存，使用默认头像
        userAvatar.value = ''
      }
    } catch (error) {
      console.error('获取用户头像失败:', error)
      // 获取失败时清空头像缓存，使用默认头像
      userAvatar.value = ''
    }
  }

  // 更新用户头像缓存
  const updateUserAvatar = (avatarData) => {
    userAvatar.value = avatarData
  }

  // 初始化管理员WebSocket连接
  const initAdminWebSocket = () => {
    // 如果已经初始化过，直接返回
    if (customerServiceClient) {
      console.log('[userStore] 管理员WebSocket已经初始化，跳过')
      console.log('[userStore] WebSocket连接状态:', customerServiceClient.isConnected())
      return
    }
    
    try {
      console.log('[userStore] ========== 管理员登录，初始化客服WebSocket连接 ==========')
      
      customerServiceClient = getCustomerServiceClient()
      customerServiceClient.setUserInfo('admin', 'admin')
      
      // 先移除可能存在的旧监听器，再添加新的
      customerServiceClient.off('user_message', handleUserMessage)
      customerServiceClient.on('user_message', handleUserMessage)
      console.log('[userStore] 已注册 user_message 事件监听器')

      // 监听连接状态
      customerServiceClient.on('connected', () => {
        console.log('[userStore] ========== 管理员客服WebSocket连接成功 ==========')
      })

      customerServiceClient.on('disconnected', () => {
        console.log('[userStore] 管理员客服WebSocket断开连接')
      })

      customerServiceClient.on('error', (error) => {
        console.error('[userStore] 管理员客服WebSocket错误:', error)
      })

      // 连接WebSocket
      console.log('[userStore] 开始连接WebSocket...')
      customerServiceClient.connect().then(() => {
        console.log('[userStore] WebSocket连接Promise resolved')
      }).catch(error => {
        console.error('[userStore] 管理员客服WebSocket连接失败:', error)
      })
    } catch (error) {
      console.error('[userStore] 初始化管理员WebSocket失败:', error)
    }
  }

  // 清除用户信息
  const clearUserInfo = () => {
    userInfo.value = {
      id: null,
      userName: '',
      email: '',
      avatar: null,
      role: null,
      createTime: null,
      updateTime: null
    }
    userAvatar.value = ''

    // 关闭WebSocket连接
    if (customerServiceClient) {
      customerServiceClient.close()
      customerServiceClient = null
    }
  }

  // 获取显示用的头像
  const getDisplayAvatar = () => {
    if (userAvatar.value) {
      return userAvatar.value
    }

    // 直接返回默认头像，不再生成用户名首字母头像
    return DEFAULT_USER_AVATAR
  }

  return {
    userInfo,
    userAvatar,
    fetchUserInfo,
    fetchUserAvatar,
    updateUserAvatar,
    clearUserInfo,
    getDisplayAvatar
  }
})