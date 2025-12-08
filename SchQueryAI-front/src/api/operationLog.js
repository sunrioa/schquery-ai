import request from './request'

/**
 * 操作日志 API
 */
export const operationLogApi = {
  /**
   * 分页获取所有操作日志
   * @param {number} pageNum - 页码，默认1
   * @param {number} pageSize - 每页大小，默认20
   * @param {string} action - 操作类型筛选
   * @param {string} operator - 操作人筛选
   * @param {number} status - 状态筛选
   */
  getAllLogs(pageNum = 1, pageSize = 20, action = '', operator = '', status = null) {
    return request.get('/user/admin/logs', {
      params: { pageNum, pageSize, action, operator, status }
    })
  },

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
