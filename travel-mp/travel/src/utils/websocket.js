/**
 * WebSocket 工具类
 * 用于接收后端推送的 AI 消息
 */

class WebSocketManager {
  constructor() {
    this.socket = null
    this.userId = null
    this.reconnectTimer = null
    this.reconnectInterval = 3000 // 重连间隔 3秒
    this.isConnected = false
    this.messageHandlers = []
  }

  /**
   * 连接 WebSocket
   * @param {number} userId - 用户ID
   */
  connect(userId) {
    if (this.socket && this.isConnected) {
      console.log('WebSocket 已连接')
      return
    }

    this.userId = userId
    const url = `ws://10.63.45.65:9999/ws/message?userId=${userId}`

    console.log('正在连接 WebSocket:', url)

    this.socket = uni.connectSocket({
      url: url,
      success: () => {
        console.log('WebSocket 连接请求已发送')
      },
      fail: (err) => {
        console.error('WebSocket 连接失败:', err)
        this.scheduleReconnect()
      }
    })

    // 监听连接打开
    this.socket.onOpen(() => {
      console.log('WebSocket 连接已打开')
      this.isConnected = true
      this.cancelReconnect()
      // 发送心跳
      this.startHeartbeat()
    })

    // 监听消息
    this.socket.onMessage((res) => {
      console.log('WebSocket 收到消息:', res.data)
      try {
        const data = JSON.parse(res.data)
        this.notifyHandlers(data)
      } catch (e) {
        console.error('解析 WebSocket 消息失败:', e)
      }
    })

    // 监听连接关闭
    this.socket.onClose(() => {
      console.log('WebSocket 连接已关闭')
      this.isConnected = false
      this.cancelHeartbeat()
      this.scheduleReconnect()
    })

    // 监听错误
    this.socket.onError((err) => {
      console.error('WebSocket 错误:', err)
      this.isConnected = false
    })
  }

  /**
   * 断开连接
   */
  disconnect() {
    this.cancelReconnect()
    this.cancelHeartbeat()
    if (this.socket) {
      this.socket.close()
      this.socket = null
    }
    this.isConnected = false
    this.userId = null
  }

  /**
   * 添加消息处理器
   * @param {Function} handler - 回调函数，接收消息数据
   */
  addMessageHandler(handler) {
    if (typeof handler === 'function' && !this.messageHandlers.includes(handler)) {
      this.messageHandlers.push(handler)
    }
  }

  /**
   * 移除消息处理器
   * @param {Function} handler - 回调函数
   */
  removeMessageHandler(handler) {
    const index = this.messageHandlers.indexOf(handler)
    if (index > -1) {
      this.messageHandlers.splice(index, 1)
    }
  }

  /**
   * 通知所有处理器
   * @param {Object} data - 消息数据
   */
  notifyHandlers(data) {
    this.messageHandlers.forEach(handler => {
      try {
        handler(data)
      } catch (e) {
        console.error('消息处理器执行失败:', e)
      }
    })
  }

  /**
   * 发送消息（用于心跳等）
   * @param {Object} data - 消息数据
   */
  send(data) {
    if (this.socket && this.isConnected) {
      this.socket.send({
        data: JSON.stringify(data),
        fail: (err) => {
          console.error('WebSocket 发送消息失败:', err)
        }
      })
    }
  }

  /**
   * 启动心跳
   */
  startHeartbeat() {
    this.heartbeatTimer = setInterval(() => {
      this.send({ type: 'ping' })
    }, 30000) // 每 30 秒发送一次心跳
  }

  /**
   * 取消心跳
   */
  cancelHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  /**
   * 安排重连
   */
  scheduleReconnect() {
    if (this.reconnectTimer || !this.userId) return
    console.log(`${this.reconnectInterval / 1000} 秒后尝试重连...`)
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      if (this.userId) {
        this.connect(this.userId)
      }
    }, this.reconnectInterval)
  }

  /**
   * 取消重连
   */
  cancelReconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }
}

// 单例
const wsManager = new WebSocketManager()

export default wsManager
