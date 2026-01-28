import request from '../request'

export function getAsrModelList(params) {
  return request.get('/asr/model/list', { params })
}

export function saveAsrModel(data) {
  return request.post('/asr/model/save', data)
}

export function removeAsrModel(id) {
  return request.post(`/asr/model/remove/${id}`)
}
