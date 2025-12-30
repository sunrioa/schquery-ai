import request from '../request'

export function getChatDefaultConfig() {
  return request.get('/chat/config/default')
}

export function saveChatDefaultConfig(data) {
  return request.put('/chat/config/default', data)
}

