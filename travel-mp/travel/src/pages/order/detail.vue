<template>
  <view class="order-detail-container">
    <!-- 订单状态 -->
    <view class="status-banner" :class="getStatusClass(order.status)">
      <text class="status-icon">{{ getStatusIcon(order.status) }}</text>
      <text class="status-text">{{ getStatusText(order.status) }}</text>
    </view>

    <!-- 酒店信息 -->
    <view class="section hotel-section">
      <image class="hotel-image" :src="order.roomImage || '/static/images/room-default.jpg'" mode="aspectFill"></image>
      <view class="hotel-info">
        <text class="hotel-name">{{ order.hotelName }}</text>
        <text class="room-name">{{ order.roomName }}</text>
        <view class="date-row">
          <view class="date-item">
            <text class="date-label">入住</text>
            <text class="date-value">{{ order.checkInDate }}</text>
          </view>
          <view class="date-arrow">-</view>
          <view class="date-item">
            <text class="date-label">离店</text>
            <text class="date-value">{{ order.checkOutDate }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 订单信息 -->
    <view class="section info-section">
      <text class="section-title">订单信息</text>
      <view class="info-row">
        <text class="info-label">订单编号</text>
        <text class="info-value">{{ order.orderNo }}</text>
      </view>
      <view class="info-row">
        <text class="info-label">下单时间</text>
        <text class="info-value">{{ order.createTime }}</text>
      </view>
      <view class="info-row">
        <text class="info-label">入住天数</text>
        <text class="info-value">{{ order.nightCount }}晚</text>
      </view>
      <view class="info-row">
        <text class="info-label">联系电话</text>
        <text class="info-value">{{ order.guestPhone || '138****8888' }}</text>
      </view>
      <view class="info-row">
        <text class="info-label">入住人</text>
        <text class="info-value">{{ order.guestName || '张三' }}</text>
      </view>
    </view>

    <!-- 费用明细 -->
    <view class="section fee-section">
      <text class="section-title">费用明细</text>
      <view class="info-row">
        <text class="info-label">{{ order.roomName }}</text>
        <text class="info-value">¥{{ order.roomPrice }}/晚 x {{ order.nightCount }}</text>
      </view>
      <view class="info-row total-row">
        <text class="info-label">合计</text>
        <text class="info-value total-price">¥{{ order.totalPrice }}</text>
      </view>
    </view>

    <!-- 取消原因 -->
    <view v-if="order.status === 3" class="section cancel-section">
      <text class="section-title">取消原因</text>
      <text class="cancel-reason">{{ order.cancelReason || '用户主动取消' }}</text>
    </view>

    <!-- 底部操作 -->
    <view class="bottom-bar">
      <view v-if="order.status === 0" class="action-group">
        <button class="action-btn cancel" @click="cancelOrder">取消订单</button>
        <button class="action-btn pay" @click="payOrder">去支付</button>
      </view>
      <view v-else-if="order.status === 1" class="action-group">
        <button class="action-btn cancel" @click="cancelOrder">取消订单</button>
      </view>
      <view v-else-if="order.status === 4" class="action-group">
        <button class="action-btn rebook" @click="reBook">再次预订</button>
      </view>
      <view v-else class="action-group">
        <button class="action-btn secondary" @click="contactService">联系客服</button>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      orderNo: '',
      order: {}
    }
  },
  onLoad(options) {
    this.orderNo = options.orderNo
    this.loadOrderDetail()
  },
  methods: {
    loadOrderDetail() {
      // TODO: 调用 /hotel/order/{orderNo} 接口
      this.order = {
        orderNo: this.orderNo,
        hotelName: '北京饭店',
        roomName: '豪华间',
        roomImage: '/static/images/room2.jpg',
        checkInDate: '1月15日',
        checkOutDate: '1月17日',
        nightCount: 2,
        roomPrice: 888,
        totalPrice: 1776,
        status: 0,
        createTime: '2024-01-10 14:30:00',
        guestName: '张三',
        guestPhone: '138****8888'
      }
    },

    getStatusClass(status) {
      const classMap = {
        '0': 'pending',
        '1': 'paid',
        '2': 'confirmed',
        '3': 'cancelled',
        '4': 'completed'
      }
      return classMap[status] || ''
    },

    getStatusIcon(status) {
      const iconMap = {
        '0': '⏰',
        '1': '✓',
        '2': '✓',
        '3': '×',
        '4': '★'
      }
      return iconMap[status] || ''
    },

    getStatusText(status) {
      const textMap = {
        '0': '待支付',
        '1': '已支付',
        '2': '已确认',
        '3': '已取消',
        '4': '已完成'
      }
      return textMap[status] || ''
    },

    cancelOrder() {
      uni.showModal({
        title: '提示',
        content: '确定要取消该订单吗？',
        success: (res) => {
          if (res.confirm) {
            // TODO: 调用 /hotel/order/{orderNo}/cancel 接口
            uni.showToast({ title: '取消成功', icon: 'success' })
            setTimeout(() => {
              uni.navigateBack()
            }, 1500)
          }
        }
      })
    },

    payOrder() {
      // TODO: 调用 /hotel/order/{orderNo}/pay 接口
      uni.showToast({ title: '支付功能开发中', icon: 'none' })
    },

    reBook() {
      uni.navigateBack()
    },

    contactService() {
      uni.makePhoneCall({
        phoneNumber: '400-123-4567'
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.order-detail-container {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 120rpx;
}

.status-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
  color: #fff;
  font-size: 32rpx;

  .status-icon {
    margin-right: 16rpx;
    font-size: 40rpx;
  }

  &.pending { background: linear-gradient(135deg, #ff9800, #ffb74d); }
  &.paid, &.confirmed { background: linear-gradient(135deg, #07c160, #4caf50); }
  &.cancelled { background: #999; }
  &.completed { background: linear-gradient(135deg, #667eea, #764ba2); }
}

.section {
  background: #fff;
  margin-bottom: 20rpx;
  padding: 30rpx;
}

.hotel-section {
  display: flex;

  .hotel-image {
    width: 200rpx;
    height: 200rpx;
    border-radius: 12rpx;
    flex-shrink: 0;
    background: #ddd;
  }

  .hotel-info {
    flex: 1;
    margin-left: 24rpx;
    display: flex;
    flex-direction: column;

    .hotel-name {
      font-size: 30rpx;
      font-weight: 600;
      color: #333;
      margin-bottom: 8rpx;
    }

    .room-name {
      font-size: 26rpx;
      color: #666;
      margin-bottom: 16rpx;
    }

    .date-row {
      display: flex;
      align-items: center;
      margin-top: auto;

      .date-item {
        .date-label {
          font-size: 22rpx;
          color: #999;
          display: block;
        }

        .date-value {
          font-size: 26rpx;
          color: #333;
        }
      }

      .date-arrow {
        margin: 0 20rpx;
        color: #999;
      }
    }
  }
}

.info-section, .fee-section {
  .section-title {
    font-size: 30rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 20rpx;
    display: block;
  }

  .info-row {
    display: flex;
    justify-content: space-between;
    padding: 16rpx 0;
    border-bottom: 1rpx solid #f5f5f5;

    &:last-child {
      border-bottom: none;
    }

    .info-label {
      font-size: 26rpx;
      color: #666;
    }

    .info-value {
      font-size: 26rpx;
      color: #333;
    }
  }

  .total-row {
    margin-top: 16rpx;
    padding-top: 16rpx;
    border-top: 1rpx solid #f0f0f0;

    .total-price {
      font-size: 32rpx;
      color: #ff4d4f;
      font-weight: 600;
    }
  }
}

.cancel-section {
  .section-title {
    font-size: 30rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 16rpx;
    display: block;
  }

  .cancel-reason {
    font-size: 26rpx;
    color: #666;
  }
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 100rpx;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 30rpx;
  border-top: 1rpx solid #f0f0f0;

  .action-group {
    display: flex;

    .action-btn {
      width: 160rpx;
      height: 64rpx;
      border-radius: 32rpx;
      font-size: 26rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-left: 20rpx;
      padding: 0;

      &.cancel {
        background: #fff;
        color: #666;
        border: 1rpx solid #ddd;
      }

      &.pay {
        background: #07c160;
        color: #fff;
      }

      &.rebook {
        background: #007AFF;
        color: #fff;
      }

      &.secondary {
        background: #f5f5f5;
        color: #666;
      }
    }
  }
}
</style>
