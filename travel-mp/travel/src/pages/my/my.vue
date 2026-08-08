<template>
  <view class="my-container">
    <!-- 用户信息 -->
    <view class="user-section">
      <view class="user-bg"></view>
      <view class="user-content">
        <image class="avatar" :src="userInfo.avatar || '/static/images/avatar-default.png'" mode="aspectFill"></image>
        <view class="user-info">
          <text class="nickname">{{ userInfo.nickname || '未登录' }}</text>
          <text class="user-id" v-if="userInfo.id">ID: {{ userInfo.id }}</text>
        </view>
        <button v-if="!isLogin" class="login-btn" @click="goLogin">登录</button>
        <button v-else class="setting-btn" @click="goSettings">
          <image src="/static/images/settings.png" mode="aspectFit"></image>
        </button>
      </view>
    </view>

    <!-- 统计卡片 -->
    <view class="stats-card">
      <view class="stat-item" @click="goMyDestinations">
        <text class="stat-value">{{ stats.destinationCount }}</text>
        <text class="stat-label">去过目的地</text>
      </view>
      <view class="stat-divider"></view>
      <view class="stat-item" @click="goOrder">
        <text class="stat-value">{{ stats.orderCount }}</text>
        <text class="stat-label">订单数</text>
      </view>
      <view class="stat-divider"></view>
      <view class="stat-item">
        <text class="stat-value">{{ stats.sessionCount }}</text>
        <text class="stat-label">会话数</text>
      </view>
    </view>

    <!-- 功能列表 -->
    <view class="menu-section">
      <view class="menu-item" @click="goMyDestinations">
        <image class="menu-icon" src="/static/images/menu-destination.png" mode="aspectFit"></image>
        <text class="menu-text">我的目的地</text>
        <text class="menu-arrow">></text>
      </view>
      <view class="menu-item" @click="goOrder">
        <image class="menu-icon" src="/static/images/menu-order.png" mode="aspectFit"></image>
        <text class="menu-text">我的订单</text>
        <text class="menu-arrow">></text>
      </view>
      <view class="menu-item" @click="goChatHistory">
        <image class="menu-icon" src="/static/images/menu-chat.png" mode="aspectFit"></image>
        <text class="menu-text">聊天记录</text>
        <text class="menu-arrow">></text>
      </view>
    </view>

    <view class="menu-section">
      <view class="menu-item" @click="goAbout">
        <image class="menu-icon" src="/static/images/menu-about.png" mode="aspectFit"></image>
        <text class="menu-text">关于我们</text>
        <text class="menu-arrow">></text>
      </view>
      <view class="menu-item" @click="contactService">
        <image class="menu-icon" src="/static/images/menu-service.png" mode="aspectFit"></image>
        <text class="menu-text">联系客服</text>
        <text class="menu-arrow">></text>
      </view>
      <view class="menu-item" @click="goHelp">
        <image class="menu-icon" src="/static/images/menu-help.png" mode="aspectFit"></image>
        <text class="menu-text">帮助与反馈</text>
        <text class="menu-arrow">></text>
      </view>
    </view>

    <!-- 退出登录 -->
    <button v-if="isLogin" class="logout-btn" @click="logout">退出登录</button>
  </view>
</template>

<script>
import { getUserSessions,getOrderList,getUserDestinations } from '@/api/request.js'

