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
            <view class="msg-bubble ai-bubble" :class="{ 'msg-bubble-wide': msg.type === 'plan','plan-bubble': msg.type === 'plan' }">
              <text v-if="msg.type === 'text'">{{ msg.content }}</text>
              <!-- 行程规划卡片（按天翻页，可翻转） -->
              <view v-else-if="msg.type === 'plan'" class="plan-card-deck" @click="openChatPlanModal(msg)">
                <!-- 卡片头 -->
                <view class="plan-card-header">
                  <view class="plan-card-header-left">
                    <text class="plan-title">{{ msg.plan.title }}</text>
                    <text class="plan-meta-line">{{ msg.plan.destination }} · {{ msg.plan.days }}天 · 预算{{ msg.plan.budget }}</text>
                  </view>
                  <view class="plan-card-header-right" @click.stop="toggleChatCardAllFlip(msg)">
                    <text class="flip-toggle-btn">{{ msg.chatCardAllFlipped ? '📖 合上' : '📖 展开' }}</text>
                  </view>
                </view>

                <!-- swiper 翻页：每天一张可翻转卡片 -->
                <swiper
                  class="chat-swiper"
                  :current="msg.chatCardCurrentIndex || 0"
                  @change="onChatSwiperChange(msg, $event)"
                >
                  <swiper-item
                    v-for="(day, idx) in getMsgDailyPlans(msg)"
                    :key="idx"
                    class="chat-card-item"
                  >
                    <view
                      class="chat-day-card"
                      :class="{ 'is-flipped': isMsgCardFlipped(msg, idx) }"
                      @click.stop="toggleMsgCardFlip(msg, idx)"
                    >
                      <!-- 正面 -->
                      <view class="card-face card-front">
                        <view class="day-badge-row">
                          <view class="day-badge">Day {{ day.day }}</view>
                        </view>
                        <text class="day-theme-text">{{ day.theme || '行程主题' }}</text>
                        <view class="day-stats">
                          <view class="stat-block">
                            <text class="stat-num">{{ (day.activities || []).length }}</text>
                            <text class="stat-label">活动</text>
                          </view>
                          <view class="stat-divider"></view>
                          <view class="stat-block">
                            <text class="stat-num">¥{{ calcDayCost(day.activities) }}</text>
                            <text class="stat-label">当日费用</text>
                          </view>
                        </view>
                        <view class="day-preview">
                          <view
                            v-for="(act, i) in (day.activities || []).slice(0, 3)"
                            :key="i"
                            class="preview-row"
                          >
                            <text class="preview-time-text">{{ act.time }}</text>
                            <text class="preview-name-text">{{ act.name }}</text>
                          </view>
                          <text v-if="(day.activities || []).length > 3" class="preview-more-text">
                            还有 {{ (day.activities || []).length - 3 }} 个活动...
                          </text>
                        </view>
                        <view class="card-footer-tip">
                          <text class="flip-hint">点击卡片查看详细行程 ↻</text>
                        </view>
                      </view>
                      <!-- 背面 -->
                      <view class="card-face card-back">
                        <view class="back-header-mini">
                          <view class="back-badge-mini">Day {{ day.day }} · 详情</view>
                          <text class="back-theme-mini">{{ day.theme }}</text>
                        </view>
                        <scroll-view class="activity-scroll" scroll-y>
                          <view
                            v-for="(act, aIdx) in (day.activities || [])"
                            :key="aIdx"
                            class="activity-row-card"
                          >
                            <view class="activity-time-row">
                              <text class="act-time-text">{{ act.time }}</text>
                              <text v-if="act.cost" class="act-cost-text">¥{{ act.cost }}</text>
                            </view>
                            <text class="act-name-text">{{ act.name }}</text>
                            <text v-if="act.location" class="act-loc-text">📍 {{ act.location }}</text>
                            <text v-if="act.transportation" class="act-transport-text">🚇 {{ act.transportation }}</text>
                            <text v-if="act.description" class="act-desc-text">{{ act.description }}</text>
                          </view>
                        </scroll-view>
                        <view class="card-footer-tip">
                          <text class="flip-hint">点击卡片返回 ↻</text>
                        </view>
                      </view>
                    </view>
                  </swiper-item>
                </swiper>

                <!-- 左右切换按钮 -->
                <view class="nav-buttons-mini">
                  <view
                    class="nav-btn-mini"
                    :class="{ disabled: !msg.chatCardCurrentIndex }"
                    @click.stop="prevChatCard(msg)"
                  >‹ 昨天</view>
                  <view
                    class="nav-btn-mini"
                    :class="{ disabled: msg.chatCardCurrentIndex >= getMsgDailyPlans(msg).length - 1 }"
                    @click.stop="nextChatCard(msg)"
                  >明天 ›</view>
                </view>

                <view class="plan-card-footer" v-if="msg.plan.total_estimated_cost">
                  <text class="plan-total-line">总预算约 ¥{{ msg.plan.total_estimated_cost }}</text>
                </view>
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

    <!-- ============== AI 行程详情弹窗（与"我的行程"一致） ============== -->
    <view v-if="showChatPlanModal" class="modal-mask" @click="closeChatPlanModal">
      <view class="modal-container" @click.stop>
        <!-- 弹窗头部 -->
        <view class="modal-header">
          <view class="modal-close" @click="closeChatPlanModal">×</view>
          <view class="modal-title-wrap">
            <text class="modal-title">{{ currentModalMsg.plan.title }}</text>
            <text class="modal-sub">{{ currentModalMsg.plan.destination }} · 第 {{ modalCurrentIndex + 1 }} / {{ getModalDailyPlans().length }} 天</text>
          </view>
          <view class="modal-flip-btn" catchtap="toggleModalAllFlip">
            <text>{{ modalAllFlipped ? '合上' : '展开' }}</text>
          </view>
        </view>

        <!-- 卡片堆（去 swiper 改用单卡 + 左右按钮，避免 swiper 拦截） -->
        <view v-if="getModalDailyPlans().length === 0" class="plan-empty">
          <text>暂无详细行程数据</text>
        </view>
        <view v-else class="card-deck-container">
          <!-- 单张卡片（每天一张） -->
          <view class="modal-card-wrapper">
            <view
              v-if="getModalDailyPlans()[modalCurrentIndex]"
              class="modal-day-card"
              :class="{ 'is-flipped': isModalCardFlipped(modalCurrentIndex) }"
              @click="toggleModalCardFlip(modalCurrentIndex)">
              <!-- 正面 -->
              <view class="modal-card-face modal-card-front">
                <view class="modal-day-badge-row">
                  <view class="modal-day-badge">Day {{ getModalDailyPlans()[modalCurrentIndex].day }}</view>
                </view>
                <text class="modal-day-theme">{{ getModalDailyPlans()[modalCurrentIndex].theme || '行程主题' }}</text>
                <view class="modal-day-stats">
                  <view class="modal-stat-block">
                    <text class="modal-stat-num">{{ (getModalDailyPlans()[modalCurrentIndex].activities || []).length }}</text>
                    <text class="modal-stat-label">活动</text>
                  </view>
                  <view class="modal-stat-divider"></view>
                  <view class="modal-stat-block">
                    <text class="modal-stat-num">¥{{ calcDayCost(getModalDailyPlans()[modalCurrentIndex].activities) }}</text>
                    <text class="modal-stat-label">当日费用</text>
                  </view>
                </view>
                <view class="modal-day-preview">
                  <view
                    v-for="(act, i) in (getModalDailyPlans()[modalCurrentIndex].activities || []).slice(0, 3)"
                    :key="i"
                    class="modal-preview-row">
                    <text class="modal-preview-time">{{ act.time }}</text>
                    <text class="modal-preview-name">{{ act.name }}</text>
                  </view>
                  <text v-if="(getModalDailyPlans()[modalCurrentIndex].activities || []).length > 3" class="modal-preview-more">
                    还有 {{ (getModalDailyPlans()[modalCurrentIndex].activities || []).length - 3 }} 个活动...
                  </text>
                </view>
                <view class="modal-card-footer">
                  <text class="modal-flip-hint">点击卡片查看详细行程 ↻</text>
                </view>
              </view>
              <!-- 背面 -->
              <view class="modal-card-face modal-card-back">
                <view class="modal-back-header">
                  <view class="modal-back-badge">Day {{ getModalDailyPlans()[modalCurrentIndex].day }} · 详情</view>
                  <text class="modal-back-theme">{{ getModalDailyPlans()[modalCurrentIndex].theme }}</text>
                </view>
                <scroll-view class="modal-activity-list" scroll-y>
                  <view
                    v-for="(act, actIdx) in (getModalDailyPlans()[modalCurrentIndex].activities || [])"
                    :key="actIdx"
                    class="modal-activity-card">
                    <view class="modal-activity-time-row">
                      <text class="modal-act-time">{{ act.time }}</text>
                      <text v-if="act.cost" class="modal-act-cost">¥{{ act.cost }}</text>
                    </view>
                    <text class="modal-act-name">{{ act.name }}</text>
                    <text v-if="act.location" class="modal-act-loc">📍 {{ act.location }}</text>
                    <text v-if="act.transportation" class="modal-act-transport">🚇 {{ act.transportation }}</text>
                    <text v-if="act.description" class="modal-act-desc">{{ act.description }}</text>
                  </view>
                </scroll-view>
                <view class="modal-card-footer">
                  <text class="modal-flip-hint">点击卡片返回 ↻</text>
                </view>
              </view>
            </view>
          </view>

          <!-- 左右切换按钮 + 指示点 -->
          <view class="modal-nav-row">
            <view
              class="modal-nav-btn modal-nav-prev"
              :class="{ disabled: modalCurrentIndex === 0 }"
              @click="prevModalCard">‹ 昨天</view>
            <!-- 指示点 -->
            <view class="modal-dots">
              <view
                v-for="(d, i) in getModalDailyPlans()"
                :key="i"
                class="modal-dot"
                :class="{ active: modalCurrentIndex === i }"
                @click="modalCurrentIndex = i"></view>
            </view>
            <view
              class="modal-nav-btn modal-nav-next"
              :class="{ disabled: modalCurrentIndex >= getModalDailyPlans().length - 1 }"
              @click="nextModalCard">明天 ›</view>
          </view>
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
      },
      // 行程卡片翻页相关
      chatCardCurrentIndex: 0,
      chatCardFlipped: {},
      chatCardAllFlipped: false,
      // 行程详情弹窗
      showChatPlanModal: false,
      currentModalMsg: null,
      modalCurrentIndex: 0,
      modalCardFlipped: {},
      modalAllFlipped: false
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

  // 拦截物理返回键/导航栏返回按钮，直接跳到首页（不层层返回）
  onBackPress(options) {
    if (options.from === 'backbutton') {
      uni.reLaunch({ url: '/pages/index/index' })
      return true
    }
  },

  // 自定义返回按钮：清栈回首页
  goHome() {
    uni.reLaunch({ url: '/pages/index/index' })
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
        interaction: data.interaction,
		chatCardCurrentIndex: 0,          // 初始化
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
					interaction: item.interaction,
					chatCardCurrentIndex: 0,          // 初始化
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
      // 用 redirectTo 替换当前页（避免栈深度增加），这样点返回直接到首页
      uni.redirectTo({
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
    },

    // ============== 行程卡片翻页（每条 AI 消息独立状态） ==============

    // 解析消息里的 dailyPlans（兼容字符串/数组/驼峰/蛇形）
    getMsgDailyPlans(msg) {
      if (!msg || !msg.plan) return []
      let plans = msg.plan.dailyPlans || msg.plan.daily_plans || msg.plan.dayPlans || []
      if (typeof plans === 'string') {
        try { plans = JSON.parse(plans) } catch (e) { plans = [] }
      }
      return Array.isArray(plans) ? plans : []
    },

    isMsgCardFlipped(msg, idx) {
      const key = this.getMsgFlipKey(msg)
      return (msg[key] && msg[key][idx]) || false
    },

    toggleMsgCardFlip(msg, idx) {
      const key = this.getMsgFlipKey(msg)
      if (!msg[key]) msg[key] = {}
      msg[key][idx] = !msg[key][idx]
      // 触发响应式更新（Vue 2）
      this.$set(msg, key, { ...msg[key] })
      this.updateMsgAllFlipped(msg)
    },

    toggleChatCardAllFlip(msg) {
      const key = this.getMsgFlipKey(msg)
      const newFlipped = !msg.chatCardAllFlipped
      const plans = this.getMsgDailyPlans(msg)
      const f = {}
      plans.forEach((_, idx) => { f[idx] = newFlipped })
      this.$set(msg, key, f)
      msg.chatCardAllFlipped = newFlipped
    },

    updateMsgAllFlipped(msg) {
      const key = this.getMsgFlipKey(msg)
      const plans = this.getMsgDailyPlans(msg)
      const flippedCount = Object.values(msg[key] || {}).filter(v => v).length
      msg.chatCardAllFlipped = plans.length > 0 && flippedCount === plans.length
    },

    getMsgFlipKey(msg) {
      // 用消息的唯一标识（planId + 时间戳）作为 key，让每条消息独立
      return `chatCardFlipped_${msg.plan && (msg.plan.planId || msg.plan.plan_id || msg._id || Math.random())}`
    },

    onChatSwiperChange(msg, e) {
      const idx = e.detail.current
      // 直接修改 msg 上的字段（Vue 2 响应式）
      msg.chatCardCurrentIndex = idx
    },

    prevChatCard(msg) {
      if (msg.chatCardCurrentIndex > 0) {
        msg.chatCardCurrentIndex--
      }
    },

    nextChatCard(msg) {
      const plans = this.getMsgDailyPlans(msg)
      if (msg.chatCardCurrentIndex < plans.length - 1) {
        msg.chatCardCurrentIndex++
      }
    },

    // 计算当日总费用
    calcDayCost(activities) {
      if (!activities || activities.length === 0) return 0
      return activities.reduce((total, a) => total + (Number(a.cost) || 0), 0)
    },

    // 打开聊天行程详情弹窗
    openChatPlanModal(msg) {
      console.log('openChatPlanModal 被调用, msg=', msg)
      this.currentModalMsg = msg
      this.modalCurrentIndex = 0
      this.modalCardFlipped = {}
      this.modalAllFlipped = false
      this.showChatPlanModal = true
      console.log('showChatPlanModal=', this.showChatPlanModal, 'currentModalMsg=', this.currentModalMsg)
    },

    // 关闭弹窗
    closeChatPlanModal() {
      console.log('closeChatPlanModal 被调用')
      this.showChatPlanModal = false
      this.currentModalMsg = null
    },

    // 获取当前 plan 的稳定 key（用 planId 区分不同 plan）
    getCurrentPlanKey() {
      const p = this.currentModalMsg && this.currentModalMsg.plan
      if (!p) return null
      return p.planId || p.plan_id || p.title || null
    },

    // 弹窗内：单卡翻转（按 plan 独立存储）
    toggleModalCardFlip(idx) {
      const planKey = this.getCurrentPlanKey()
      if (!planKey) return
      console.log('toggleModalCardFlip 被调用, planKey=', planKey, 'idx=', idx)

      const planFlipped = this.modalCardFlipped[planKey] || {}
      this.modalCardFlipped = {
        ...this.modalCardFlipped,
        [planKey]: {
          ...planFlipped,
          [idx]: !planFlipped[idx]
        }
      }
      this.updateModalAllFlipped()
    },

    isModalCardFlipped(idx) {
      const planKey = this.getCurrentPlanKey()
      if (!planKey) return false
      const planFlipped = this.modalCardFlipped[planKey]
      return (planFlipped && planFlipped[idx]) || false
    },

    updateModalAllFlipped() {
      const planKey = this.getCurrentPlanKey()
      if (!planKey) return
      const plans = this.getModalDailyPlans()
      const planFlipped = this.modalCardFlipped[planKey] || {}
      const flippedCount = Object.values(planFlipped).filter(v => v).length
      this.modalAllFlipped = plans.length > 0 && flippedCount === plans.length
    },

    toggleModalAllFlip() {
      console.log('toggleModalAllFlip 被调用')
      const planKey = this.getCurrentPlanKey()
      if (!planKey) return
      const plans = this.getModalDailyPlans()
      this.modalAllFlipped = !this.modalAllFlipped
      const f = {}
      plans.forEach((_, idx) => { f[idx] = this.modalAllFlipped })
      this.modalCardFlipped = {
        ...this.modalCardFlipped,
        [planKey]: f
      }
    },

    // 弹窗内的 swiper 切换
    onModalSwiperChange(e) {
      this.modalCurrentIndex = e.detail.current
    },
    prevModalCard() {
      if (this.modalCurrentIndex > 0) {
        this.modalCurrentIndex--
      }
    },
    nextModalCard() {
      const plans = this.getModalDailyPlans()
      if (this.modalCurrentIndex < plans.length - 1) {
        this.modalCurrentIndex++
      }
    },

    // 弹窗内的 dailyPlans 解析
    getModalDailyPlans() {
      if (!this.currentModalMsg || !this.currentModalMsg.plan) return []
      let plans = this.currentModalMsg.plan.dailyPlans || this.currentModalMsg.plan.daily_plans || []
      if (typeof plans === 'string') {
        try { plans = JSON.parse(plans) } catch (e) { plans = [] }
      }
      return Array.isArray(plans) ? plans : []
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

  // 包含 plan 卡片的气泡放宽
  .msg-bubble-wide {
    width: 70% !important;               /* 取消固定宽度，由内容撑开 */
    max-width: calc(100vw - 80rpx) !important; /* 左右各留 40rpx 边距，不贴边 */
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

/* ============== 行程卡片翻页（嵌入式） ============== */
.plan-card-deck {
  margin-top: 16rpx;
  width: 100%;
  background: #fff;
  border-radius: 16rpx;
  padding: 20rpx 16rpx 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
}

.plan-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 0 8rpx;
  margin-bottom: 16rpx;

  .plan-card-header-left {
    flex: 1;
    min-width: 0;

    .plan-title {
      display: block;
      font-size: 30rpx;
      font-weight: 600;
      color: #333;
      margin-bottom: 6rpx;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .plan-meta-line {
      display: block;
      font-size: 22rpx;
      color: #666;
    }
  }

  .plan-card-header-right {
    flex-shrink: 0;

    .flip-toggle-btn {
      display: inline-block;
      font-size: 20rpx;
      color: #667eea;
      background: rgba(102, 126, 234, 0.1);
      padding: 6rpx 14rpx;
      border-radius: 18rpx;
    }
  }
}

/* 简短预览（前 3 天） */
.plan-preview-list {
  margin-top: 8rpx;

  .preview-day-row {
    display: flex;
    align-items: center;
    padding: 14rpx 8rpx;
    border-bottom: 1rpx solid #f0f0f0;

    &:last-of-type {
      border-bottom: none;
    }

    .preview-day-tag {
      flex-shrink: 0;
      display: inline-block;
      background: linear-gradient(135deg, #667eea, #764ba2);
      color: #fff;
      font-size: 22rpx;
      font-weight: 600;
      padding: 4rpx 14rpx;
      border-radius: 16rpx;
      margin-right: 16rpx;
    }

    .preview-day-name {
      flex: 1;
      font-size: 26rpx;
      color: #333;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .preview-day-activities {
      flex-shrink: 0;
      font-size: 22rpx;
      color: #999;
      margin-left: 12rpx;
    }
  }

  .preview-more-link {
    display: block;
    text-align: center;
    font-size: 22rpx;
    color: #667eea;
    padding: 14rpx 0 4rpx;
  }
}

.plan-card-footer {
  margin-top: 12rpx;
  padding-top: 12rpx;
  border-top: 1rpx solid #f0f0f0;
  text-align: right;

  .plan-total-line {
    font-size: 24rpx;
    font-weight: 600;
    color: #fa541c;
  }
}

.plan-empty {
  text-align: center;
  padding: 60rpx 0;
  color: #999;
  font-size: 26rpx;
}

.chat-card-deck {
  width: 100%;
}

.chat-swiper {
  height: 720rpx;
}

.chat-card-item {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 6rpx;
}

.chat-day-card {
  width: 100%;
  height: 700rpx;
  position: relative;
  border-radius: 16rpx;
  transform-style: preserve-3d;
  transition: transform 0.7s cubic-bezier(0.4, 0, 0.2, 1);

  &.is-flipped {
    transform: rotateY(180deg);
  }
}

.card-face {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border-radius: 16rpx;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  overflow: hidden;
  background: linear-gradient(180deg, #f8f9ff 0%, #fff 100%);
  box-shadow: 0 6rpx 16rpx rgba(0, 0, 0, 0.08);
}

.card-front {
  display: flex;
  flex-direction: column;
  padding: 24rpx 24rpx 16rpx;
}

.day-badge-row {
  margin-bottom: 12rpx;

  .day-badge {
    display: inline-block;
    background: linear-gradient(135deg, #667eea, #764ba2);
    color: #fff;
    font-size: 24rpx;
    font-weight: 600;
    padding: 4rpx 18rpx;
    border-radius: 18rpx;
  }
}

.day-theme-text {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
  text-align: center;
  margin-bottom: 16rpx;
}

.day-stats {
  display: flex;
  background: rgba(102, 126, 234, 0.06);
  border-radius: 12rpx;
  padding: 16rpx 0;
  margin-bottom: 16rpx;

  .stat-block {
    flex: 1;
    text-align: center;
  }

  .stat-divider {
    width: 1rpx;
    background: rgba(0, 0, 0, 0.08);
  }

  .stat-num {
    display: block;
    font-size: 28rpx;
    font-weight: 700;
    color: #667eea;
  }

  .stat-label {
    display: block;
    font-size: 20rpx;
    color: #999;
    margin-top: 2rpx;
  }
}

.day-preview {
  flex: 1;
  overflow: hidden;

  .preview-row {
    display: flex;
    align-items: center;
    gap: 12rpx;
    padding: 6rpx 0;
    border-bottom: 1rpx dashed #eee;

    &:last-child {
      border-bottom: none;
    }

    .preview-time-text {
      font-size: 20rpx;
      color: #667eea;
      font-weight: 600;
      width: 110rpx;
      flex-shrink: 0;
    }

    .preview-name-text {
      font-size: 22rpx;
      color: #333;
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .preview-more-text {
    display: block;
    font-size: 20rpx;
    color: #999;
    text-align: center;
    padding-top: 8rpx;
  }
}

.card-footer-tip {
  text-align: center;
  padding: 8rpx 0 0;

  .flip-hint {
    font-size: 20rpx;
    color: #999;
    animation: pulse 2s infinite;
  }
}

.card-back {
  transform: rotateY(180deg);
  display: flex;
  flex-direction: column;
}

.back-header-mini {
  padding: 14rpx 20rpx;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  flex-shrink: 0;

  .back-badge-mini {
    display: inline-block;
    font-size: 20rpx;
    background: rgba(255, 255, 255, 0.25);
    padding: 2rpx 12rpx;
    border-radius: 12rpx;
    margin-bottom: 4rpx;
  }

  .back-theme-mini {
    display: block;
    font-size: 26rpx;
    font-weight: 600;
  }
}

.activity-scroll {
  flex: 1;
  padding: 10rpx 16rpx;
}

.activity-row-card {
  background: #fafafa;
  border-radius: 10rpx;
  padding: 10rpx 12rpx;
  margin-bottom: 8rpx;

  .activity-time-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4rpx;

    .act-time-text {
      font-size: 20rpx;
      color: #667eea;
      font-weight: 600;
    }

    .act-cost-text {
      font-size: 18rpx;
      color: #d4880c;
      background: #fff3cd;
      padding: 1rpx 10rpx;
      border-radius: 10rpx;
    }
  }

  .act-name-text {
    display: block;
    font-size: 24rpx;
    font-weight: 500;
    color: #333;
    margin-bottom: 4rpx;
  }

  .act-loc-text,
  .act-transport-text {
    display: block;
    font-size: 20rpx;
    color: #999;
    line-height: 1.5;
    margin-bottom: 2rpx;
  }

  .act-desc-text {
    display: block;
    font-size: 22rpx;
    color: #666;
    line-height: 1.5;
    margin-top: 4rpx;
  }
}

.nav-buttons-mini {
  display: flex;
  justify-content: space-around;
  padding: 16rpx 40rpx 0;

  .nav-btn-mini {
    background: #f5f5f5;
    color: #667eea;
    font-size: 24rpx;
    padding: 12rpx 36rpx;
    border-radius: 30rpx;

    &.disabled {
      opacity: 0.4;
      pointer-events: none;
    }
  }
}

.plan-total-line {
  margin-top: 12rpx;
  padding: 8rpx 16rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: #fa541c;
  text-align: right;
  border-top: 1rpx solid #f0f0f0;
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


.plan-bubble {
  display: flex;
  flex-direction: column;
  align-items: center;   /* 水平居中子元素 */
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

/* ============== AI 行程详情弹窗 ============== */
.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.75);
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-container {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 30rpx 0;
}

.modal-header {
  display: flex;
  align-items: center;
  padding: 0 30rpx 20rpx;
  gap: 16rpx;

  .modal-close {
    width: 60rpx;
    height: 60rpx;
    line-height: 56rpx;
    text-align: center;
    font-size: 44rpx;
    color: #fff;
    background: rgba(255, 255, 255, 0.2);
    border-radius: 50%;
    flex-shrink: 0;
  }

  .modal-title-wrap {
    flex: 1;
    min-width: 0;

    .modal-title {
      display: block;
      font-size: 32rpx;
      font-weight: 600;
      color: #fff;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .modal-sub {
      display: block;
      font-size: 22rpx;
      color: rgba(255, 255, 255, 0.85);
      margin-top: 4rpx;
    }
  }

  .modal-flip-btn {
    background: rgba(255, 255, 255, 0.25);
    color: #fff;
    font-size: 24rpx;
    padding: 14rpx 24rpx;
    border-radius: 30rpx;
    flex-shrink: 0;
  }
}

.plan-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 28rpx;
}

.card-deck-container {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.modal-card-deck {
  flex: 1;
}

.modal-card-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 40rpx;
  perspective: 2000rpx;
}

.modal-nav-row {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 30rpx 60rpx 0;
  gap: 30rpx;

  .modal-dots {
    display: flex;
    gap: 12rpx;

    .modal-dot {
      width: 14rpx;
      height: 14rpx;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.3);
      transition: all 0.2s;

      &.active {
        background: #667eea;
        width: 28rpx;
        border-radius: 7rpx;
      }
    }
  }
}

.modal-day-card {
  width: 100%;
  height: 850rpx;
  position: relative;
  border-radius: 24rpx;
  transform-style: preserve-3d;
  transition: transform 0.7s cubic-bezier(0.4, 0, 0.2, 1);

  &.is-flipped {
    transform: rotateY(180deg);
  }
}

.modal-card-face {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border-radius: 24rpx;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 10rpx 30rpx rgba(0, 0, 0, 0.3);
}

.modal-card-front {
  display: flex;
  flex-direction: column;
  padding: 24rpx 24rpx 16rpx;
}

.modal-day-badge-row {
  margin-bottom: 12rpx;

  .modal-day-badge {
    display: inline-block;
    background: linear-gradient(135deg, #667eea, #764ba2);
    color: #fff;
    font-size: 26rpx;
    font-weight: 600;
    padding: 6rpx 20rpx;
    border-radius: 20rpx;
  }
}

.modal-day-theme {
  display: block;
  font-size: 38rpx;
  font-weight: 700;
  color: #333;
  text-align: center;
  margin-bottom: 10rpx;
}

.modal-day-stats {
  display: flex;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.08), rgba(118, 75, 162, 0.08));
  border-radius: 16rpx;
  padding: 22rpx 0;
  margin-bottom: 24rpx;

  .modal-stat-block {
    flex: 1;
    text-align: center;
  }

  .modal-stat-divider {
    width: 1rpx;
    background: rgba(0, 0, 0, 0.1);
  }

  .modal-stat-num {
    display: block;
    font-size: 32rpx;
    font-weight: 700;
    color: #667eea;
  }

  .modal-stat-label {
    display: block;
    font-size: 20rpx;
    color: #999;
    margin-top: 4rpx;
  }
}

.modal-day-preview {
  flex: 1;
  overflow: hidden;

  .modal-preview-row {
    display: flex;
    align-items: center;
    gap: 12rpx;
    padding: 8rpx 0;
    border-bottom: 1rpx dashed #eee;

    &:last-child { border-bottom: none; }

    .modal-preview-time {
      font-size: 20rpx;
      color: #667eea;
      font-weight: 600;
      width: 120rpx;
      flex-shrink: 0;
    }

    .modal-preview-name {
      font-size: 24rpx;
      color: #333;
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .modal-preview-more {
    display: block;
    font-size: 20rpx;
    color: #999;
    text-align: center;
    padding-top: 10rpx;
  }
}

.modal-card-footer {
  text-align: center;
  padding: 8rpx 0 0;

  .modal-flip-hint {
    font-size: 20rpx;
    color: #999;
    animation: pulse 2s infinite;
  }
}

.modal-card-back {
  transform: rotateY(180deg);
  display: flex;
  flex-direction: column;
}

.modal-back-header {
  padding: 18rpx 24rpx;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  flex-shrink: 0;

  .modal-back-badge {
    display: inline-block;
    font-size: 20rpx;
    background: rgba(255, 255, 255, 0.25);
    padding: 3rpx 14rpx;
    border-radius: 14rpx;
    margin-bottom: 4rpx;
  }

  .modal-back-theme {
    display: block;
    font-size: 28rpx;
    font-weight: 600;
  }
}

.modal-activity-list {
  flex: 1;
  padding: 12rpx 16rpx;
}

.modal-activity-card {
  background: #fafafa;
  border-radius: 10rpx;
  padding: 12rpx;
  margin-bottom: 10rpx;

  .modal-activity-time-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4rpx;

    .modal-act-time {
      font-size: 20rpx;
      color: #667eea;
      font-weight: 600;
    }

    .modal-act-cost {
      font-size: 18rpx;
      color: #d4880c;
      background: #fff3cd;
      padding: 1rpx 10rpx;
      border-radius: 10rpx;
    }
  }

  .modal-act-name {
    display: block;
    font-size: 24rpx;
    font-weight: 500;
    color: #333;
    margin-bottom: 4rpx;
  }

  .modal-act-loc,
  .modal-act-transport {
    display: block;
    font-size: 20rpx;
    color: #999;
    line-height: 1.5;
    margin-bottom: 2rpx;
  }

  .modal-act-desc {
    display: block;
    font-size: 22rpx;
    color: #666;
    line-height: 1.5;
    margin-top: 4rpx;
  }
}

.modal-nav-buttons {
  display: flex;
  justify-content: space-around;
  padding: 20rpx 60rpx 0;

  .modal-nav-btn {
    background: rgba(255, 255, 255, 0.95);
    color: #667eea;
    font-size: 26rpx;
    padding: 16rpx 50rpx;
    border-radius: 50rpx;
    box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.2);

    &:active { transform: scale(0.95); }

    &.disabled {
      opacity: 0.4;
      pointer-events: none;
    }
  }
}

@keyframes pulse {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}
</style>
