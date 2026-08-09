<template>
  <view class="chat-container">
    <!-- 顶部Tab -->
    <view class="chat-tabs">
      <view
        class="tab-item"
        :class="{ active: currentTab === 'chat' }"
        @click="switchTab('chat')"
      >AI对话</view>
      <view
        class="tab-item"
        :class="{ active: currentTab === 'history' }"
        @click="switchTab('history')"
      >历史会话</view>
      <view class="tab-add" @click="createNewSession">+</view>
    </view>

    <!-- AI对话 -->
    <view v-if="currentTab === 'chat'" class="chat-main">
      <!-- 欢迎页 -->
      <view v-if="messages.length === 0" class="welcome-area">
        <image class="welcome-icon" src="/static/images/ai-robot.png" mode="aspectFit"></image>
        <text class="welcome-title">我是AI旅行助手</text>
        <text class="welcome-desc">告诉我你想去哪里旅行，我会为你规划专属行程</text>

        <button class="start-plan-btn" @click="showPlanForm">开始规划行程</button>

        <view class="quick-questions">
          <text class="quick-title">试试这样问我：</text>
          <text class="quick-item" @click="sendQuickQuestion('我想去北京玩3天，推荐一下行程')">我想去北京玩3天，推荐一下行程</text>
          <text class="quick-item" @click="sendQuickQuestion('三亚5日游，预算5000元怎么安排')">三亚5日游，预算5000元怎么安排</text>
          <text class="quick-item" @click="sendQuickQuestion('杭州适合周末亲子游吗')">杭州适合周末亲子游吗</text>
        </view>
      </view>

      <!-- 消息列表 -->
      <scroll-view
        v-else
        class="message-list"
        scroll-y
        :scroll-top="scrollTop"
        :scroll-into-view="scrollIntoView"
      >
        <view v-for="(msg, index) in messages" :key="index" :id="'msg-' + index">
          <!-- 用户消息 -->
          <view v-if="msg.role === 'USER'" class="message-item user">
            <image class="msg-avatar" :src="userInfo.avatar || '/static/images/avatar-default.png'" mode="aspectFill"></image>
            <view class="msg-bubble user-bubble">{{ msg.content }}</view>
          </view>

          <!-- AI消息 -->
          <view v-else class="message-item ai">
            <image class="msg-avatar" src="/static/images/ai-avatar.png" mode="aspectFill"></image>
            <view class="msg-bubble ai-bubble">
              <text v-if="msg.type === 'text'">{{ msg.content }}</text>
              <!-- 行程规划卡片 -->
              <view v-else-if="msg.type === 'plan'" class="plan-card">
                <view class="plan-header">
                  <text class="plan-title">{{ msg.plan.title }}</text>
                  <text class="plan-destination">{{ msg.plan.destination }}</text>
                </view>
                <view class="plan-days">{{ msg.plan.days }}天行程</view>
                <view class="plan-budget">预算: {{ msg.plan.budget }}</view>
                <view class="plan-details">
                  <view v-for="(day, dIndex) in msg.plan.daily_plans" :key="dIndex" class="day-item">
                    <view class="plan-day-title">Day {{ day.day }} · {{ day.theme }}</view>
                    <view v-for="(act, aIndex) in day.activities" :key="aIndex" class="activity-item">
                      <view class="activity-time">{{ act.time }}</view>
                      <view class="activity-body">
                        <view class="activity-name">{{ act.name }}</view>
                        <view class="activity-desc">{{ act.description }}</view>
                        <view class="activity-loc" v-if="act.location">📍 {{ act.location }}</view>
                      </view>
                    </view>
                    <view v-if="day.estimated_cost" class="day-cost">当日预算约 ¥{{ day.estimated_cost }}</view>
                  </view>
                </view>
                <view class="plan-total" v-if="msg.plan.total_estimated_cost">
                  总预算约 ¥{{ msg.plan.total_estimated_cost }}
                </view>
                <button class="book-hotel-btn" @click="goBookHotel(msg.plan.destination)">预订酒店</button>
              </view>
            </view>
          </view>
        </view>

        <!-- 加载中 -->
        <view v-if="isLoading" class="message-item ai loading">
          <image class="msg-avatar" src="/static/images/ai-avatar.png" mode="aspectFill"></image>
          <view class="loading-dots">
            <text class="dot">.</text>
            <text class="dot">.</text>
            <text class="dot">.</text>
          </view>
        </view>
      </scroll-view>

      <!-- 输入区域 -->
      <view class="input-area">
        <input
          class="message-input"
          v-model="inputMessage"
          placeholder="输入你的旅行需求..."
          confirm-type="send"
          @confirm="sendMessage"
        />
        <button class="send-btn" :disabled="!inputMessage || isLoading" @click="sendMessage">发送</button>
      </view>
    </view>

    <!-- 历史会话 -->
    <view v-else class="history-main">
      <view v-if="sessions.length === 0" class="empty-state">
        <image class="empty-icon" src="/static/images/empty-chat.png" mode="aspectFit"></image>
        <text class="empty-text">暂无历史会话</text>
      </view>

      <view v-else class="session-list">
        <view v-for="session in sessions" :key="session.id" class="session-item" @click="openSession(session)">
          <view class="session-content">
            <text class="session-title">{{ session.title }}</text>
            <text class="session-time">{{ formatTime(session.createTime) }}</text>
          </view>
          <text class="session-arrow">></text>
        </view>
      </view>
    </view>

    <!-- 入住人信息弹层：后端 interaction.type=guest_contact && status=waiting_user_contact 时显示 -->
    <view v-if="contactModalVisible" class="modal-mask">
      <view class="contact-modal">
        <view class="modal-title">填写入住人信息</view>
        <view class="modal-hotel" v-if="pendingContact">
          {{ pendingContact.hotel_name || pendingContact.hotelName }}
        </view>
        <view class="form-item">
          <text class="form-label">姓名</text>
          <input
            class="form-input"
            v-model="contactForm.guestName"
            placeholder="请输入入住人姓名"
            maxlength="20"
          />
        </view>
        <view class="form-item">
          <text class="form-label">手机号</text>
          <input
            class="form-input"
            v-model="contactForm.guestPhone"
            placeholder="请输入 11 位手机号"
            type="number"
            maxlength="11"
          />
        </view>
        <text v-if="contactError" class="contact-error">{{ contactError }}</text>
        <view class="modal-actions">
          <button class="modal-confirm" @click="submitContact">提交并预订</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { getUserSessions, getSessionMessages, createSession, sendMessage } from '@/api/request.js'
