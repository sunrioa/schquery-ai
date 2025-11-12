import request from './request'

export const userApi = {
  // 用户注册
  register(userData) {
    return request.post('/user/register', userData)
  },

  // 发送注册验证码
  sendRegisterCode(userData) {
    return request.post('/user/sendRegisterCode', userData)
  },

  // 用户登录
  login(userData) {
    return request.post('/user/login', userData)
  },

  // 修改密码
  updatePassword(userData) {
    return request.post('/user/updatePassword', userData)
  },

  // 找回密码
  findPassword(userData) {
    return request.post('/user/findPassword', userData)
  },

  // 发送找回密码验证码
  sendFindPasswordCode(userData) {
    return request.post('/user/sendFindPasswordCode', userData)
  },

  // 上传头像
  uploadAvatar(avatarFile) {
    const formData = new FormData()
    formData.append('avatarFile', avatarFile)
    return request.post('/user/uploadAvatar', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 获取用户头像
  getUserAvatar() {
    return request.get('/user/getAvatar')
  },

  // 根据avatarId获取头像
  getAvatarById(avatarId) {
    return request.get('/user/getAvatarById', {
      params: { avatarId }
    })
  },

  // 更新用户头像
  updateAvatar(avatarFile) {
    const formData = new FormData()
    formData.append('avatarFile', avatarFile)
    return request.post('/user/updateAvatar', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 获取用户信息
  getUserInfo() {
    return request.get('/user/getUserInfo')
  },

  // 删除用户头像
  deleteAvatar() {
    return request.post('/user/deleteAvatar')
  },

  // 发送重置密码验证码
  sendResetCode(email) {
    return request.post('/user/sendFindPasswordCode', { email })
  },

  // 检查邮箱是否存在 (通过发送验证码接口来验证)
  checkEmailExists(email) {
    return request.post('/user/sendFindPasswordCode', { email })
  },

  // 验证重置密码验证码并重置密码
  resetPassword(data) {
    return request.post('/user/findPassword', data)
  },

  // 获取登录历史
  getLoginHistory(pageNum = 1, pageSize = 10) {
    return request.get('/user/loginHistory', {
      params: { pageNum, pageSize }
    })
  },

  // 管理员功能：获取所有用户列表
  getAllUsers(pageNum = 1, pageSize = 10) {
    return request.get('/user/admin/allUsers', {
      params: { pageNum, pageSize }
    })
  },

  // 仪表板统计信息
  getDashboardStats() {
    return request.get('/user/admin/dashboardStats')
  },

  // 管理员功能：获取指定用户的登录历史
  getUserLoginHistory(userId, pageNum = 1, pageSize = 10) {
    return request.get('/user/admin/userLoginHistory', {
      params: { userId, pageNum, pageSize }
    })
  },

  // 管理员功能：拉黑用户
  blacklistUser(userId) {
    return request.post('/user/admin/blacklistUser', {}, {
      params: { userId }
    })
  },

  // 管理员功能：解除拉黑用户
  unblacklistUser(userId) {
    return request.post('/user/admin/unblacklistUser', {}, {
      params: { userId }
    })
  }
}