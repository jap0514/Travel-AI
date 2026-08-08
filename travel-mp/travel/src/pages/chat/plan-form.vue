<template>
  <view class="plan-form-container">
    <view class="form-header">
      <text class="form-title">制定旅行计划</text>
      <text class="form-subtitle">请填写您的旅行信息</text>
    </view>

    <view class="form-content">
      <!-- 目的地 -->
      <view class="form-item">
        <text class="form-label">目的地</text>
        <input
          class="form-input"
          v-model="formData.destination"
          placeholder="例如：北京、上海、杭州"
        />
      </view>

      <!-- 出发日期 -->
      <view class="form-item">
        <text class="form-label">出发日期</text>
        <picker mode="date" :value="formData.startDate" @change="onDateChange">
          <view class="form-picker">
            <text :class="{ placeholder: !formData.startDate }">
              {{ formData.startDate || '请选择出发日期' }}
            </text>
            <text class="picker-arrow">></text>
          </view>
        </picker>
      </view>

      <!-- 旅游天数 -->
      <view class="form-item">
        <text class="form-label">旅游天数</text>
        <view class="days-selector">
          <view
            class="day-btn"
            :class="{ active: formData.days === 1 }"
            @click="formData.days = 1"
          >1天</view>
          <view
            class="day-btn"
            :class="{ active: formData.days === 2 }"
            @click="formData.days = 2"
          >2天</view>
          <view
            class="day-btn"
            :class="{ active: formData.days === 3 }"
            @click="formData.days = 3"
          >3天</view>
          <view
            class="day-btn"
            :class="{ active: formData.days === 4 }"
            @click="formData.days = 4"
          >4天</view>
          <view
            class="day-btn"
            :class="{ active: formData.days === 5 }"
            @click="formData.days = 5"
          >5天</view>
        </view>
      </view>
    </view>

    <view class="form-footer">
      <button class="cancel-btn" @click="onCancel">取消</button>
      <button class="confirm-btn" @click="onConfirm">开始规划</button>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      formData: {
        destination: '',
        startDate: '',
        days: 3
      }
    }
  },
  methods: {
    onDateChange(e) {
      this.formData.startDate = e.detail.value
    },

    onCancel() {
      uni.navigateBack()
    },

    onConfirm() {
      if (!this.formData.destination) {
        uni.showToast({ title: '请输入目的地', icon: 'none' })
        return
      }
      if (!this.formData.startDate) {
        uni.showToast({ title: '请选择出发日期', icon: 'none' })
        return
      }

      // 保存到本地存储，返回上一页时使用
      uni.setStorageSync('travelPlanInfo', this.formData)

      // 构建消息内容
      const content = `我想去${this.formData.destination}玩${this.formData.days}天，出发日期是${this.formData.startDate}`

      // 返回上一页并传递数据
      const pages = getCurrentPages()
      const prevPage = pages[pages.length - 2]
      if (prevPage) {
        prevPage.travelInfo = this.formData
        prevPage.inputMessage = content
      }

      uni.navigateBack()
    }
  }
}
</script>

<style lang="scss" scoped>
.plan-form-container {
  min-height: 100vh;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
}

.form-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 60rpx 40rpx;
  color: #fff;

  .form-title {
    font-size: 40rpx;
    font-weight: 600;
    display: block;
    margin-bottom: 12rpx;
  }

  .form-subtitle {
    font-size: 28rpx;
    opacity: 0.9;
  }
}

.form-content {
  flex: 1;
  padding: 40rpx 30rpx;
}

.form-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 30rpx;
  margin-bottom: 24rpx;

  .form-label {
    font-size: 28rpx;
    font-weight: 500;
    color: #333;
    display: block;
    margin-bottom: 16rpx;
  }

  .form-input {
    width: 100%;
    height: 80rpx;
    padding: 0 20rpx;
    background: #f5f5f5;
    border-radius: 12rpx;
    font-size: 28rpx;
  }

  .form-picker {
    display: flex;
    justify-content: space-between;
    align-items: center;
    height: 80rpx;
    padding: 0 20rpx;
    background: #f5f5f5;
    border-radius: 12rpx;

    .placeholder {
      color: #999;
    }

    .picker-arrow {
      color: #999;
    }
  }

  .days-selector {
    display: flex;
    gap: 16rpx;

    .day-btn {
      flex: 1;
      height: 72rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f5f5f5;
      border-radius: 12rpx;
      font-size: 26rpx;
      color: #666;

      &.active {
        background: #007AFF;
        color: #fff;
      }
    }
  }
}

.form-footer {
  display: flex;
  gap: 24rpx;
  padding: 30rpx;
  background: #fff;
  border-top: 1rpx solid #f0f0f0;

  .cancel-btn {
    flex: 1;
    height: 88rpx;
    background: #f5f5f5;
    color: #666;
    font-size: 30rpx;
    border-radius: 44rpx;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .confirm-btn {
    flex: 2;
    height: 88rpx;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: #fff;
    font-size: 30rpx;
    border-radius: 44rpx;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}
</style>
