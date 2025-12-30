import request from '../request'

export function getChatModelList(params) {
  return request.get('/chat/model/list', { params })
}

export function saveChatModel(data) {
  return request.post('/chat/model/save', data)
}

export function removeChatModel(id) {
  return request.post(`/chat/model/remove/${id}`)
}

