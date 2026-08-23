<template>
  <view class="plan-list-container">
    <!-- 顶部标题 -->
    <view class="plan-header">
      <text class="plan-title">我的行程规划</text>
      <text class="plan-subtitle">共 {{ plans.length }} 个行程</text>
    </view>

    <!-- 加载/空状态 -->
    <view v-if="loading" class="loading-state">
      <text>加载中...</text>
    </view>
    <view v-else-if="plans.length === 0" class="empty-state">
      <image class="empty-icon" src="/static/images/empty-plan.png" mode="aspectFit"></image>
      <text class="empty-text">还没有规划过行程</text>
      <button class="go-plan-btn" @click="goPlan">去规划行程</button>
    </view>

    <!-- 行程列表 -->
    <view v-else class="plan-list">
      <view
        v-for="plan in plans"
        :key="plan.planId"
        class="plan-item"
        @click="openCardModal(plan)">
        <view class="plan-item-main">
          <view class="plan-destination">
            <text class="destination-icon">📍</text>
            <text class="destination-name">{{ plan.destination }}</text>
          </view>
          <text class="plan-title-text">{{ plan.title }}</text>
          <view class="plan-meta">
            <text class="meta-item">{{ plan.days }}天</text>
            <text class="meta-divider">·</text>
            <text class="meta-item">{{ formatTime(plan.createTime) }}</text>
          </view>
        </view>
        <view class="plan-item-arrow">
          <text>查看 {{ plan.days }} 天 ›</text>
        </view>
      </view>
    </view>

    <!-- ============== 弹窗：每天一张可翻转的卡片 ============== -->
    <view v-if="showModal" class="modal-mask" @click="closeModal">
      <view class="modal-container" @click.stop>
        <!-- 弹窗顶部 -->
        <view class="modal-header">
          <view class="modal-close" @click="closeModal">×</view>
          <view class="modal-title-wrap">
            <text class="modal-title">{{ currentPlan.title }}</text>
            <text class="modal-sub">{{ currentPlan.destination }} · 第 {{ currentIndex + 1 }} / {{ dailyPlans.length }} 天</text>
          </view>
          <view class="modal-flip-btn" @click="toggleAllFlip">
            <text>{{ allFlipped ? '合上' : '展开' }}</text>
          </view>
        </view>

        <!-- 卡片堆（每天一张，可翻转） -->
        <view v-if="dailyPlans.length === 0" class="modal-empty">
          <text>暂无详细行程数据</text>
        </view>
        <view v-else class="card-deck-container">
          <swiper
            class="card-deck"
            :current="currentIndex"
            @change="onSwiperChange"
            :indicator-dots="true"
            indicator-color="rgba(255,255,255,0.4)"
            indicator-active-color="#fff"
            previous-margin="60rpx"
            next-margin="60rpx"
            :circular="false">
            <swiper-item
              v-for="(day, idx) in dailyPlans"
              :key="day.day || idx"
              class="card-item">
              <view
                class="day-card"
                :class="{ 'is-flipped': flipped[idx] }"
                @click="toggleFlip(idx)">

                <!-- 正面：当日概览 -->
                <view class="card-face card-front">
                  <view class="card-front-bg">
                    <text class="bg-icon">📅</text>
                    <text class="bg-icon bg-icon-2">🗺️</text>
                    <text class="bg-icon bg-icon-3">✨</text>
                  </view>

                  <view class="day-header">
                    <view class="day-badge">Day {{ day.day }}</view>
                  </view>

                  <view class="day-main">
                    <text class="day-theme">{{ day.theme || '行程主题' }}</text>
                    <text class="day-day-label">第 {{ day.day }} 天</text>

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
                        class="preview-item">
                        <text class="preview-time">{{ act.time }}</text>
                        <text class="preview-name">{{ act.name }}</text>
                      </view>
                      <text v-if="(day.activities || []).length > 3" class="preview-more">
                        还有 {{ (day.activities || []).length - 3 }} 个活动...
                      </text>
                    </view>
                  </view>

                  <view class="card-footer">
                    <text class="flip-hint">点击卡片查看详细行程 ↻</text>
                  </view>
                </view>

                <!-- 背面：详细行程 -->
                <view class="card-face card-back">
                  <view class="back-header">
                    <view class="back-badge">Day {{ day.day }} · 详情</view>
                    <text class="back-theme">{{ day.theme }}</text>
                  </view>

                  <scroll-view class="activity-list" scroll-y>
                    <view
                      v-for="(act, actIdx) in (day.activities || [])"
                      :key="actIdx"
                      class="activity-card">
                      <view class="activity-time-row">
                        <text class="activity-time">{{ act.time }}</text>
                        <text v-if="act.cost" class="activity-cost">¥{{ act.cost }}</text>
                      </view>
                      <text class="activity-name">{{ act.name }}</text>
                      <text v-if="act.location" class="activity-loc">📍 {{ act.location }}</text>
                      <text v-if="act.transportation" class="activity-transport">🚇 {{ act.transportation }}</text>
                      <text v-if="act.description" class="activity-desc">{{ act.description }}</text>
                    </view>
                  </scroll-view>

                  <view class="card-footer">
                    <text class="flip-hint">点击卡片返回 ↻</text>
                  </view>
                </view>
              </view>
            </swiper-item>
          </swiper>

          <!-- 左右切换按钮 -->
          <view class="nav-buttons">
            <view
              class="nav-btn nav-prev"
              :class="{ disabled: currentIndex === 0 }"
              @click="prevCard">‹ 昨天</view>
            <view
              class="nav-btn nav-next"
              :class="{ disabled: currentIndex === dailyPlans.length - 1 }"
              @click="nextCard">明天 ›</view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { getUserPlans } from '@/api/request.js'

