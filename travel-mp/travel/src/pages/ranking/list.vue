<template>
  <view class="ranking-container">
    <!-- 顶部标题栏 -->
    <view class="ranking-header">
      <text class="ranking-title">热门目的地排行榜</text>
      <text class="ranking-subtitle">基于真实用户去过的次数统计</text>
    </view>

    <!-- 时间筛选 -->
    <view class="time-filter">
      <view class="time-item"
            v-for="t in timeOptions"
            :key="t.value"
            :class="{ active: selectedDays === t.value }"
            @click="selectTime(t.value)">
        <text>{{ t.label }}</text>
      </view>
    </view>

    <!-- 排行榜前三名特殊展示 -->
    <view class="top-three" v-if="ranking.length >= 3">
      <view class="top-item top-2">
        <image class="top-avatar" :src="ranking[1].image || defaultImage" mode="aspectFill"></image>
        <view class="top-rank rank-2">
          <text>2</text>
        </view>
        <text class="top-name">{{ ranking[1].name }}</text>
        <text class="top-count">{{ ranking[1].count }}人去过</text>
      </view>
      <view class="top-item top-1">
        <image class="top-avatar" :src="ranking[0].image || defaultImage" mode="aspectFill"></image>
        <view class="top-rank rank-1">
          <text>1</text>
        </view>
        <text class="top-name">{{ ranking[0].name }}</text>
        <text class="top-count">{{ ranking[0].count }}人去过</text>
      </view>
      <view class="top-item top-3">
        <image class="top-avatar" :src="ranking[2].image || defaultImage" mode="aspectFill"></image>
        <view class="top-rank rank-3">
          <text>3</text>
        </view>
        <text class="top-name">{{ ranking[2].name }}</text>
        <text class="top-count">{{ ranking[2].count }}人去过</text>
      </view>
    </view>

    <!-- 完整排行榜 -->
    <view class="ranking-list">
      <view class="list-header">
        <text class="col-rank">排名</text>
        <text class="col-name">目的地</text>
        <text class="col-count">去过人数</text>
      </view>

      <view v-if="ranking.length === 0" class="empty">
        <text>暂无数据</text>
      </view>

      <view v-for="(item, index) in ranking" :key="item.id || index" class="list-item">
        <view class="col-rank">
          <text class="rank-num" :class="{ top: index < 3 }">{{ index + 1 }}</text>
        </view>
        <view class="col-name">
          <image class="item-img" :src="item.image || defaultImage" mode="aspectFill"></image>
          <text class="item-name">{{ item.name }}</text>
        </view>
        <text class="col-count">{{ item.count }}人</text>
      </view>
    </view>
  </view>
</template>

<script>
import { getPopularDestinations } from '@/api/request.js'

export default {
  data() {
    return {
      ranking: [],
      selectedDays: 30,
      timeOptions: [
        { label: '近 7 天', value: 7 },
        { label: '近 30 天', value: 30 },
        { label: '近 90 天', value: 90 }
      ],
      defaultImage: '/static/images/destination-default.jpg'
    }
  },
  onLoad() {
    this.loadRanking()
  },
  methods: {
    selectTime(days) {
      this.selectedDays = days
      this.loadRanking()
    },
    async loadRanking() {
      try {
        const data = await getPopularDestinations(this.selectedDays)
        console.log('排行榜数据:', data)
        if (data && Array.isArray(data)) {
          this.ranking = data.map(item => ({
            id: item.id || Math.random(),
            name: item.destination || item.name || '未知',
            count: item.count || 0,
            image: item.image || ''
          }))
        } else {
          this.ranking = []
        }
      } catch (e) {
        console.error('加载排行榜失败', e)
        this.ranking = []
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.ranking-container {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 40rpx;
}

.ranking-header {
  padding: 40rpx 30rpx 30rpx;
  background: #fff;

  .ranking-title {
    display: block;
    font-size: 40rpx;
    font-weight: 700;
    color: #333;
    margin-bottom: 8rpx;
  }

  .ranking-subtitle {
    display: block;
    font-size: 24rpx;
    color: #999;
  }
}

// 时间筛选
.time-filter {
  display: flex;
  background: #fff;
  padding: 0 30rpx 20rpx;
  gap: 16rpx;

  .time-item {
    flex: 1;
    text-align: center;
    padding: 16rpx 0;
    border-radius: 12rpx;
    background: #f5f5f5;
    font-size: 26rpx;
    color: #666;
    transition: all 0.2s;

    &.active {
      background: #007AFF;
      color: #fff;
    }
  }
}

// 前三名特殊展示
.top-three {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 40rpx 30rpx 60rpx;
  background: linear-gradient(180deg, #fff 0%, #f5f5f5 100%);
  position: relative;

  .top-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    margin: 0 10rpx;

    .top-avatar {
      width: 140rpx;
      height: 140rpx;
      border-radius: 50%;
      background: #ddd;
      border: 4rpx solid #fff;
      box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.1);
    }

    .top-name {
      font-size: 28rpx;
      font-weight: 600;
      color: #333;
      margin-top: 16rpx;
    }

    .top-count {
      font-size: 22rpx;
      color: #999;
      margin-top: 4rpx;
    }

    .top-rank {
      width: 44rpx;
      height: 44rpx;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      font-weight: 700;
      margin-top: -20rpx;
      box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.15);
      z-index: 2;
    }

    &.top-1 .top-rank {
      background: #FFD700;
      font-size: 28rpx;
    }

    &.top-2 .top-rank {
      background: #C0C0C0;
      font-size: 26rpx;
    }

    &.top-3 .top-rank {
      background: #CD7F32;
      font-size: 24rpx;
    }
  }

  // 第一名比第二、三名高一些
  .top-1 {
    margin-top: -30rpx;

    .top-avatar {
      width: 180rpx;
      height: 180rpx;
    }
  }
}

// 完整排行榜
.ranking-list {
  background: #fff;
  margin: 0 20rpx;
  border-radius: 16rpx;
  overflow: hidden;

  .list-header {
    display: flex;
    padding: 24rpx 30rpx;
    background: #fafafa;
    font-size: 24rpx;
    color: #999;
    border-bottom: 1rpx solid #f0f0f0;

    .col-rank { width: 100rpx; text-align: center; }
    .col-name { flex: 1; }
    .col-count { width: 140rpx; text-align: right; }
  }

  .list-item {
    display: flex;
    align-items: center;
    padding: 24rpx 30rpx;
    border-bottom: 1rpx solid #f0f0f0;

    &:last-child { border-bottom: none; }

    .col-rank {
      width: 100rpx;
      text-align: center;

      .rank-num {
        font-size: 30rpx;
        color: #999;
        font-weight: 500;

        &.top {
          color: #007AFF;
          font-weight: 700;
        }
      }
    }

    .col-name {
      flex: 1;
      display: flex;
      align-items: center;

      .item-img {
        width: 70rpx;
        height: 70rpx;
        border-radius: 8rpx;
        background: #ddd;
        margin-right: 20rpx;
      }

      .item-name {
        font-size: 30rpx;
        color: #333;
      }
    }

    .col-count {
      width: 140rpx;
      text-align: right;
      font-size: 26rpx;
      color: #666;
    }
  }

  .empty {
    padding: 80rpx 0;
    text-align: center;
    color: #999;
    font-size: 28rpx;
  }
}
</style>