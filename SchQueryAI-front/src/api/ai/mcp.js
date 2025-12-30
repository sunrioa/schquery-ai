import request from '../request'

export function getMcpServers() {
  return request.get('/chat/mcp/servers')
}

export function refreshMcpClients() {
  return request.post('/chat/mcp/refresh')
}

export function clearMcpClients() {
  return request.post('/chat/mcp/clear')
}

