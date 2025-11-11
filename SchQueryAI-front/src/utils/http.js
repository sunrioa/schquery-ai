import request from '../api/request'

export function useHttp() {
  return {
    get: (url, config = {}) => request.get(url, config),
    post: (url, data = {}, config = {}) => request.post(url, data, config),
    put: (url, data = {}, config = {}) => request.put(url, data, config),
    delete: (url, config = {}) => request.delete(url, config),
    patch: (url, data = {}, config = {}) => request.patch(url, data, config)
  }
}