export default {
  data() {
    return {
      isLogin: false,
      userInfo: {},
      stats: {
        destinationCount: 0,
        orderCount: 0,
        sessionCount: 0
      }
    }
  },
  onLoad() {
    this.checkLogin()
  },
  onShow() {
	console.log('my.vue onShow 被调用了')
    this.checkLogin()
  },
  methods: {
    checkLogin() {
      const token = uni.getStorageSync('token')
      const userInfo = uni.getStorageSync('userInfo')
      if (token && userInfo) {
        this.isLogin = true
        this.userInfo = userInfo
        this.loadStats()
      } else {
        this.isLogin = false
        this.userInfo = {}
        this.stats = { destinationCount: 0, orderCount: 0, sessionCount: 0 }
      }
    },

    async loadStats() {
      // TODO: 调用统计接口
	  const userId=this.userInfo.id
	  console.log(userId)
	  const sessions=await getUserSessions(userId,1,10)
	  const destinations=await getUserDestinations(userId)
	  const orders=await getOrderList(userId,1,10)
	  
      this.stats = {
        destinationCount: destinations.length || 0,
        orderCount: orders.length || 0,
        sessionCount: sessions.length || 0
      }
    },

    goLogin() {
      uni.navigateTo({ url: '/pages/login/login' })
    },

    goSettings() {
      uni.showToast({ title: '设置功能开发中', icon: 'none' })
    },

    goMyDestinations() {
      if (!this.isLogin) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      // TODO: 跳转到我的目的地页面
      uni.showToast({ title: '我的目的地', icon: 'none' })
    },

    goOrder() {
      if (!this.isLogin) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.switchTab({ url: '/pages/order/list' })
    },

    goChatHistory() {
      if (!this.isLogin) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.navigateTo({ url: '/pages/chat/chat?tab=history' })
    },

    goAbout() {
      uni.showToast({ title: '关于我们', icon: 'none' })
    },

    contactService() {
      uni.makePhoneCall({
        phoneNumber: '400-123-4567'
      })
    },

    goHelp() {
      uni.showToast({ title: '帮助与反馈', icon: 'none' })
    },

    logout() {
      uni.showModal({
        title: '提示',
        content: '确定要退出登录吗？',
        success: (res) => {
          if (res.confirm) {
            // TODO: 调用 /logout 接口
            uni.removeStorageSync('token')
            uni.removeStorageSync('userInfo')
            uni.showToast({ title: '已退出登录', icon: 'success' })
            this.checkLogin()
          }
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.my-container {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 60rpx;
}

.user-section {
  position: relative;
  margin-bottom: 20rpx;

  .user-bg {
    height: 280rpx;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  }

  .user-content {
    position: absolute;
    top: 120rpx;
    left: 30rpx;
    right: 30rpx;
    display: flex;
    align-items: center;
    background: #fff;
    border-radius: 20rpx;
    padding: 30rpx;
    box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.1);

    .avatar {
      width: 100rpx;
      height: 100rpx;
      border-radius: 50%;
      border: 4rpx solid #fff;
      box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.1);
    }

    .user-info {
      flex: 1;
      margin-left: 24rpx;

      .nickname {
        font-size: 32rpx;
        font-weight: 600;
        color: #333;
        display: block;
        margin-bottom: 8rpx;
      }

      .user-id {
        font-size: 24rpx;
        color: #999;
      }
    }

    .login-btn {
      width: 140rpx;
      height: 56rpx;
      background: #007AFF;
      color: #fff;
      font-size: 24rpx;
      border-radius: 28rpx;
    }

    .setting-btn {
      width: 56rpx;
      height: 56rpx;
      background: #f5f5f5;
      border-radius: 50%;
      padding: 0;
      display: flex;
      align-items: center;
      justify-content: center;

      image {
        width: 36rpx;
        height: 36rpx;
      }
    }
  }
}

.stats-card {
  display: flex;
  background: #fff;
  margin: 0 30rpx 20rpx;
  border-radius: 16rpx;
  padding: 30rpx 0;

  .stat-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;

    .stat-value {
      font-size: 40rpx;
      font-weight: 600;
      color: #333;
      margin-bottom: 8rpx;
    }

    .stat-label {
      font-size: 24rpx;
      color: #999;
    }
  }

  .stat-divider {
    width: 1rpx;
    background: #f0f0f0;
  }
}

.menu-section {
  background: #fff;
  margin-bottom: 20rpx;

  .menu-item {
    display: flex;
    align-items: center;
    padding: 30rpx;
    border-bottom: 1rpx solid #f5f5f5;

    &:last-child {
      border-bottom: none;
    }

    .menu-icon {
      width: 44rpx;
      height: 44rpx;
      margin-right: 20rpx;
    }

    .menu-text {
      flex: 1;
      font-size: 28rpx;
      color: #333;
    }

    .menu-arrow {
      font-size: 28rpx;
      color: #ccc;
    }
  }
}

.logout-btn {
  margin: 40rpx 30rpx;
  height: 88rpx;
  background: #fff;
  color: #ff4d4f;
  font-size: 28rpx;
  border-radius: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
