import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建axios实例
const request = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    // 定义不需要JWT令牌的公开接口
    const publicEndpoints = [
      '/user/login',
      '/user/register',
      '/user/sendRegisterCode',
      '/user/sendFindPasswordCode',
      '/user/findPassword'
    ]

    // 检查当前请求是否是公开接口
    const isPublicEndpoint = publicEndpoints.some(endpoint =>
      config.url?.includes(endpoint)
    )

    // 只有非公开接口才添加Authorization头
    if (!isPublicEndpoint) {
      const token = localStorage.getItem('token')
      console.log('Token in localStorage:', token)
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
        console.log('Added Authorization header:', config.headers.Authorization)
      } else {
        console.warn('No token found in localStorage for protected endpoint:', config.url)
      }
    } else {
      console.log('Public endpoint, skipping token:', config.url)
    }

    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    const { data } = response
    // 对于公开接口（如忘记密码），不在这里统一处理错误，让业务组件自己处理
    const publicEndpoints = [
      '/user/sendFindPasswordCode',
      '/user/findPassword'
    ]

    const isPublicEndpoint = publicEndpoints.some(endpoint =>
      response.config.url?.includes(endpoint)
    )

    if (isPublicEndpoint) {
      // 公开接口直接返回数据，让业务组件处理
      return data
    } else if (data.code === 200) {
      return data
    } else {
      ElMessage.error(data.msg || '请求失败')
      return Promise.reject(data)
    }
  },
  error => {
    // 检查401状态码或令牌过期相关的错误
    if (error.response?.status === 401 ||
        error.code === 401 ||
        error.response?.data?.msg?.includes('TOKEN_EXPIRED') ||
        error.message?.includes('TOKEN_EXPIRED') ||
        error.message?.includes('JWT expired')) {

      localStorage.removeItem('token')
      ElMessage.error('登录已过期，请重新登录')
      window.location.href = '/login'
    } else if (error.response && error.response.data) {
      ElMessage.error(error.response.data.msg || '请求失败')
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request