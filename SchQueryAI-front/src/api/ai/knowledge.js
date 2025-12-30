import request from '../request'

// 知识库
export function getKnowledgeList(params) {
  return request.get('/knowledge/list', { params })
}

export function getKnowledgeDetail(id) {
  return request.get(`/knowledge/detail/${id}`)
}

export function saveKnowledge(data) {
  return request.post('/knowledge/save', data)
}

export function removeKnowledge(id) {
  return request.post(`/knowledge/remove/${id}`)
}

// 知识库附件（文档）
export function uploadKnowledgeAttach({ knowledgeId, title, content, metadata, file }) {
  const formData = new FormData()
  if (knowledgeId != null) formData.append('knowledgeId', knowledgeId)
  if (title) formData.append('title', title)
  if (content) formData.append('content', content)
  if (metadata) formData.append('metadata', metadata)
  formData.append('file', file)

  return request.post('/knowledge/attach/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function getKnowledgeAttachList(knowledgeId) {
  return request.get(`/knowledge/attach/list/${knowledgeId}`)
}

export function getKnowledgeAttachInfo(id) {
  return request.get(`/knowledge/attach/info/${id}`)
}

export function rebuildKnowledgeAttach(data) {
  return request.post('/knowledge/attach/rebuild', data)
}

export function reindexKnowledgeAttach(docId) {
  return request.post(`/knowledge/attach/reindex/${docId}`)
}

export function removeKnowledgeAttach(id) {
  return request.post(`/knowledge/attach/remove/${id}`)
}

// 知识片段
export function getKnowledgeFragmentList(docId) {
  return request.get(`/knowledge/fragment/list/${docId}`)
}

export function updateKnowledgeFragmentContent(data) {
  return request.post('/knowledge/fragment/updateContent', data)
}