import wsManager from '@/utils/websocket.js'

export default {
  data() {
    return {
      currentTab: 'chat',
      inputMessage: '',
      messages: [],
      sessions: [],
      isLoading: false,
      scrollTop: 0,
      scrollIntoView: '',
      userInfo: {},
      currentSessionId: null,
      // 入住人信息弹层（后端 interaction.type=guest_contact && status=waiting_user_contact 时打开）
      contactModalVisible: false,
      contactForm: {
        guestName: '',
        guestPhone: ''
      },
      contactError: '',
      pendingContact: null,
      // 行程信息
      travelInfo: {
        destination: '',
        startDate: '',
        days: 3
      }
    }
  },
  onLoad(options) {
    this.userInfo = uni.getStorageSync('userInfo') || {}
    // 加载行程信息
    const travelInfo = uni.getStorageSync('travelPlanInfo')
    if (travelInfo) {
      this.travelInfo = travelInfo
    }
    if (options.sessionId) {
      // 有传入 sessionId，加载指定会话
      // 页面参数恒为字符串，转成数字，与后端下发的 sessionId(Long) 类型保持一致
      this.currentSessionId = Number(options.sessionId)
      this.loadSession(options.sessionId)
    } else {
      // 没有传入 sessionId，加载最近的会话
      this.loadLatestSession()
    }
  },
  onShow() {
    // 页面显示时连接 WebSocket
    if (this.userInfo && this.userInfo.id) {
      this.connectWebSocket()
    }
  },
  onUnload() {
    // 页面卸载时断开 WebSocket
    this.disconnectWebSocket()
  },
  methods: {
    // ==================== WebSocket ====================
    connectWebSocket() {
      const userId = this.userInfo.id
      if (!userId) return

      // 添加消息处理器
      wsManager.addMessageHandler(this.handleWebSocketMessage)

      // 连接
      wsManager.connect(userId)
    },

    disconnectWebSocket() {
      wsManager.removeMessageHandler(this.handleWebSocketMessage)
      wsManager.disconnect()
    },

    handleWebSocketMessage(data) {
      console.log('WebSocket 收到消息:', data)

      // 判断是否是当前会话的消息
      if (data.sessionId !== this.currentSessionId) {
        console.log('不是当前会话的消息，忽略')
        return
      }

      // planJson 是 JSON 字符串，解析成对象给模板用；解析失败时降级为 text
      let plan = null
      if (data.planJson) {
        try { plan = JSON.parse(data.planJson) } catch (e) { plan = null }
      }
      this.messages.push({
        role: 'ASSISTANT',
        type: plan ? 'plan' : 'text',
        content: data.content,
        planJson: data.planJson,
        plan: plan,
        interaction: data.interaction
      })

      // push 之后再检查整个消息列表，避免后端回声导致重复弹窗
      this.checkContactInteraction()

      this.scrollToBottom()
    },

    // 扫描整个 messages 列表，决定是否打开入住人信息弹层
    // 规则：找到最后一条处于 waiting_user_contact 的 assistant 消息，
    //      且其后没有任何 user 消息提交过（即用户还没回应），才弹窗
    checkContactInteraction() {
      for (let i = this.messages.length - 1; i >= 0; i--) {
        const m = this.messages[i]
        if (m.role === 'USER') {
          // 在最后一条 assistant waiting 之后已有用户消息 → 已响应，不弹
          return
        }
        if (m.role === 'ASSISTANT') {
          const obj = this.parseInteraction(m.interaction)
          if (obj && obj.type === 'guest_contact' && obj.status === 'waiting_user_contact') {
            this.pendingContact = obj
            this.contactForm = { guestName: '', guestPhone: '' }
            this.contactError = ''
            this.contactModalVisible = true
          }
          return
        }
      }
    },

    // 把 interaction 字段统一成对象（历史加载可能是 JSON 字符串）
    parseInteraction(raw) {
      if (!raw) return null
      if (typeof raw === 'object') return raw
      if (typeof raw === 'string') {
        try { return JSON.parse(raw) } catch (e) { return null }
      }
      return null
    },

    // ==================== Tab 切换 ====================
    switchTab(tab) {
      this.currentTab = tab
      if (tab === 'history') {
        this.loadSessions()
      }
    },

    // ==================== 行程规划 ====================
    showPlanForm() {
      uni.navigateTo({
        url: '/pages/chat/plan-form'
      })
    },

    async startPlan() {
      if (!this.travelInfo.destination) {
        uni.showToast({ title: '请输入目的地', icon: 'none' })
        return
      }
      if (!this.travelInfo.startDate) {
        uni.showToast({ title: '请选择出发日期', icon: 'none' })
        return
      }

      // 构建消息
      const content = `我想去${this.travelInfo.destination}玩${this.travelInfo.days}天，出发日期是${this.travelInfo.startDate}`
      this.inputMessage = content
      this.sendMessage()
    },

    async loadSessions() {
      // TODO: 调用 /session/getUserSessions 接口
	  try{
		  const userId=this.userInfo.id
		  const data=await getUserSessions(userId,1,10)
		  console.log('获取用户的所有会话',data)
		  if(data && data.records && data.records.length>0){
		  		  this.sessions=data.records.map(item => ({
		  			  sessionId: item.sessionId,
		  			  title: item.title,
		  			  createTime: item.createTime
		  		  }))
		  }
	  }catch(error){
		  console.error('加载会话失败:', error)
	  }
    },

    async loadLatestSession() {
      try {
        const userId = this.userInfo.id
        const data = await getUserSessions(userId, 1, 10)
        console.log('获取最近会话:', data)
        if (data && data.records && data.records.length > 0) {
          const latest = data.records[0]
          this.currentSessionId = latest.sessionId
          this.loadSession(latest.sessionId)
        } else {
          this.messages = []
        }
      } catch (error) {
        console.error('加载最近会话失败:', error)
        this.messages = []
      }
    },

    async loadSession(sessionId) {
      // TODO: 调用 /session/{userId}/{sessionId}/message 接口
	  try{
		const userId=this.userInfo.id
		console.log('会话ID: ',sessionId)
		const data=await getSessionMessages(userId,sessionId,1,10)
		console.log('原始返回:', JSON.stringify(data, null, 2))
		console.log('消息: ',data.records)
		console.log('role值:', data.records.map(item => item.role))
		if(data && data.records && data.records.length>0){
			this.messages=data.records.map(item =>{
				let plan = null
				if (item.planJson) {
					try { plan = JSON.parse(item.planJson) } catch (e) { plan = null }
				}
				return {
					msgId: item.msgId,
					role: item.role,
					content: item.content,
					planJson: item.planJson,
					plan: plan,
					type: plan ? 'plan' : 'text',
					flowId: item.flowId,
					interaction: item.interaction
				}
			})
			// 历史恢复：扫描整个消息列表决定是否弹窗
			this.checkContactInteraction()
		}

	  }catch(error){
		  console.error('加载会话消息失败',error)
	  }
    },

    openSession(session) {
      uni.navigateTo({
        url: `/pages/chat/chat?sessionId=${session.sessionId}`
      })
    },

    async createNewSession() {
      // 弹窗让用户输入标题
      uni.showModal({
        title: '新建会话',
        editable: true,
        placeholderText: '请输入会话标题',
        success: async (res) => {
          if (res.confirm && res.content) {
            try {
              const userId = this.userInfo.id
              const data = await createSession({ title: res.content })
              console.log('创建会话成功:', data)
              // 清空消息，切换到 AI 对话 Tab
              this.messages = []
              this.currentTab = 'chat'
              this.currentSessionId = data.sessionId
              // 跳转到新会话
              uni.navigateTo({
                url: `/pages/chat/chat?sessionId=${data.sessionId}`
              })
            } catch (error) {
              console.error('创建会话失败:', error)
              uni.showToast({ title: '创建会话失败', icon: 'none' })
            }
          }
        }
      })
    },

    sendQuickQuestion(question) {
      this.inputMessage = question
      this.sendMessage()
    },

    sendMessage() {
      if (!this.inputMessage.trim() || this.isLoading) return
      if (!this.currentSessionId) {
        uni.showToast({ title: '请先创建会话', icon: 'none' })
        return
      }
      this.sendContent(this.inputMessage.trim())
    },

    // 实际发送消息的公共逻辑，弹层表单和输入框都走这里
    sendContent(content) {
      if (!content) return
      if (!this.currentSessionId) {
        uni.showToast({ title: '请先创建会话', icon: 'none' })
        return
      }

      this.messages.push({
        role: 'USER',
        type: 'text',
        content: content
      })

      // 输入框发的清空输入框；弹层发的不要动 inputMessage
      if (content === this.inputMessage.trim()) {
        this.inputMessage = ''
      }
      this.scrollToBottom()
      this.isLoading = true

      const params = {
        sessionId: this.currentSessionId,
        role: 'USER',
        content: content
      }
      if (this.travelInfo.startDate) {
        params.startDate = this.travelInfo.startDate
      }
      if (this.travelInfo.days) {
        params.days = this.travelInfo.days
      }

      console.log('请求参数:', params)
      sendMessage(params).then(res => {
        console.log('消息发送成功:', res)
      }).catch(err => {
        console.error('消息发送失败:', err)
        uni.showToast({ title: '发送失败', icon: 'none' })
        this.messages.pop()
      }).finally(() => {
        this.isLoading = false
      })
    },

    // ==================== 入住人信息弹层 ====================
    submitContact() {
      const name = this.contactForm.guestName.trim()
      const phone = this.contactForm.guestPhone.trim()

      if (!name) {
        this.contactError = '请输入入住人姓名'
        return
      }
      // 姓名：2-20 位中文/英文/中点，与 Python 侧正则保持一致
      if (!/^[一-龥A-Za-z·]{2,20}$/.test(name)) {
        this.contactError = '姓名格式不正确（2-20 位中文/英文）'
        return
      }
      // 手机号与 Java HotelBookingDTO 的 ^1[3-9]\d{9}$ 完全一致
      if (!/^1[3-9]\d{9}$/.test(phone)) {
        this.contactError = '请输入正确的 11 位手机号'
        return
      }

      this.contactError = ''
      this.contactModalVisible = false
      const content = `入住人姓名：${name}，手机号：${phone}`
      this.sendContent(content)
    },

    scrollToBottom() {
      this.$nextTick(() => {
        this.scrollIntoView = `msg-${this.messages.length - 1}`
      })
    },

    formatTime(time) {
      if (!time) return ''
      const date = new Date(time)
      return `${date.getMonth() + 1}月${date.getDate()}日 ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`
    },

    goBookHotel(destination) {
      uni.switchTab({ url: '/pages/hotel/list' })
    }
  }
}
</script>

