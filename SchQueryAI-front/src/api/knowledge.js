import request from './request'

export function uploadKnowledgeFile({ title, content, metadata, file }) {
  const formData = new FormData()
  if (title) formData.append('title', title)
  if (content) formData.append('content', content)
  if (metadata) {
    formData.append('metadata', typeof metadata === 'string' ? metadata : JSON.stringify(metadata))
  }
  formData.append('file', file)

  return request.post('/admin/knowledge/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function getKnowledgeStatus(id) {
  return request.get('/admin/knowledge/status', { params: { id } })
}

