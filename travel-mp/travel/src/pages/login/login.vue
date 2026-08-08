<template>
  <view class="login-container">
    <view class="login-header">
      <image class="logo" src="/static/images/logo.png" mode="aspectFit"></image>
      <text class="app-name">AI旅行规划</text>
      <text class="app-slogan">智能规划，畅享旅程</text>
    </view>

    <view class="login-content">
      <!-- 微信一键登录 -->
      <button class="login-btn" type="primary" @click="onWechatLogin">
        <image class="wechat-icon" src="/static/images/wechat.png" mode="aspectFit"></image>
        <text>微信授权登录</text>
      </button>

      <!-- 调试模式 -->
      <button class="login-btn debug-btn" @click="onDebugLogin">
        <text>调试模式-模拟登录</text>
      </button>

      <view class="tips">
        <text class="tip-text">登录即表示同意</text>
        <text class="link">《用户协议》</text>
        <text class="tip-text">和</text>
        <text class="link">《隐私政策》</text>
      </view>
    </view>

    <view class="login-footer">
      <text class="footer-text">遇到问题？联系客服</text>
    </view>
  </view>
</template>

<script>
import { login } from '@/api/request.js'

export default {
  data() {
    return {
      loading: false
    }
  },
  methods: {
    /**
     * 微信授权登录（标准流程）
     */
    async onWechatLogin() {
      if (this.loading) return

      uni.showLoading({ title: '正在登录...' })

      try {
        // 1. 获取微信登录凭证 code
        const loginRes = await uni.login({ provider: 'weixin' })
        console.log('微信 login result:', loginRes)

        if (!loginRes.code) {
          uni.hideLoading()
          uni.showToast({ title: '获取登录凭证失败', icon: 'none' })
          return
        }

        // 2. 把 code 发给后端，换取 Token
        await this.doLogin(loginRes.code)

      } catch (err) {
        console.error('微信登录失败:', err)
        uni.hideLoading()
        uni.showToast({ title: '登录失败', icon: 'none' })
      }
    },

    /**
     * 调试模式模拟登录
     */
    async onDebugLogin() {
      if (this.loading) return

      uni.showModal({
        title: '提示',
        content: '调试模式将使用模拟code登录，是否继续？',
        success: async (res) => {
          if (res.confirm) {
            await this.doLogin('debug_test_code_12345')
          }
        }
      })
    },

    /**
     * 执行登录
     */
    async doLogin(code) {
      if (this.loading) return
      this.loading = true

      try {
        // 调用后端登录接口
        const data = await login(code)
        console.log('登录成功:', data)

        // 保存 token 和用户信息
        uni.setStorageSync('token', data.token)
        uni.setStorageSync('userInfo', {
          id: data.userId,
          nickname: data.nickname,
          avatar: data.avatar
        })

        uni.hideLoading()
        uni.showToast({ title: '登录成功', icon: 'success' })

        // 延迟返回上一页
        setTimeout(() => {
          uni.navigateBack()
        }, 1500)

      } catch (err) {
        console.error('登录失败:', err)
        uni.hideLoading()
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  padding: 120rpx 60rpx 60rpx;
}

.login-header {
  display: flex;
  flex-direction: column;
  align-items: center;

  .logo {
    width: 160rpx;
    height: 160rpx;
    margin-bottom: 30rpx;
    background: #f0f0f0;
    border-radius: 20rpx;
  }

  .app-name {
    font-size: 40rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 16rpx;
  }

  .app-slogan {
    font-size: 28rpx;
    color: #999;
  }
}

.login-content {
  width: 100%;

  .login-btn {
    width: 100%;
    height: 96rpx;
    background: #07c160;
    border-radius: 48rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 32rpx;
    color: #fff;
    margin-bottom: 24rpx;

    .wechat-icon {
      width: 48rpx;
      height: 48rpx;
      margin-right: 16rpx;
    }
  }

  .debug-btn {
    background: #ff9800;
    font-size: 26rpx;
  }

  .tips {
    display: flex;
    justify-content: center;
    margin-top: 40rpx;
    font-size: 24rpx;
    color: #999;

    .link {
      color: #007AFF;
    }
  }
}

.login-footer {
  .footer-text {
    font-size: 24rpx;
    color: #999;
  }
}
</style>
