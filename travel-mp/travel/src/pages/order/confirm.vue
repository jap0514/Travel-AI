<template>
  <view class="confirm-container">
    <!-- 酒店信息 -->
    <view class="section hotel-info">
      <image class="hotel-img" :src="hotel.image || '/static/images/hotel-default.jpg'" mode="aspectFill"></image>
      <view class="hotel-detail">
        <text class="hotel-name">{{ hotel.name }}</text>
        <text class="hotel-room">{{ room.name }}</text>
        <text class="hotel-dates">{{ checkIn }} ~ {{ checkOut }}（{{ nightCount }}晚）</text>
      </view>
    </view>

    <!-- 入住人信息 -->
    <view class="section">
      <text class="section-title">入住人信息</text>
      <view class="form-item">
        <text class="form-label">姓名</text>
        <input class="form-input"
               v-model="guestName"
               placeholder="请输入入住人姓名"
               maxlength="20" />
      </view>
      <view class="form-item">
        <text class="form-label">手机</text>
        <input class="form-input"
               v-model="guestPhone"
               placeholder="请输入手机号"
               type="number"
               maxlength="11" />
      </view>
    </view>

    <!-- 价格明细 -->
    <view class="section">
      <text class="section-title">价格明细</text>
      <view class="price-row">
        <text>¥{{ room.price }} × {{ nightCount }}晚</text>
        <text>¥{{ totalPrice }}</text>
      </view>
      <view class="price-row total">
        <text>合计</text>
        <text class="price-total">¥{{ totalPrice }}</text>
      </view>
    </view>

    <!-- 提交按钮 -->
    <view class="bottom-bar">
      <view class="price-info">
        <text class="total-price">¥{{ totalPrice }}</text>
        <text class="price-tip">{{ nightCount }}晚</text>
      </view>
      <button class="submit-btn"
              :disabled="submitting"
              @click="submitOrder">提交订单</button>
    </view>
  </view>
</template>

<script>
import { getIdempotentToken, createOrder } from '@/api/request.js'

export default {
  data() {
    return {
      hotelId: null,
      roomId: null,
      checkIn: '',
      checkOut: '',
      totalPrice: 0,
      nightCount: 0,
      hotel: { name: '', image: '' },
      room: { name: '', price: 0 },
      guestName: '',
      guestPhone: '',
      submitting: false
    }
  },
  onLoad(options) {
    this.hotelId = options.hotelId
    this.roomId = options.roomId
    this.roomNo = options.roomNo || ''  // 详情页选好的房间号
    this.checkIn = options.checkIn
    this.checkOut = options.checkOut
    this.totalPrice = Number(options.price) || 0
    this.calcNights()
    this.loadDetail()
  },
  methods: {
    // 把"M月D日"格式转换为 ISO LocalDateTime 字符串（如 "2026-08-21T14:00:00"）
    parseDateFull(dateStr) {
      if (!dateStr) return null
      const m = dateStr.match(/(\d+)月(\d+)日/)
      if (!m) return null
      const month = String(m[1]).padStart(2, '0')
      const day = String(m[2]).padStart(2, '0')
      const year = new Date().getFullYear()
      // 默认 14:00 入住，次日 12:00 离店（酒店行业惯例）
      return `${year}-${month}-${day}T14:00:00`
    },

    calcNights() {
      // checkIn/checkOut 格式：M月D日
      const m1 = this.checkIn.match(/(\d+)月(\d+)日/)
      const m2 = this.checkOut.match(/(\d+)月(\d+)日/)
      if (!m1 || !m2) {
        this.nightCount = 1
        return
      }
      const d1 = new Date(2026, Number(m1[1]) - 1, Number(m1[2]))
      const d2 = new Date(2026, Number(m2[1]) - 1, Number(m2[2]))
      const diff = (d2 - d1) / (1000 * 60 * 60 * 24)
      this.nightCount = diff > 0 ? diff : 1
      // 计算单价（用于显示）
      this.room.price = this.totalPrice / this.nightCount
    },

    loadDetail() {
      // 简单展示酒店和房型名（如果有缓存可以直接读，这里只显示大致信息）
      this.hotel.name = '酒店'
      this.room.name = '房型'
    },

    async submitOrder() {
      // 校验
      if (!this.guestName) {
        uni.showToast({ title: '请输入入住人姓名', icon: 'none' })
        return
      }
      if (!this.guestPhone || this.guestPhone.length !== 11) {
        uni.showToast({ title: '请输入11位手机号', icon: 'none' })
        return
      }

      this.submitting = true
      try {
        // 1. 获取幂等 token
        const tokenRes = await getIdempotentToken()
        const idempotentToken = tokenRes.token

        // 2. 提交订单（日期转 LocalDateTime 格式）
        const userInfo = uni.getStorageSync('userInfo')
        await createOrder({
          hotelId: Number(this.hotelId),
          roomTypeId: Number(this.roomId),   // 后端字段名是 roomTypeId
          roomNo: this.roomNo,                // 房间号
          guestName: this.guestName,
          guestPhone: this.guestPhone,
          checkInDate: this.parseDateFull(this.checkIn),
          checkOutDate: this.parseDateFull(this.checkOut),
          idempotentToken: idempotentToken,
          userId: userInfo.id
        })

        uni.showToast({ title: '预订成功', icon: 'success' })
        setTimeout(() => {
          uni.switchTab({ url: '/pages/order/list' })
        }, 1000)
      } catch (e) {
        console.error('提交订单失败', e)
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.confirm-container {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 120rpx;
}

.section {
  background: #fff;
  margin: 20rpx;
  padding: 30rpx;
  border-radius: 12rpx;
}

.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #333;
  margin-bottom: 24rpx;
}

.hotel-info {
  display: flex;
  gap: 20rpx;

  .hotel-img {
    width: 180rpx;
    height: 180rpx;
    border-radius: 12rpx;
    background: #ddd;
    flex-shrink: 0;
  }

  .hotel-detail {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;

    .hotel-name {
      font-size: 32rpx;
      font-weight: 600;
      color: #333;
      margin-bottom: 12rpx;
    }

    .hotel-room {
      font-size: 26rpx;
      color: #666;
      margin-bottom: 8rpx;
    }

    .hotel-dates {
      font-size: 24rpx;
      color: #999;
    }
  }
}

.form-item {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f0f0;

  &:last-child { border-bottom: none; }

  .form-label {
    width: 120rpx;
    font-size: 28rpx;
    color: #333;
  }

  .form-input {
    flex: 1;
    font-size: 28rpx;
    color: #333;
  }
}

.price-row {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 0;
  font-size: 28rpx;
  color: #666;

  &.total {
    border-top: 1rpx solid #f0f0f0;
    margin-top: 12rpx;
    padding-top: 20rpx;
    font-weight: 600;
    color: #333;
    font-size: 30rpx;
  }

  .price-total {
    color: #ff4d4f;
    font-size: 36rpx;
  }
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  background: #fff;
  padding: 16rpx 30rpx;
  box-shadow: 0 -2rpx 8rpx rgba(0, 0, 0, 0.06);

  .price-info {
    flex: 1;
    display: flex;
    flex-direction: column;

    .total-price {
      font-size: 36rpx;
      font-weight: 700;
      color: #ff4d4f;
    }

    .price-tip {
      font-size: 22rpx;
      color: #999;
    }
  }

  .submit-btn {
    background: #007AFF;
    color: #fff;
    font-size: 30rpx;
    border-radius: 40rpx;
    padding: 16rpx 50rpx;

    &[disabled] {
      opacity: 0.6;
    }
  }
}
</style>