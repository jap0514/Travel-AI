"use strict";
const common_vendor = require("../common/vendor.js");
class WebSocketManager {
  constructor() {
    this.socket = null;
    this.userId = null;
    this.reconnectTimer = null;
    this.reconnectInterval = 3e3;
    this.isConnected = false;
    this.messageHandlers = [];
  }
  /**
   * 连接 WebSocket
   * @param {number} userId - 用户ID
   */
  connect(userId) {
    if (this.socket && this.isConnected) {
      console.log("WebSocket 已连接");
      return;
    }
    this.userId = userId;
    const url = `ws://localhost:9999/ws/message?userId=${userId}`;
    console.log("正在连接 WebSocket:", url);
    this.socket = common_vendor.index.connectSocket({
      url,
      success: () => {
        console.log("WebSocket 连接请求已发送");
      },
      fail: (err) => {
        console.error("WebSocket 连接失败:", err);
        this.scheduleReconnect();
      }
    });
    this.socket.onOpen(() => {
      console.log("WebSocket 连接已打开");
      this.isConnected = true;
      this.cancelReconnect();
      this.startHeartbeat();
    });
    this.socket.onMessage((res) => {
      console.log("WebSocket 收到消息:", res.data);
      try {
        const data = JSON.parse(res.data);
        this.notifyHandlers(data);
      } catch (e) {
        console.error("解析 WebSocket 消息失败:", e);
      }
    });
    this.socket.onClose(() => {
      console.log("WebSocket 连接已关闭");
      this.isConnected = false;
      this.cancelHeartbeat();
      this.scheduleReconnect();
    });
    this.socket.onError((err) => {
      console.error("WebSocket 错误:", err);
      this.isConnected = false;
    });
  }
  /**
   * 断开连接
   */
  disconnect() {
    this.cancelReconnect();
    this.cancelHeartbeat();
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
    this.isConnected = false;
    this.userId = null;
  }
  /**
   * 添加消息处理器
   * @param {Function} handler - 回调函数，接收消息数据
   */
  addMessageHandler(handler) {
    if (typeof handler === "function" && !this.messageHandlers.includes(handler)) {
      this.messageHandlers.push(handler);
    }
  }
  /**
   * 移除消息处理器
   * @param {Function} handler - 回调函数
   */
  removeMessageHandler(handler) {
    const index = this.messageHandlers.indexOf(handler);
    if (index > -1) {
      this.messageHandlers.splice(index, 1);
    }
  }
  /**
   * 通知所有处理器
   * @param {Object} data - 消息数据
   */
  notifyHandlers(data) {
    this.messageHandlers.forEach((handler) => {
      try {
        handler(data);
      } catch (e) {
        console.error("消息处理器执行失败:", e);
      }
    });
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
          console.error("WebSocket 发送消息失败:", err);
        }
      });
    }
  }
  /**
   * 启动心跳
   */
  startHeartbeat() {
    this.heartbeatTimer = setInterval(() => {
      this.send({ type: "ping" });
    }, 3e4);
  }
  /**
   * 取消心跳
   */
  cancelHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }
  /**
   * 安排重连
   */
  scheduleReconnect() {
    if (this.reconnectTimer || !this.userId) return;
    console.log(`${this.reconnectInterval / 1e3} 秒后尝试重连...`);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      if (this.userId) {
        this.connect(this.userId);
      }
    }, this.reconnectInterval);
  }
  /**
   * 取消重连
   */
  cancelReconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }
}
const wsManager = new WebSocketManager();
exports.wsManager = wsManager;