<style lang="scss" scoped>
.chat-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.chat-tabs {
  display: flex;
  background: #fff;
  border-bottom: 1rpx solid #eee;

  .tab-item {
    flex: 1;
    height: 88rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 28rpx;
    color: #666;
    position: relative;

    &.active {
      color: #007AFF;
      font-weight: 600;

      &::after {
        content: '';
        position: absolute;
        bottom: 0;
        width: 60rpx;
        height: 4rpx;
        background: #007AFF;
        border-radius: 2rpx;
      }
    }
  }

  .tab-add {
    width: 88rpx;
    height: 88rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 48rpx;
    color: #007AFF;
    font-weight: 300;
  }
}

.chat-main {
  flex: 1;
  min-height: 0;  /* 解除 flex item 默认 min-height:auto，否则内部 scroll-view 拿不到限定高度，无法滚动 */
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.welcome-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 40rpx;

  .welcome-icon {
    width: 160rpx;
    height: 160rpx;
    margin-bottom: 30rpx;
  }

  .welcome-title {
    font-size: 36rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 16rpx;
  }

  .welcome-desc {
    font-size: 28rpx;
    color: #999;
    text-align: center;
    margin-bottom: 50rpx;
  }

  .start-plan-btn {
    width: 80%;
    height: 88rpx;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: #fff;
    font-size: 30rpx;
    border-radius: 44rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 40rpx;
  }

  .quick-questions {
    width: 100%;

    .quick-title {
      font-size: 26rpx;
      color: #999;
      margin-bottom: 20rpx;
      display: block;
    }

    .quick-item {
      display: block;
      padding: 24rpx 30rpx;
      background: #fff;
      border-radius: 12rpx;
      font-size: 26rpx;
      color: #007AFF;
      margin-bottom: 16rpx;
    }
  }
}

