<template>
  <view class="index-container">
    <!-- 顶部欢迎区 -->
    <view class="header">
      <view class="user-info" @click="goLogin">
        <image class="avatar" src="" mode="aspectFill"></image>
        <text class="nickname">{{ isLogin ? userInfo.nickname : '点击登录' }}</text>
      </view>
      <text class="subtitle">AI智能规划您的专属旅行</text>
    </view>

    <!-- Banner轮播 -->
    <swiper class="banner" indicator-dots autoplay circular>
      <swiper-item v-for="(item, index) in banners" :key="index">
        <image class="banner-img" :src="item.image" mode="aspectFill"></image>
      </swiper-item>
    </swiper>

    <!-- 热门目的地 -->
    <view class="section">
      <view class="section-header">
        <text class="section-title">热门目的地</text>
        <text class="section-more" @click="goRanking">更多></text>
      </view>
      <scroll-view class="destination-scroll" scroll-x>
        <view class="destination-item" v-for="item in hotDestinations" :key="item.id">
          <image class="destination-img" :src="item.image" mode="aspectFill"></image>
          <text class="destination-name">{{ item.name }}</text>
          <text class="destination-count">{{ item.count }}人去过</text>
        </view>
      </scroll-view>
    </view>

    <!-- 功能入口 -->
    <view class="function-grid">
      <view class="function-item" @click="goChat">
        <image class="function-icon" src="" mode="aspectFit"></image>
        <text class="function-text">AI规划行程</text>
      </view>
      <view class="function-item" @click="goHotel">
        <image class="function-icon" src="" mode="aspectFit"></image>
        <text class="function-text">预订酒店</text>
      </view>
      <view class="function-item" @click="goOrder">
        <image class="function-icon" src="" mode="aspectFit"></image>
        <text class="function-text">我的订单</text>
      </view>
      <view class="function-item" @click="goMy">
        <image class="function-icon" src="" mode="aspectFit"></image>
        <text class="function-text">我的目的地</text>
      </view>
    </view>

    <!-- AI规划入口 -->
    <view class="ai-entry" @click="goChat">
      <view class="ai-entry-content">
        <text class="ai-title">开始智能规划</text>
        <text class="ai-desc">告诉AI你的旅行想法，为你生成专属行程</text>
      </view>
      <text class="ai-arrow">></text>
    </view>
  </view>
</template>

<script>
import { getPopularDestinations } from '@/api/request.js'
	
export default {
  data() {
    return {
      isLogin: false,
      userInfo: {},
      banners: [
        { image: '' },
        { image: '' },
        { image: '' }
      ],
      hotDestinations: [
        { id: 1, name: '北京', image: '', count: 1234 },
        { id: 2, name: '上海', image: '', count: 987 },
        { id: 3, name: '杭州', image: '', count: 856 },
        { id: 4, name: '成都', image: '', count: 765 },
        { id: 5, name: '三亚', image: '', count: 654 }
      ]
    }
  },
  onLoad() {
    this.checkLogin()
  },
  onShow() {
    this.checkLogin()
	this.gethotDestinations()
  },
  methods: {
    checkLogin() {
      const token = uni.getStorageSync('token')
      const userInfo = uni.getStorageSync('userInfo')
      if (token && userInfo) {
        this.isLogin = true
        this.userInfo = userInfo
      }
    },
    goLogin() {
      if (!this.isLogin) {
        uni.navigateTo({ url: '/pages/login/login' })
      }
    },
    goChat() {
      if (!this.isLogin) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.navigateTo({ url: '/pages/chat/chat' })
    },
    goHotel() {
      uni.switchTab({ url: '/pages/hotel/list' })
    },

    goRanking() {
      uni.navigateTo({ url: '/pages/ranking/list' })
    },
    goOrder() {
      if (!this.isLogin) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.switchTab({ url: '/pages/order/list' })
    },
    goMy() {
      uni.switchTab({ url: '/pages/my/my' })
    },
	async gethotDestinations(){
		try{
			// 首页热门目的地：统计所有时间的目的地（不限近几天）
			const data = await getPopularDestinations(9999)
			console.log('热门地点: ', data)
			if (data && data.length > 0) {
				this.hotDestinations = data.map(item => ({
					id: item.id || Math.random(),
					name: item.destination || item.name,
					image: '',
					count: item.count || 0
				}))
			}
		}catch(error){
			console.error('获取热门目的地失败', error);
		}
	}
  }
}
</script>

<style lang="scss" scoped>
.index-container {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 20rpx;
}

.header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40rpx 30rpx 60rpx;
  color: #fff;

  .user-info {
    display: flex;
    align-items: center;
    margin-bottom: 20rpx;

    .avatar {
      width: 80rpx;
      height: 80rpx;
      border-radius: 40rpx;
      border: 3rpx solid rgba(255,255,255,0.5);
      margin-right: 20rpx;
    }

    .nickname {
      font-size: 32rpx;
      font-weight: 500;
    }
  }

  .subtitle {
    font-size: 28rpx;
    opacity: 0.9;
  }
}

.banner {
  height: 320rpx;
  margin: -40rpx 30rpx 0;
  border-radius: 16rpx;
  overflow: hidden;

  .banner-img {
    width: 100%;
    height: 100%;
  }
}

.section {
  margin-top: 40rpx;
  padding: 0 30rpx;

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24rpx;

    .section-title {
      font-size: 32rpx;
      font-weight: 600;
      color: #333;
    }

    .section-more {
      font-size: 26rpx;
      color: #999;
    }
  }
}

.destination-scroll {
  white-space: nowrap;

  .destination-item {
    display: inline-block;
    width: 200rpx;
    margin-right: 20rpx;
    vertical-align: top;

    .destination-img {
      width: 200rpx;
      height: 200rpx;
      border-radius: 12rpx;
      background: #ddd;
    }

    .destination-name {
      display: block;
      font-size: 28rpx;
      font-weight: 500;
      color: #333;
      margin-top: 12rpx;
      text-align: center;
    }

    .destination-count {
      display: block;
      font-size: 22rpx;
      color: #999;
      text-align: center;
      margin-top: 4rpx;
    }
  }
}

.function-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20rpx;
  padding: 40rpx 30rpx;

  .function-item {
    display: flex;
    flex-direction: column;
    align-items: center;

    .function-icon {
      width: 80rpx;
      height: 80rpx;
      margin-bottom: 12rpx;
    }

    .function-text {
      font-size: 24rpx;
      color: #666;
    }
  }
}

.ai-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20rpx 30rpx;
  padding: 30rpx;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16rpx;
  color: #fff;

  .ai-title {
    font-size: 32rpx;
    font-weight: 600;
  }

  .ai-desc {
    font-size: 24rpx;
    opacity: 0.85;
    margin-top: 8rpx;
  }

  .ai-arrow {
    font-size: 36rpx;
  }
}
</style>
