/**
 * 客服消息WebSocket客户端
 * 封装WebSocket连接和消息处理逻辑，支持用户和管理员两种角色
 */

class CustomerServiceClient {
  constructor(options = {}) {
    this.ws = null
    this.url = options.url || 'ws://localhost:8080/ws/customer-service'
    this.userId = options.userId || null
    this.userType = options.userType || 'user' // 'user' or 'admin'
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.reconnectDelay = 3000
    this.heartbeatInterval = null
    this.heartbeatTimeout = 30000
    this.messageHandlers = new Map()
    this.connected = false
    this.isManualClose = false
  }

  /**
   * 连接到WebSocket服务器
   */
  connect() {
    return new Promise((resolve, reject) => {
      try {
        // 构建连接URL
        const params = new URLSearchParams({
          userId: this.userId,
          userType: this.userType
        })
        const wsUrl = `${this.url}?${params.toString()}`

        this.ws = new WebSocket(wsUrl)

        // 连接打开
        this.ws.onopen = () => {
          console.log('[CustomerServiceClient] WebSocket连接已建立')
          this.connected = true
          this.reconnectAttempts = 0
          this.startHeartbeat()
          this.emit('connected')
          resolve()
        }

        // 接收消息
        this.ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data)
            this.handleMessage(data)
          } catch (error) {
            console.error('[CustomerServiceClient] 解析消息失败:', error)
          }
        }

        // 连接错误
        this.ws.onerror = (error) => {
          console.error('[CustomerServiceClient] WebSocket错误:', error)
          this.connected = false
          this.emit('error', error)
          reject(error)
        }

        // 连接关闭
        this.ws.onclose = () => {
          console.log('[CustomerServiceClient] WebSocket连接已关闭')
          this.connected = false
          this.stopHeartbeat()
          this.emit('disconnected')

          // 自动重连（除非是手动关闭）
          if (!this.isManualClose) {
            this.attemptReconnect()
          }
        }
      } catch (error) {
        console.error('[CustomerServiceClient] 连接失败:', error)
        reject(error)
      }
    })
  }

  /**
   * 尝试重连
   */
  attemptReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      console.log(`[CustomerServiceClient] 尝试第 ${this.reconnectAttempts} 次重连...`)
      setTimeout(() => {
        this.connect().catch(error => {
          console.error('[CustomerServiceClient] 重连失败:', error)
        })
      }, this.reconnectDelay)
    } else {
      console.error('[CustomerServiceClient] 达到最大重连次数，停止重连')
      this.emit('reconnect_failed')
    }
  }

  /**
   * 发送消息
   */
  sendMessage(data) {
    return new Promise((resolve, reject) => {
      if (!this.connected) {
        reject(new Error('WebSocket未连接'))
        return
      }

      try {
        const message = {
          type: 'message',
          timestamp: new Date().getTime(),
          ...data
        }
        this.ws.send(JSON.stringify(message))
        resolve()
      } catch (error) {
        reject(error)
      }
    })
  }

  /**
   * 处理接收的消息
   */
  handleMessage(data) {
    const messageType = data.type

    // 根据消息类型分发
    this.emit('raw_message', data)
    
    switch (messageType) {
      case 'user_message_received':
        // 管理员接收到用户消息
        this.emit('user_message', data)
        break
      case 'admin_reply':
        // 用户接收到管理员回复
        this.emit('admin_reply', data)
        break
      case 'admin_connected':
        this.emit('admin_connected', data)
        break
      case 'admin_replied':
        this.emit('admin_replied', data)
        break
      case 'typing':
        this.emit('typing', data)
        break
      case 'typing_end':
        this.emit('typing_end', data)
        break
      case 'pong':
        this.resetHeartbeat()
        break
      default:
        console.warn('[CustomerServiceClient] 未知消息类型:', messageType, data)
    }
  }

  /**
   * 注册消息处理器
   */
  on(eventName, handler) {
    if (!this.messageHandlers.has(eventName)) {
      this.messageHandlers.set(eventName, [])
    }
    this.messageHandlers.get(eventName).push(handler)
  }

  /**
   * 注销消息处理器
   */
  off(eventName, handler) {
    if (this.messageHandlers.has(eventName)) {
      const handlers = this.messageHandlers.get(eventName)
      const index = handlers.indexOf(handler)
      if (index > -1) {
        handlers.splice(index, 1)
      }
    }
  }

  /**
   * 触发事件
   */
  emit(eventName, data) {
    if (this.messageHandlers.has(eventName)) {
      const handlers = this.messageHandlers.get(eventName)
      handlers.forEach(handler => {
        try {
          handler(data)
        } catch (error) {
          console.error(`[CustomerServiceClient] 事件处理器出错 (${eventName}):`, error)
        }
      })
    }
  }

  /**
   * 启动心跳检测
   */
  startHeartbeat() {
    this.heartbeatInterval = setInterval(() => {
      if (this.connected) {
        try {
          this.ws.send(JSON.stringify({ type: 'ping' }))
        } catch (error) {
          console.error('[CustomerServiceClient] 发送心跳失败:', error)
        }
      }
    }, this.heartbeatTimeout)
  }

  /**
   * 停止心跳检测
   */
  stopHeartbeat() {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval)
      this.heartbeatInterval = null
    }
  }

  /**
   * 重置心跳
   */
  resetHeartbeat() {
    // 可选：重置心跳计时器
    // this.stopHeartbeat()
    // this.startHeartbeat()
  }

  /**
   * 关闭连接
   */
  close() {
    this.isManualClose = true
    if (this.ws) {
      this.ws.close()
    }
    this.stopHeartbeat()
  }

  /**
   * 获取连接状态
   */
  isConnected() {
    return this.connected && this.ws?.readyState === WebSocket.OPEN
  }

  /**
   * 设置用户信息
   */
  setUserInfo(userId, userType) {
    this.userId = userId
    this.userType = userType
  }
}

/**
 * 创建全局单例实例
 */
let instance = null

export function createCustomerServiceClient(options = {}) {
  if (!instance) {
    instance = new CustomerServiceClient(options)
  }
  return instance
}

export function getCustomerServiceClient() {
  if (!instance) {
    instance = new CustomerServiceClient()
  }
  return instance
}

export default CustomerServiceClient
