<template>
  <view class="detail-container">
    <!-- 顶部标题栏 -->
    <view class="detail-header">
      <view class="header-back" @click="goBack">‹</view>
      <view class="header-title-wrap">
        <text class="header-title">{{ title }}</text>
        <text class="header-sub">{{ destination }} · {{ days }}天 · 第 {{ currentIndex + 1 }}/{{ dailyPlans.length }} 天</text>
      </view>
      <view class="header-flip-btn" @click="toggleAllFlip">
        <text>{{ allFlipped ? '合上' : '展开' }}</text>
      </view>
    </view>

    <!-- 加载/空状态 -->
    <view v-if="dailyPlans.length === 0" class="empty-state">
      <text>暂无详细行程数据</text>
    </view>

    <!-- 每天一张卡片：可翻转的卡片堆 -->
    <view v-else class="card-deck-container">
      <swiper
        class="card-deck"
        :current="currentIndex"
        @change="onSwiperChange"
        :indicator-dots="true"
        indicator-color="rgba(255,255,255,0.5)"
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
            :class="{ 'is-flipped': flipped[idx], 'is-active': currentIndex === idx }"
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
</template>

<script>
export default {
  data() {
    return {
      planId: null,
      title: '',
      destination: '',
      days: 1,
      dailyPlans: [],
      currentIndex: 0,
      flipped: {},      // 每张卡片的翻转状态 {0: false, 1: false, ...}
      allFlipped: false
    }
  },
  onLoad(options) {
    this.planId = options.planId
    this.title = decodeURIComponent(options.title || '行程详情')
    this.destination = decodeURIComponent(options.destination || '')
    this.days = Number(options.days) || 1
    try {
      this.dailyPlans = JSON.parse(decodeURIComponent(options.dailyPlans || '[]'))
    } catch (e) {
      console.error('解析 dailyPlans 失败', e)
      this.dailyPlans = []
    }
  },
  computed: {
    currentDay() {
      return this.dailyPlans[this.currentIndex]
    }
  },
  methods: {
    goBack() {
      uni.navigateBack()
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
    }
  }
}
</script>

<style lang="scss" scoped>
.detail-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding-bottom: 40rpx;
}

/* 顶部标题栏 */
.detail-header {
  display: flex;
  align-items: center;
  padding: 30rpx 20rpx 20rpx;
  gap: 16rpx;

  .header-back {
    width: 60rpx;
    height: 60rpx;
    line-height: 60rpx;
    text-align: center;
    font-size: 48rpx;
    color: #fff;
    background: rgba(255, 255, 255, 0.2);
    border-radius: 50%;
  }

  .header-title-wrap {
    flex: 1;
    min-width: 0;

    .header-title {
      display: block;
      font-size: 36rpx;
      font-weight: 600;
      color: #fff;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .header-sub {
      display: block;
      font-size: 22rpx;
      color: rgba(255, 255, 255, 0.85);
      margin-top: 4rpx;
    }
  }

  .header-flip-btn {
    background: rgba(255, 255, 255, 0.25);
    color: #fff;
    font-size: 24rpx;
    padding: 14rpx 24rpx;
    border-radius: 30rpx;
  }
}

.empty-state {
  text-align: center;
  color: #fff;
  padding: 200rpx 0;
  font-size: 28rpx;
}

/* 卡片堆容器 */
.card-deck-container {
  margin-top: 20rpx;
}

.card-deck {
  height: 1100rpx;
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
  height: 1050rpx;
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
  box-shadow: 0 10rpx 30rpx rgba(0, 0, 0, 0.2);
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
      font-size: 240rpx;
      opacity: 0.06;

      &:nth-child(1) { top: -40rpx; right: -40rpx; }
      &:nth-child(2) { top: 50%; left: -40rpx; }
      &:nth-child(3) { bottom: -40rpx; right: 30rpx; }
    }
  }

  .day-header {
    padding: 30rpx 30rpx 0;
    z-index: 1;

    .day-badge {
      display: inline-block;
      background: linear-gradient(135deg, #667eea, #764ba2);
      color: #fff;
      font-size: 28rpx;
      font-weight: 600;
      padding: 8rpx 24rpx;
      border-radius: 24rpx;
      letter-spacing: 2rpx;
    }
  }

  .day-main {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 30rpx 40rpx;
    z-index: 1;

    .day-theme {
      font-size: 42rpx;
      font-weight: 700;
      color: #333;
      text-align: center;
      margin-bottom: 12rpx;
    }

    .day-day-label {
      font-size: 26rpx;
      color: #999;
      margin-bottom: 40rpx;
    }

    .day-stats {
      display: flex;
      width: 100%;
      background: linear-gradient(135deg, rgba(102, 126, 234, 0.08), rgba(118, 75, 162, 0.08));
      border-radius: 16rpx;
      padding: 28rpx 0;
      margin-bottom: 40rpx;

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
        font-size: 36rpx;
        font-weight: 700;
        color: #667eea;
      }

      .stat-label {
        display: block;
        font-size: 22rpx;
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
        padding: 12rpx 0;
        border-bottom: 1rpx dashed #eee;

        &:last-child {
          border-bottom: none;
        }

        .preview-time {
          font-size: 22rpx;
          color: #667eea;
          font-weight: 600;
          width: 130rpx;
          flex-shrink: 0;
        }

        .preview-name {
          font-size: 26rpx;
          color: #333;
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }

      .preview-more {
        display: block;
        font-size: 22rpx;
        color: #999;
        text-align: center;
        padding-top: 12rpx;
      }
    }
  }

  .card-footer {
    padding: 20rpx;
    text-align: center;
    z-index: 1;

    .flip-hint {
      font-size: 22rpx;
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
    padding: 24rpx 30rpx;
    background: linear-gradient(135deg, #667eea, #764ba2);
    color: #fff;

    .back-badge {
      display: inline-block;
      font-size: 22rpx;
      background: rgba(255, 255, 255, 0.25);
      padding: 4rpx 16rpx;
      border-radius: 16rpx;
      margin-bottom: 6rpx;
    }

    .back-theme {
      display: block;
      font-size: 32rpx;
      font-weight: 600;
    }
  }

  .activity-list {
    flex: 1;
    padding: 16rpx 20rpx;
    max-height: 870rpx;
  }

  .activity-card {
    background: #fafafa;
    border-radius: 12rpx;
    padding: 16rpx;
    margin-bottom: 14rpx;

    .activity-time-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 6rpx;

      .activity-time {
        font-size: 22rpx;
        color: #667eea;
        font-weight: 600;
      }

      .activity-cost {
        font-size: 20rpx;
        color: #d4880c;
        background: #fff3cd;
        padding: 2rpx 12rpx;
        border-radius: 12rpx;
      }
    }

    .activity-name {
      display: block;
      font-size: 28rpx;
      font-weight: 500;
      color: #333;
      margin-bottom: 6rpx;
    }

    .activity-loc,
    .activity-transport {
      display: block;
      font-size: 22rpx;
      color: #999;
      line-height: 1.5;
      margin-bottom: 4rpx;
    }

    .activity-desc {
      display: block;
      font-size: 24rpx;
      color: #666;
      line-height: 1.5;
      margin-top: 8rpx;
    }
  }

  .card-footer {
    padding: 20rpx;
    text-align: center;

    .flip-hint {
      font-size: 22rpx;
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
  padding: 30rpx 60rpx 0;

  .nav-btn {
    background: rgba(255, 255, 255, 0.95);
    color: #667eea;
    font-size: 28rpx;
    padding: 20rpx 50rpx;
    border-radius: 50rpx;
    box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.1);

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