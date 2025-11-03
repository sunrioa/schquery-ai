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
  }
}