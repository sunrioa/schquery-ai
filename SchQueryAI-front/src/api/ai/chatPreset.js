import request from '../request'

export function getChatPresetList(params) {
  return request.get('/chat/preset/list', { params })
}

export function saveChatPreset(data) {
  return request.post('/chat/preset/save', data)
}

export function removeChatPreset(id) {
  return request.post(`/chat/preset/remove/${id}`)
}

export function getDefaultChatPresetId() {
  return request.get('/chat/preset/default')
}

export function setDefaultChatPresetId(id) {
  return request.post(`/chat/preset/default/${id}`)
}

