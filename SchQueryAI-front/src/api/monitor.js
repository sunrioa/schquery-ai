import request from './request'

/**
 * 系统监控API
 */
export default {
  /**
   * 获取全部系统监控数据
   */
  getSystemMonitorData() {
    return request.get('/admin/monitor/all')
  },

  /**
   * 获取全部系统监控数据(别名)
   */
  getAllInfo() {
    return request.get('/admin/monitor/all')
  },

  /**
   * 获取CPU信息
   */
  getCpuInfo() {
    return request.get('/admin/monitor/cpu')
  },

  /**
   * 获取内存信息
   */
  getMemoryInfo() {
    return request.get('/admin/monitor/memory')
  },

  /**
   * 获取MySQL信息
   */
  getMySQLInfo() {
    return request.get('/admin/monitor/mysql')
  },

  /**
   * 获取Redis信息
   */
  getRedisInfo() {
    return request.get('/admin/monitor/redis')
  }
}
