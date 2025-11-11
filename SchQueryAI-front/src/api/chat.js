import request from './request'

export const chatApi = {
  // 创建新的聊天会话
  createSession() {
    return request({
      url: '/user/session/add',
      method: 'post'
    })
  },

  // 删除聊天会话
  deleteSession(sessionData) {
    return request({
      url: '/user/session/delete',
      method: 'delete',
      data: sessionData
    })
  },

  // 更新聊天会话
  updateSession(sessionData) {
    return request({
      url: '/user/session/update',
      method: 'post',
      data: sessionData
    })
  },

  // 获取聊天会话列表
  getSessions() {
    console.log('Making API call to get sessions...')
    return request({
      url: '/user/session/get',
      method: 'get'
    })
  },

  // 获取聊天消息历史
  getMessages(sessionId) {
    return request({
      url: '/user/message/get',
      method: 'get',
      params: { sessionId }
    })
  },

  // 发送消息 (流式响应)
  sendMessage(messageData) {
    // 先保存用户消息
    return request({
      url: '/user/message/send',
      method: 'get',
      params: {
        sessionId: messageData.sessionId,
        content: messageData.content
      },
      responseType: 'text' // 接收流式文本响应
    })
  },

  // 删除消息
  deleteMessage(messageData) {
    return request({
      url: '/user/message/delete',
      method: 'get',
      params: messageData
    })
  }
}