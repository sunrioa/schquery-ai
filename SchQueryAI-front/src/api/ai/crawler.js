import request from '@/api/request'

/**
 * 爬虫管理 API
 */
export function startCrawlApi(data = {}) {
  return request({
    url: '/ai/crawler/start',
    method: 'post',
    data
  })
}

export function stopCrawlApi() {
  return request({
    url: '/ai/crawler/stop',
    method: 'post'
  })
}

export function getCrawlerStatusApi() {
  return request({
    url: '/ai/crawler/status',
    method: 'get'
  })
}

export function getCrawlerConfigApi() {
  return request({
    url: '/ai/crawler/config',
    method: 'get'
  })
}

export function updateCrawlerConfigApi(key, value) {
  return request({
    url: '/ai/crawler/config/update',
    method: 'post',
    data: { key, value },
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    transformRequest: [function (data) {
      // 将对象转换为 URL 编码字符串
      return Object.keys(data)
        .map(function (k) {
          return encodeURIComponent(k) + '=' + encodeURIComponent(data[k])
        })
        .join('&')
    }]
  })
}

export function generateCronApi(params) {
  return request({
    url: '/ai/crawler/cron/generate',
    method: 'post',
    data: params,
    headers: {
      'Content-Type': 'application/json'
    }
  })
}

export function getCronNextExecutionApi(data) {
  return request({
    url: '/ai/crawler/cron/next-execution',
    method: 'post',
    data
  })
}

export function getCrawlerResultsApi() {
  return request({
    url: '/ai/crawler/results',
    method: 'get'
  })
}

export function updateCrawlerResultApi(data) {
  return request({
    url: '/ai/crawler/results/update',
    method: 'post',
    data
  })
}

export function saveCrawlerResultsApi(data) {
  return request({
    url: '/ai/crawler/results/save',
    method: 'post',
    data
  })
}

export function clearCrawlerResultsApi() {
  return request({
    url: '/ai/crawler/results/clear',
    method: 'post'
  })
}
