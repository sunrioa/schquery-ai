/**
 * 浏览器信息解析工具
 * 从User-Agent字符串解析出浏览器名称和版本
 */

/**
 * 解析User-Agent获取浏览器和操作系统信息
 * @param {string} userAgent - User-Agent字符串
 * @returns {Object} 包含browser和os信息的对象
 */
export function parseBrowserInfo(userAgent) {
  if (!userAgent) {
    return {
      browser: '未知浏览器',
      version: '',
      os: '未知系统'
    }
  }

  let browserName = '未知浏览器'
  let browserVersion = ''
  let osName = '未知系统'

  // 检测操作系统
  if (/Windows NT 10.0/.test(userAgent)) {
    osName = 'Windows 10'
  } else if (/Windows NT 6.3/.test(userAgent)) {
    osName = 'Windows 8.1'
  } else if (/Windows NT 6.2/.test(userAgent)) {
    osName = 'Windows 8'
  } else if (/Windows NT 6.1/.test(userAgent)) {
    osName = 'Windows 7'
  } else if (/Mac/.test(userAgent)) {
    osName = 'macOS'
  } else if (/Linux/.test(userAgent)) {
    osName = 'Linux'
  } else if (/iPhone|iPad|iPod/.test(userAgent)) {
    osName = 'iOS'
  } else if (/Android/.test(userAgent)) {
    osName = 'Android'
  }

  // 检测浏览器 (需要按照特定顺序检测，因为有些浏览器的UA会包含其他浏览器的关键词)
  
  // Edge (需要在Chrome之前)
  if (/Edg/.test(userAgent)) {
    browserName = 'Edge'
    const match = userAgent.match(/Edg[e]?\/(\d+)/)
    browserVersion = match ? match[1] : ''
  }
  // Firefox
  else if (/Firefox/.test(userAgent)) {
    browserName = 'Firefox'
    const match = userAgent.match(/Firefox\/(\d+)/)
    browserVersion = match ? match[1] : ''
  }
  // Chrome (需要在Safari之前)
  else if (/Chrome/.test(userAgent)) {
    browserName = 'Chrome'
    const match = userAgent.match(/Chrome\/(\d+)/)
    browserVersion = match ? match[1] : ''
  }
  // Safari
  else if (/Safari/.test(userAgent)) {
    browserName = 'Safari'
    const match = userAgent.match(/Version\/(\d+)/)
    browserVersion = match ? match[1] : ''
  }
  // Opera
  else if (/OPR/.test(userAgent)) {
    browserName = 'Opera'
    const match = userAgent.match(/OPR\/(\d+)/)
    browserVersion = match ? match[1] : ''
  }
  // IE
  else if (/Trident/.test(userAgent)) {
    browserName = 'Internet Explorer'
    const match = userAgent.match(/rv:(\d+)/)
    browserVersion = match ? match[1] : ''
  }

  return {
    browser: browserName,
    version: browserVersion,
    os: osName
  }
}

/**
 * 格式化浏览器信息展示
 * @param {string} userAgent - User-Agent字符串
 * @returns {string} 格式化后的浏览器信息
 */
export function formatBrowserInfo(userAgent) {
  const info = parseBrowserInfo(userAgent)
  
  if (info.version) {
    return `${info.browser} ${info.version}`
  }
  return info.browser
}

/**
 * 获取简洁的浏览器信息展示（只显示浏览器名称和版本）
 * @param {string} userAgent - User-Agent字符串
 * @returns {string} 简洁的浏览器信息
 */
export function getBrowserDisplay(userAgent) {
  return formatBrowserInfo(userAgent)
}

/**
 * 获取完整的设备信息展示（包括浏览器和操作系统）
 * @param {string} userAgent - User-Agent字符串
 * @returns {string} 完整的设备信息
 */
export function getFullDeviceInfo(userAgent) {
  const info = parseBrowserInfo(userAgent)
  
  let display = ''
  
  if (info.version) {
    display = `${info.browser} ${info.version}`
  } else {
    display = info.browser
  }
  
  display += ` / ${info.os}`
  
  return display
}
