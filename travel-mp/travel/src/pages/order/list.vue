<template>
  <view class="order-list-container">
    <!-- 状态Tab -->
    <view class="status-tabs">
      <view
        v-for="tab in tabs"
        :key="tab.status"
        class="tab-item"
        :class="{ active: currentTab === tab.status }"
        @click="switchTab(tab.status)"
      >
        {{ tab.name }}
      </view>
    </view>

    <!-- 订单列表 -->
    <scroll-view class="order-list" scroll-y @scrolltolower="loadMore">
      <view v-if="orders.length === 0" class="empty-state">
        <image class="empty-icon" src="/static/images/empty-order.png" mode="aspectFit"></image>
        <text class="empty-text">暂无订单</text>
        <button class="go-book-btn" @click="goHotel">去预订</button>
      </view>

      <view
        v-for="order in orders"
        :key="order.orderNo"
        class="order-card"
        @click="goDetail(order)"
      >
        <view class="order-header">
          <text class="order-no">订单号: {{ order.orderNo }}</text>
          <text class="order-status" :class="getStatusClass(order.status)">
            {{ getStatusText(order.status) }}
          </text>
        </view>

        <view class="order-content">
          <image class="room-image" :src="order.roomImage || '/static/images/room-default.jpg'" mode="aspectFill"></image>
          <view class="order-info">
            <text class="hotel-name">{{ order.hotelName }}</text>
            <text class="room-name">{{ order.roomName }}</text>
            <text class="order-date">{{ order.checkInDate }} - {{ order.checkOutDate }}</text>
          </view>
          <view class="order-price">
            <text class="price">¥{{ order.totalPrice }}</text>
            <text class="price-tip">{{ order.nightCount }}晚</text>
          </view>
        </view>

        <view class="order-actions" v-if="order.status === 0">
          <button class="action-btn cancel" @click.stop="cancelOrder(order)">取消</button>
          <button class="action-btn pay" @click.stop="payOrder(order)">去支付</button>
        </view>
      </view>

      <view v-if="isLoading" class="loading-more">
        <text>加载中...</text>
      </view>
      <view v-if="noMore && orders.length > 0" class="no-more">
        <text>没有更多了</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      currentTab: 'all',
      page: 1,
      pageSize: 10,
      isLoading: false,
      noMore: false,
      tabs: [
        { status: 'all', name: '全部' },
        { status: '0', name: '待支付' },
        { status: '1', name: '已支付' },
        { status: '2', name: '已确认' },
        { status: '4', name: '已完成' }
      ],
      orders: []
    }
  },
  onLoad() {
    this.checkLoginAndLoad()
  },
  onShow() {
    this.loadOrders()
  },
  methods: {
    checkLoginAndLoad() {
      const token = uni.getStorageSync('token')
      if (!token) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      this.loadOrders()
    },

    switchTab(status) {
      this.currentTab = status
      this.page = 1
      this.noMore = false
      this.orders = []
      this.loadOrders()
    },

    loadOrders() {
      if (this.isLoading || this.noMore) return
      this.isLoading = true

      // TODO: 调用 /hotel/order/list 接口
      setTimeout(() => {
        const mockOrders = [
          {
            orderNo: 'HT202401010001',
            hotelName: '北京饭店',
            roomName: '豪华间',
            roomImage: '/static/images/room2.jpg',
            checkInDate: '1月15日',
            checkOutDate: '1月17日',
            nightCount: 2,
            totalPrice: 1776,
            status: 0
          },
          {
            orderNo: 'HT202401010002',
            hotelName: '上海外滩酒店',
            roomName: '标准间',
            roomImage: '/static/images/room1.jpg',
            checkInDate: '1月20日',
            checkOutDate: '1月22日',
            nightCount: 2,
            totalPrice: 1176,
            status: 1
          },
          {
            orderNo: 'HT202401010003',
            hotelName: '杭州西湖酒店',
            roomName: '套房',
            roomImage: '/static/images/room3.jpg',
            checkInDate: '12月25日',
            checkOutDate: '12月27日',
            nightCount: 2,
            totalPrice: 3176,
            status: 4
          }
        ]

        if (this.currentTab === 'all') {
          this.orders = mockOrders
        } else {
          this.orders = mockOrders.filter(o => o.status === parseInt(this.currentTab))
        }

        this.noMore = true
        this.isLoading = false
      }, 1000)
    },

    loadMore() {
      this.loadOrders()
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

    goDetail(order) {
      uni.navigateTo({
        url: `/pages/order/detail?orderNo=${order.orderNo}`
      })
    },

    cancelOrder(order) {
      uni.showModal({
        title: '提示',
        content: '确定要取消该订单吗？',
        success: (res) => {
          if (res.confirm) {
            // TODO: 调用 /hotel/order/{orderNo}/cancel 接口
            uni.showToast({ title: '取消成功', icon: 'success' })
            this.loadOrders()
          }
        }
      })
    },

    payOrder(order) {
      // TODO: 调用 /hotel/order/{orderNo}/pay 接口
      uni.showToast({ title: '支付功能开发中', icon: 'none' })
    },

    goHotel() {
      uni.switchTab({ url: '/pages/hotel/list' })
    }
  }
}
</script>

<style lang="scss" scoped>
.order-list-container {
  min-height: 100vh;
  background: #f5f5f5;
}

.status-tabs {
  display: flex;
  background: #fff;
  padding: 0 20rpx;

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
}

.order-list {
  height: calc(100vh - 88rpx);
  padding: 20rpx 30rpx;
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

  .go-book-btn {
    margin-top: 40rpx;
    width: 240rpx;
    height: 72rpx;
    background: #007AFF;
    color: #fff;
    font-size: 28rpx;
    border-radius: 36rpx;
  }
}

.order-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;

  .order-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20rpx;
    padding-bottom: 20rpx;
    border-bottom: 1rpx solid #f0f0f0;

    .order-no {
      font-size: 24rpx;
      color: #999;
    }

    .order-status {
      font-size: 26rpx;
      font-weight: 500;

      &.pending { color: #ff9800; }
      &.paid { color: #07c160; }
      &.confirmed { color: #007AFF; }
      &.cancelled { color: #999; }
      &.completed { color: #07c160; }
    }
  }

  .order-content {
    display: flex;

    .room-image {
      width: 160rpx;
      height: 160rpx;
      border-radius: 8rpx;
      flex-shrink: 0;
      background: #ddd;
    }

    .order-info {
      flex: 1;
      margin-left: 20rpx;
      display: flex;
      flex-direction: column;

      .hotel-name {
        font-size: 28rpx;
        font-weight: 600;
        color: #333;
        margin-bottom: 8rpx;
      }

      .room-name {
        font-size: 26rpx;
        color: #666;
        margin-bottom: 8rpx;
      }

      .order-date {
        font-size: 24rpx;
        color: #999;
      }
    }

    .order-price {
      display: flex;
      flex-direction: column;
      align-items: flex-end;

      .price {
        font-size: 32rpx;
        color: #ff4d4f;
        font-weight: 600;
      }

      .price-tip {
        font-size: 22rpx;
        color: #999;
      }
    }
  }

  .order-actions {
    display: flex;
    justify-content: flex-end;
    margin-top: 20rpx;
    padding-top: 20rpx;
    border-top: 1rpx solid #f0f0f0;

    .action-btn {
      width: 140rpx;
      height: 56rpx;
      border-radius: 28rpx;
      font-size: 24rpx;
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
    }
  }
}

.loading-more, .no-more {
  text-align: center;
  padding: 30rpx;
  font-size: 24rpx;
  color: #999;
}
</style>