.message-list {
  flex: 1;
  min-height: 0;  /* 同上，scroll-view 在小程序里需要明确的尺寸约束才能滚动 */
  padding: 20rpx 30rpx;
}

.message-item {
  display: flex;
  margin-bottom: 30rpx;

  &.user {
    flex-direction: row-reverse;

    .msg-bubble {
      margin-right: 20rpx;
    }
  }

  &.ai {
    .msg-bubble {
      margin-left: 20rpx;
    }
  }

  .msg-avatar {
    width: 72rpx;
    height: 72rpx;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .msg-bubble {
    max-width: 520rpx;
    padding: 20rpx 28rpx;
    border-radius: 20rpx;
    font-size: 28rpx;
    line-height: 1.5;
  }

  .user-bubble {
    background: #007AFF;
    color: #fff;
    border-bottom-right-radius: 8rpx;
  }

  .ai-bubble {
    background: #fff;
    color: #333;
    border-bottom-left-radius: 8rpx;
  }
}

.loading {
  .loading-dots {
    display: flex;
    padding: 20rpx 0;

    .dot {
      font-size: 48rpx;
      color: #999;
      animation: blink 1s infinite;
    }

    .dot:nth-child(2) { animation-delay: 0.2s; }
    .dot:nth-child(3) { animation-delay: 0.4s; }
  }
}

@keyframes blink {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

.plan-card {
  margin-top: 20rpx;
  background: #f8f8f8;
  border-radius: 12rpx;
  padding: 24rpx;

  .plan-header {
    margin-bottom: 16rpx;

    .plan-title {
      font-size: 30rpx;
      font-weight: 600;
      color: #333;
      margin-right: 16rpx;
    }

    .plan-destination {
      font-size: 24rpx;
      color: #007AFF;
    }
  }

  .plan-days, .plan-budget {
    font-size: 24rpx;
    color: #666;
    margin-bottom: 8rpx;
  }

  .plan-details {
    margin-top: 16rpx;

    .day-item {
      margin-bottom: 20rpx;
      padding: 12rpx;
      background: #f8f8fa;
      border-radius: 8rpx;

      .plan-day-title {
        font-size: 26rpx;
        font-weight: 600;
        color: #333;
        margin-bottom: 12rpx;
      }

      .activity-item {
        display: flex;
        align-items: flex-start;
        padding: 8rpx 0;
        border-bottom: 1rpx solid #eee;
        font-size: 24rpx;

        &:last-child {
          border-bottom: none;
        }

        .activity-time {
          width: 180rpx;
          flex-shrink: 0;
          color: #888;
        }

        .activity-body {
          flex: 1;

          .activity-name {
            color: #333;
            font-weight: 500;
            margin-bottom: 4rpx;
          }

          .activity-desc {
            color: #888;
            font-size: 22rpx;
            line-height: 1.4;
          }

          .activity-loc {
            color: #aaa;
            font-size: 22rpx;
            margin-top: 4rpx;
          }
        }
      }

      .day-cost {
        margin-top: 8rpx;
        font-size: 22rpx;
        color: #fa8c16;
        text-align: right;
      }
    }
  }

  .plan-total {
    margin-top: 16rpx;
    padding-top: 12rpx;
    border-top: 2rpx solid #eee;
    font-size: 26rpx;
    font-weight: 600;
    color: #fa541c;
    text-align: right;
  }

  .book-hotel-btn {
    margin-top: 20rpx;
    width: 100%;
    height: 72rpx;
    background: #07c160;
    color: #fff;
    font-size: 28rpx;
    border-radius: 36rpx;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.input-area {
  display: flex;
  align-items: center;
  padding: 20rpx 30rpx;
  background: #fff;
  border-top: 1rpx solid #eee;

  .message-input {
    flex: 1;
    height: 72rpx;
    padding: 0 30rpx;
    background: #f5f5f5;
    border-radius: 36rpx;
    font-size: 28rpx;
  }

  /* ==================== 入住人信息弹层 ==================== */
  .modal-mask {
    position: fixed;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 999;
  }
  .contact-modal {
    width: 640rpx;
    background: #fff;
    border-radius: 24rpx;
    padding: 40rpx;
    box-sizing: border-box;
  }
  .modal-title {
    font-size: 36rpx;
    font-weight: bold;
    color: #333;
    text-align: center;
    margin-bottom: 16rpx;
  }
  .modal-hotel {
    font-size: 28rpx;
    color: #666;
    text-align: center;
    margin-bottom: 32rpx;
  }
  .form-item {
    margin-bottom: 24rpx;
  }
  .form-label {
    display: block;
    font-size: 28rpx;
    color: #333;
    margin-bottom: 12rpx;
  }
  .form-input {
    width: 100%;
    height: 80rpx;
    padding: 0 20rpx;
    background: #f5f5f5;
    border-radius: 12rpx;
    font-size: 28rpx;
    box-sizing: border-box;
  }
  .contact-error {
    display: block;
    color: #ff4d4f;
    font-size: 26rpx;
    margin-bottom: 16rpx;
  }
  .modal-actions {
    display: flex;
    margin-top: 16rpx;
  }
  .modal-confirm {
    flex: 1;
    height: 88rpx;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: #fff;
    font-size: 30rpx;
    border-radius: 44rpx;
    line-height: 88rpx;
  }

  .send-btn {
    width: 120rpx;
    height: 72rpx;
    margin-left: 20rpx;
    background: #007AFF;
    color: #fff;
    font-size: 28rpx;
    border-radius: 36rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0;

    &:disabled {
      background: #ccc;
    }
  }
}

.history-main {
  flex: 1;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 200rpx;

  .empty-icon {
    width: 200rpx;
    height: 200rpx;
    opacity: 0.5;
  }

  .empty-text {
    margin-top: 30rpx;
    font-size: 28rpx;
    color: #999;
  }
}

.session-list {
  .session-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 30rpx;
    background: #fff;
    border-bottom: 1rpx solid #f0f0f0;

    .session-content {
      flex: 1;

      .session-title {
        font-size: 28rpx;
        color: #333;
        display: block;
        margin-bottom: 8rpx;
      }

      .session-time {
        font-size: 24rpx;
        color: #999;
      }
    }

    .session-arrow {
      font-size: 32rpx;
      color: #ccc;
    }
  }
}
</style>