export default {
  data() {
    return {
      plans: [],
      loading: false,

      // 弹窗相关
      showModal: false,
      currentPlan: {},
      dailyPlans: [],
      currentIndex: 0,
      flipped: {},
      allFlipped: false
    }
  },
  onLoad() {
    this.checkLoginAndLoad()
  },
  onShow() {
    if (this.isLoggedIn) {
      this.loadPlans()
    }
  },
  computed: {
    isLoggedIn() {
      return !!uni.getStorageSync('token')
    }
  },
  methods: {
    checkLoginAndLoad() {
      const token = uni.getStorageSync('token')
      if (!token) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      this.loadPlans()
    },

    async loadPlans() {
      const userInfo = uni.getStorageSync('userInfo')
      if (!userInfo || !userInfo.id) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }

      this.loading = true
      try {
        const data = await getUserPlans(userInfo.id)
        this.plans = Array.isArray(data) ? data : []
      } catch (e) {
        console.error('加载行程失败', e)
        this.plans = []
      } finally {
        this.loading = false
      }
    },

    formatTime(timeStr) {
      if (!timeStr) return ''
      const d = new Date(timeStr)
      if (isNaN(d.getTime())) return ''
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${y}-${m}-${day}`
    },

    // 打开弹窗
    openCardModal(plan) {
      this.currentPlan = plan
      console.log('plan:', plan)
      // 兼容多种字段名
      let rawDailyPlans = plan.dailyPlans || plan.daily_plans || plan.dayPlans || []
      // 后端存的是 JSON 字符串，需要解析
      if (typeof rawDailyPlans === 'string') {
        try {
          rawDailyPlans = JSON.parse(rawDailyPlans)
          console.log('解析 dailyPlans 字符串成功，长度:', rawDailyPlans.length)
        } catch (e) {
          console.error('解析 dailyPlans 失败', e, rawDailyPlans)
          rawDailyPlans = []
        }
      }
      // 兜底：确保是数组
      if (!Array.isArray(rawDailyPlans)) {
        rawDailyPlans = []
      }
      this.dailyPlans = rawDailyPlans
      this.currentIndex = 0
      const f = {}
      this.dailyPlans.forEach((_, idx) => { f[idx] = false })
      this.flipped = f
      this.allFlipped = false
      this.showModal = true
    },

    // 关闭弹窗
    closeModal() {
      this.showModal = false
    },

    // 切换单张卡片翻转
    toggleFlip(idx) {
      this.flipped = {
        ...this.flipped,
        [idx]: !this.flipped[idx]
      }
      this.updateAllFlippedState()
    },

    // 全部展开/合上
    toggleAllFlip() {
      this.allFlipped = !this.allFlipped
      const f = {}
      this.dailyPlans.forEach((_, idx) => { f[idx] = this.allFlipped })
      this.flipped = f
    },

    updateAllFlippedState() {
      const flippedCount = Object.values(this.flipped).filter(v => v).length
      this.allFlipped = flippedCount === this.dailyPlans.length && this.dailyPlans.length > 0
    },

    // swiper 切换
    onSwiperChange(e) {
      this.currentIndex = e.detail.current
    },

    // 上一张/下一张
    prevCard() {
      if (this.currentIndex > 0) {
        this.currentIndex--
      }
    },
    nextCard() {
      if (this.currentIndex < this.dailyPlans.length - 1) {
        this.currentIndex++
      }
    },

    // 计算当日总费用
    calcDayCost(activities) {
      if (!activities || activities.length === 0) return 0
      return activities.reduce((total, a) => total + (Number(a.cost) || 0), 0)
    },

    goPlan() {
      uni.switchTab({ url: '/pages/index/index' })
    }
  }
}
</script>

<style lang="scss" scoped>
.plan-list-container {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 40rpx;
}

.plan-header {
  background: #fff;
  padding: 40rpx 30rpx 24rpx;

  .plan-title {
    display: block;
    font-size: 40rpx;
    font-weight: 700;
    color: #333;
    margin-bottom: 8rpx;
  }

  .plan-subtitle {
    display: block;
    font-size: 24rpx;
    color: #999;
  }
}

.loading-state {
  padding: 200rpx 0;
  text-align: center;
  color: #999;
  font-size: 28rpx;
}

.empty-state {
  padding: 120rpx 0;
  text-align: center;

  .empty-icon {
    width: 240rpx;
    height: 240rpx;
    opacity: 0.5;
    margin-bottom: 24rpx;
  }

  .empty-text {
    display: block;
    font-size: 28rpx;
    color: #999;
    margin-bottom: 30rpx;
  }

  .go-plan-btn {
    background: #007AFF;
    color: #fff;
    font-size: 28rpx;
    border-radius: 40rpx;
    padding: 16rpx 60rpx;
  }
}

.plan-list {
  padding: 20rpx 30rpx;
}

.plan-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 30rpx;
  margin-bottom: 20rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);

  &:active {
    background: #f8f8f8;
  }

  .plan-item-main {
    flex: 1;

    .plan-destination {
      display: flex;
      align-items: center;
      gap: 8rpx;
      margin-bottom: 12rpx;

      .destination-icon {
        font-size: 28rpx;
      }

      .destination-name {
        font-size: 32rpx;
        font-weight: 600;
        color: #333;
      }
    }

    .plan-title-text {
      display: block;
      font-size: 28rpx;
      color: #666;
      line-height: 1.4;
      margin-bottom: 16rpx;
    }

    .plan-meta {
      display: flex;
      align-items: center;
      gap: 8rpx;
      font-size: 24rpx;
      color: #999;

      .meta-divider {
        color: #ddd;
      }
    }
  }

  .plan-item-arrow {
    font-size: 24rpx;
    color: #007AFF;
    white-space: nowrap;
  }
}

/* ============== 弹窗样式 ============== */
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

.modal-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 28rpx;
}

/* 卡片堆容器 */
.card-deck-container {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.card-deck {
  flex: 1;
}

.card-item {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 10rpx;
}

/* 单张卡片：3D 翻转核心 */
.day-card {
  width: 100%;
  height: 90%;
  position: relative;
  border-radius: 24rpx;
  transform-style: preserve-3d;
  transition: transform 0.7s cubic-bezier(0.4, 0, 0.2, 1);

  &.is-flipped {
    transform: rotateY(180deg);
  }
}

/* 卡片两面 */
.card-face {
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

/* 正面 */
.card-front {
  display: flex;
  flex-direction: column;

  .card-front-bg {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    overflow: hidden;
    z-index: 0;
    background: linear-gradient(180deg, #f8f9ff 0%, #fff 100%);

    .bg-icon {
      position: absolute;
      font-size: 200rpx;
      opacity: 0.06;

      &:nth-child(1) { top: -40rpx; right: -40rpx; }
      &:nth-child(2) { top: 50%; left: -40rpx; }
      &:nth-child(3) { bottom: -40rpx; right: 30rpx; }
    }
  }

  .day-header {
    padding: 24rpx 30rpx 0;
    z-index: 1;

    .day-badge {
      display: inline-block;
      background: linear-gradient(135deg, #667eea, #764ba2);
      color: #fff;
      font-size: 26rpx;
      font-weight: 600;
      padding: 6rpx 20rpx;
      border-radius: 20rpx;
      letter-spacing: 2rpx;
    }
  }

  .day-main {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 20rpx 30rpx;
    z-index: 1;

    .day-theme {
      font-size: 38rpx;
      font-weight: 700;
      color: #333;
      text-align: center;
      margin-bottom: 10rpx;
    }

    .day-day-label {
      font-size: 24rpx;
      color: #999;
      margin-bottom: 30rpx;
    }

    .day-stats {
      display: flex;
      width: 100%;
      background: linear-gradient(135deg, rgba(102, 126, 234, 0.08), rgba(118, 75, 162, 0.08));
      border-radius: 16rpx;
      padding: 22rpx 0;
      margin-bottom: 24rpx;

      .stat-block {
        flex: 1;
        text-align: center;
      }

      .stat-divider {
        width: 1rpx;
        background: rgba(0, 0, 0, 0.1);
      }

      .stat-num {
        display: block;
        font-size: 32rpx;
        font-weight: 700;
        color: #667eea;
      }

      .stat-label {
        display: block;
        font-size: 20rpx;
        color: #999;
        margin-top: 4rpx;
      }
    }

    .day-preview {
      width: 100%;

      .preview-item {
        display: flex;
        align-items: center;
        gap: 16rpx;
        padding: 8rpx 0;
        border-bottom: 1rpx dashed #eee;

        &:last-child {
          border-bottom: none;
        }

        .preview-time {
          font-size: 20rpx;
          color: #667eea;
          font-weight: 600;
          width: 120rpx;
          flex-shrink: 0;
        }

        .preview-name {
          font-size: 24rpx;
          color: #333;
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }

      .preview-more {
        display: block;
        font-size: 20rpx;
        color: #999;
        text-align: center;
        padding-top: 10rpx;
      }
    }
  }

  .card-footer {
    padding: 16rpx;
    text-align: center;
    z-index: 1;

    .flip-hint {
      font-size: 20rpx;
      color: #999;
      animation: pulse 2s infinite;
    }
  }
}

/* 背面 */
.card-back {
  transform: rotateY(180deg);
  display: flex;
  flex-direction: column;

  .back-header {
    padding: 18rpx 24rpx;
    background: linear-gradient(135deg, #667eea, #764ba2);
    color: #fff;
    flex-shrink: 0;

    .back-badge {
      display: inline-block;
      font-size: 20rpx;
      background: rgba(255, 255, 255, 0.25);
      padding: 3rpx 14rpx;
      border-radius: 14rpx;
      margin-bottom: 4rpx;
    }

    .back-theme {
      display: block;
      font-size: 28rpx;
      font-weight: 600;
    }
  }

  .activity-list {
    flex: 1;
    padding: 12rpx 16rpx;
  }

  .activity-card {
    background: #fafafa;
    border-radius: 10rpx;
    padding: 12rpx;
    margin-bottom: 10rpx;

    .activity-time-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 4rpx;

      .activity-time {
        font-size: 20rpx;
        color: #667eea;
        font-weight: 600;
      }

      .activity-cost {
        font-size: 18rpx;
        color: #d4880c;
        background: #fff3cd;
        padding: 1rpx 10rpx;
        border-radius: 10rpx;
      }
    }

    .activity-name {
      display: block;
      font-size: 24rpx;
      font-weight: 500;
      color: #333;
      margin-bottom: 4rpx;
    }

    .activity-loc,
    .activity-transport {
      display: block;
      font-size: 20rpx;
      color: #999;
      line-height: 1.5;
      margin-bottom: 2rpx;
    }

    .activity-desc {
      display: block;
      font-size: 22rpx;
      color: #666;
      line-height: 1.5;
      margin-top: 6rpx;
    }
  }

  .card-footer {
    padding: 16rpx;
    text-align: center;
    flex-shrink: 0;

    .flip-hint {
      font-size: 20rpx;
      color: #999;
      animation: pulse 2s infinite;
    }
  }
}

@keyframes pulse {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

/* 左右切换按钮 */
.nav-buttons {
  display: flex;
  justify-content: space-around;
  padding: 20rpx 60rpx 0;

  .nav-btn {
    background: rgba(255, 255, 255, 0.95);
    color: #667eea;
    font-size: 26rpx;
    padding: 16rpx 50rpx;
    border-radius: 50rpx;
    box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.2);

    &:active {
      transform: scale(0.95);
    }

    &.disabled {
      opacity: 0.4;
      pointer-events: none;
    }
  }
}
</style>