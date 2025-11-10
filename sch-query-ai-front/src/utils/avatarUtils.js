/**
 * 头像工具类
 */

// 默认头像路径
export const DEFAULT_USER_AVATAR = '/default-user-avatar.jpg'
export const AI_AVATAR = '/robot-avatar.png'

// 默认头像base64数据 (备用，如果静态资源加载失败时使用)
export const DEFAULT_AVATAR_FALLBACK = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIiByeD0iNTAiIGZpbGw9IiNGN0Y4RkEiLz4KPGNpcmNsZSBjeD0iNTAiIGN5PSI0MCIgcj0iMTgiIGZpbGw9IiM4NjkwOUMiLz4KPHBhdGggZD0iTTI1IDc1QzI1IDY2LjUgMzYuNSA2MCA1MCA2MEM2My41IDYwIDc1IDY2LjUgNzUgNzVWNzVIMjVWNzVaIiBmaWxsPSIjODY5MDlDIi8+Cjwvc3ZnPgo='

/**
 * 获取用户头像，如果没有则返回默认头像
 * @param {string} userAvatar - 用户头像base64
 * @returns {string} 头像URL
 */
export function getUserAvatar(userAvatar) {
  return userAvatar || DEFAULT_USER_AVATAR
}

/**
 * 获取AI头像
 * @returns {string} AI头像URL
 */
export function getAIAvatar() {
  return AI_AVATAR
}

/**
 * 生成用户名首字母头像
 * @param {string} username - 用户名
 * @returns {string} SVG头像base64
 */
export function generateAvatarFromUsername(username) {
  if (!username) return DEFAULT_USER_AVATAR

  const firstChar = username.charAt(0).toUpperCase()
  const colors = ['#165DFF', '#00B42A', '#FF7D00', '#F53F3F', '#722ED1', '#F77234']
  const colorIndex = firstChar.charCodeAt(0) % colors.length
  const bgColor = colors[colorIndex]

  const svg = `
    <svg width="100" height="100" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="100" height="100" rx="50" fill="${bgColor}"/>
      <text x="50" y="65" text-anchor="middle" font-size="36" font-weight="600" fill="white">${firstChar}</text>
    </svg>
  `

  return `data:image/svg+xml;base64,${btoa(unescape(encodeURIComponent(svg)))}`
}

/**
 * 获取最佳头像显示方案
 * @param {string} userAvatar - 用户头像base64
 * @param {string} username - 用户名
 * @returns {string} 头像URL
 */
export function getBestAvatar(userAvatar, username) {
  if (userAvatar) {
    return userAvatar
  }

  if (username) {
    return generateAvatarFromUsername(username)
  }

  return DEFAULT_USER_AVATAR
}