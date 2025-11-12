import request from './request'

/**
 * 操作日志 API
 */
export const operationLogApi = {
  /**
   * 获取最近的操作日志
   * @param {number} limit - 数量限制，默认10
   */
  getRecentLogs(limit = 10) {
    return request.get('/user/admin/recentLogs', {
      params: { limit }
    })
  },

  /**
   * 获取用户的操作日志
   * @param {string} operator - 操作人用户名
   * @param {number} pageNum - 页码
   * @param {number} pageSize - 每页大小
   */
  getUserLogs(operator, pageNum = 1, pageSize = 10) {
    return request.get('/user/admin/userLogs', {
      params: { operator, pageNum, pageSize }
    })
  },

  /**
   * 清空操作日志
   */
  clearLogs() {
    return request.post('/user/admin/clearLogs')
  }
}
