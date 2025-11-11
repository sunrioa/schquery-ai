import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userApi } from '../api/user'
import { getUserAvatar, generateAvatarFromUsername, DEFAULT_USER_AVATAR } from '../utils/avatarUtils'

export const useUserStore = defineStore('user', () => {
  // 用户信息
  const userInfo = ref({
    id: null,
    userName: '',
    email: '',
    avatar: null,
    createTime: null,
    updateTime: null
  })

  // 用户头像base64缓存
  const userAvatar = ref('')

  // 获取用户信息
  const fetchUserInfo = async () => {
    try {
      const result = await userApi.getUserInfo()
      if (result.code === 200 && result.data) {
        userInfo.value = result.data

        // 如果用户有头像，获取头像数据
        if (result.data.avatar) {
          await fetchUserAvatar()
        } else {
          // 如果用户没有头像，清空头像缓存，使用默认头像
          userAvatar.value = ''
        }
      }
    } catch (error) {
      console.error('获取用户信息失败:', error)
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

  // 清除用户信息
  const clearUserInfo = () => {
    userInfo.value = {
      id: null,
      userName: '',
      email: '',
      avatar: null,
      createTime: null,
      updateTime: null
    }
    userAvatar.value = ''
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