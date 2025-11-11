/**
 * 权限和用户身份管理工具
 */

// 用户角色常量
export const USER_ROLES = {
  ADMIN: 'admin',      // 管理员
  WORKER: 'worker',    // 员工
  USER: 'user',        // 普通用户
}

/**
 * 获取当前用户角色
 * @returns {string|null} 用户角色
 */
export function getUserRole() {
  return localStorage.getItem('userRole')
}

/**
 * 检查用户是否是管理员
 * @returns {boolean} 是否是管理员
 */
export function isAdmin() {
  const role = getUserRole()
  return role === USER_ROLES.ADMIN
}

/**
 * 检查用户是否是普通用户
 * @returns {boolean} 是否是普通用户
 */
export function isUser() {
  const role = getUserRole()
  return role === USER_ROLES.USER
}

/**
 * 检查用户是否是员工
 * @returns {boolean} 是否是员工
 */
export function isWorker() {
  const role = getUserRole()
  return role === USER_ROLES.WORKER
}

/**
 * 检查用户是否有指定角色
 * @param {string} requiredRole 需要的角色
 * @returns {boolean} 是否有该角色
 */
export function hasRole(requiredRole) {
  const role = getUserRole()
  return role === requiredRole
}

/**
 * 检查用户是否有任一角色
 * @param {string[]} roles 角色数组
 * @returns {boolean} 是否有任一角色
 */
export function hasAnyRole(roles) {
  const role = getUserRole()
  return roles.includes(role)
}

/**
 * 检查用户是否是管理员或员工（工作人员角色）
 * @returns {boolean} 是否是管理员或员工
 */
export function isStaff() {
  const role = getUserRole()
  return role === USER_ROLES.ADMIN || role === USER_ROLES.WORKER
}

/**
 * 获取角色显示名称
 * @param {string} role 角色值
 * @returns {string} 角色显示名称
 */
export function getRoleDisplayName(role) {
  switch (role) {
    case USER_ROLES.ADMIN:
      return '管理员'
    case USER_ROLES.WORKER:
      return '员工'
    case USER_ROLES.USER:
      return '普通用户'
    default:
      return '未知角色'
  }
}

/**
 * 获取当前用户信息
 * @returns {object} 用户信息对象
 */
export function getCurrentUser() {
  return {
    token: localStorage.getItem('token'),
    role: localStorage.getItem('userRole'),
    userName: localStorage.getItem('userName'),
    userId: localStorage.getItem('userId')
  }
}

/**
 * 检查用户是否已登录
 * @returns {boolean} 是否已登录
 */
export function isLoggedIn() {
  const token = localStorage.getItem('token')
  return !!token
}

/**
 * 清除用户登录信息
 */
export function clearUserInfo() {
  localStorage.removeItem('token')
  localStorage.removeItem('userRole')
  localStorage.removeItem('userName')
  localStorage.removeItem('userId')
  localStorage.removeItem('rememberedUser')
}

/**
 * 权限检查高阶组件示例（Vue 3）
 * @param {string[]} allowedRoles 允许的角色数组
 * @param {object} fallbackOptions 回退选项
 * @returns {Function} 高阶函数
 */
export function requireAuth(allowedRoles = [], fallbackOptions = {}) {
  return {
    beforeRouteEnter(to, from, next) {
      if (!isLoggedIn()) {
        next('/login')
        return
      }

      if (allowedRoles.length > 0 && !hasAnyRole(allowedRoles)) {
        // 根据配置决定跳转路径
        const fallbackPath = fallbackOptions.fallbackPath || '/unauthorized'
        next(fallbackPath)
        return
      }

      next()
    }
  }
}

/**
 * 创建权限指令（Vue 3 指令示例）
 */
export const authDirective = {
  mounted(el, binding) {
    const { value } = binding

    if (value && value.roles && value.roles.length > 0) {
      const hasPermission = hasAnyRole(value.roles)
      if (!hasPermission && value.fallback) {
        // 如果没有权限且有回退内容，显示回退内容
        el.innerHTML = value.fallback
        el.style.display = 'block'
      } else if (!hasPermission) {
        // 如果没有权限，隐藏元素
        el.style.display = 'none'
      }
    }
  }
}