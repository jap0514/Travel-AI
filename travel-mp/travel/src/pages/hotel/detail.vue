<template>
  <view class="hotel-detail-container">
    <!-- 酒店图片 -->
    <swiper class="hotel-images" indicator-dots circular>
      <swiper-item v-for="(img, index) in hotel.images" :key="index">
        <image class="hotel-image" :src="img" mode="aspectFill"></image>
      </swiper-item>
    </swiper>

    <!-- 酒店基本信息 -->
    <view class="hotel-base">
      <view class="hotel-name-row">
        <text class="hotel-name">{{ hotel.name }}</text>
        <view class="hotel-star">
          <text v-for="n in hotel.star" :key="n">*</text>
        </view>
      </view>
      <view class="hotel-address-row">
        <image class="location-icon" src="/static/images/location.png" mode="aspectFit"></image>
        <text class="hotel-address">{{ hotel.address }}</text>
      </view>
      <view class="hotel-rating-row">
        <text class="rating-score">{{ hotel.rating || '5.0' }}</text>
        <text class="rating-text">分</text>
        <text class="rating-count">| {{ hotel.commentCount || 0 }}条评价</text>
      </view>
    </view>

    <!-- 设施服务 -->
    <view class="section facilities-section">
      <text class="section-title">设施服务</text>
      <view class="facilities-grid">
        <view v-for="(value, key) in hotel.facilities" :key="key" class="facility-item">
          <image class="facility-icon" src="/static/images/facility-default.png" mode="aspectFit"></image>
          <text class="facility-name">
            {{ key }}<text v-if="Array.isArray(value)">：{{ value.join('、') }}</text>
          </text>
        </view>
      </view>
    </view>

    <!-- 房型列表 -->
    <view class="section room-section">
      <text class="section-title">房型选择</text>
      <view class="date-picker-row">
        <view class="date-item">
          <text class="date-label">入住</text>
          <text class="date-value">{{ checkInDate || '选择日期' }}</text>
        </view>
        <view class="date-arrow">
          <text>-</text>
        </view>
        <view class="date-item">
          <text class="date-label">离店</text>
          <text class="date-value">{{ checkOutDate || '选择日期' }}</text>
        </view>
        <view class="night-count">
          <text>{{ nightCount }}晚</text>
        </view>
      </view>

      <view
        v-for="room in roomTypes"
        :key="room.id"
        class="room-card"
        @click="selectRoom(room)"
      >
        <image class="room-image" :src="room.image || '/static/images/room-default.jpg'" mode="aspectFill"></image>
        <view class="room-info">
          <text class="room-name">{{ room.name }}</text>
          <view class="room-specs">
            <text>{{ room.bedType }}</text>
            <text>{{ room.area }}m²</text>
            <text>可住{{ room.capacity }}人</text>
          </view>
          <view class="room-facilities">
            <text v-for="(value, key) in filterFacilities(room.amenities, 2)" :key="key" class="amenity-tag">
              {{ key }}
            </text>
          </view>
        </view>
        <view class="room-price-book">
          <text class="room-price">¥{{ room.price }}</text>
          <text class="room-price-unit">/晚</text>
          <button class="book-btn" @click.stop="goBookRoom(room)">预订</button>
        </view>
      </view>
    </view>

    <!-- 底部预订栏 -->
    <view class="bottom-bar">
      <view class="price-info">
        <text class="total-price">¥{{ totalPrice }}</text>
        <text class="price-tip">{{ nightCount }}晚</text>
      </view>
      <button class="book-now-btn" @click="goBook">立即预订</button>
    </view>
  </view>
</template>

<script>
import { getHotelById, getHotelRoomType } from '@/api/request.js'

export default {
  data() {
    return {
      hotelId: null,
      hotel: {},
      roomTypes: [],
      checkInDate: '',
      checkOutDate: '',
      nightCount: 0,
      selectedRoom: null,
      loading: false
    }
  },
  computed: {
    totalPrice() {
      if (!this.selectedRoom) return 0
      return this.selectedRoom.price * this.nightCount
    }
  },
  onLoad(options) {
    this.hotelId = options.id
    this.initDates()
    this.loadHotelDetail()
  },
  methods: {
    initDates() {
      const today = new Date()
      const tomorrow = new Date(today)
      tomorrow.setDate(tomorrow.getDate() + 1)

      this.checkInDate = this.formatDate(today)
      this.checkOutDate = this.formatDate(tomorrow)
      this.nightCount = 1
    },

    formatDate(date) {
      return `${date.getMonth() + 1}月${date.getDate()}日`
    },

    // 过滤设施：只保留值为 truthy 的项，可选限制数量
    filterFacilities(facilities, limit) {
      if (!facilities || typeof facilities !== 'object') return {}
      const result = {}
      const keys = Object.keys(facilities).filter(k => facilities[k])
      const limited = typeof limit === 'number' ? keys.slice(0, limit) : keys
      limited.forEach(k => { result[k] = facilities[k] })
      return result
    },

    async loadHotelDetail() {
      if (!this.hotelId) return
      this.loading = true
      try {
        const [hotelVO, roomTypeList] = await Promise.all([
          getHotelById(this.hotelId),
          getHotelRoomType(this.hotelId)
        ])

        // 酒店基本信息
        this.hotel = {
          id: hotelVO.hotelId,
          name: hotelVO.name,
          address: hotelVO.address,
          star: hotelVO.star,
          rating: '5.0',          // 后端无此字段，给默认值
          commentCount: 0,        // 后端无此字段
          facilities: this.filterFacilities(hotelVO.facilities),
          images: hotelVO.mainImage ? [hotelVO.mainImage] : ['/static/images/hotel-default.jpg'],
          description: hotelVO.description
        }

        // 房型列表：id → roomTypeId, area 类型转换
        this.roomTypes = roomTypeList.map(rt => ({
          id: rt.roomTypeId,
          hotelId: rt.hotelId,
          hotelName: rt.hotelName,
          name: rt.name,
          price: Number(rt.price),
          capacity: rt.capacity,
          bedType: rt.bedType,
          area: parseFloat(rt.area) || 0,
          amenities: this.filterFacilities(rt.amenities),
          image: '/static/images/room-default.jpg'  // 后端无此字段
        }))
      } catch (e) {
        // 静默：request.js 已 toast 错误
      } finally {
        this.loading = false
      }
    },

    selectRoom(room) {
      this.selectedRoom = room
    },

    goBookRoom(room) {
      this.selectedRoom = room
      this.goBook()
    },

    goBook() {
      if (!this.selectedRoom) {
        uni.showToast({ title: '请选择房型', icon: 'none' })
        return
      }

      const token = uni.getStorageSync('token')
      if (!token) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }

      // TODO: 跳转到订单确认页面
      uni.navigateTo({
        url: `/pages/order/confirm?hotelId=${this.hotelId}&roomId=${this.selectedRoom.id}&checkIn=${this.checkInDate}&checkOut=${this.checkOutDate}&price=${this.totalPrice}`
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.hotel-detail-container {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 120rpx;
}

.hotel-images {
  height: 400rpx;

  .hotel-image {
    width: 100%;
    height: 100%;
  }
}

.hotel-base {
  background: #fff;
  padding: 30rpx;

  .hotel-name-row {
    display: flex;
    align-items: center;
    margin-bottom: 16rpx;

    .hotel-name {
      font-size: 36rpx;
      font-weight: 600;
      color: #333;
      margin-right: 16rpx;
    }

    .hotel-star {
      font-size: 20rpx;
      color: #ffc107;
    }
  }

  .hotel-address-row {
    display: flex;
    align-items: center;
    margin-bottom: 12rpx;

    .location-icon {
      width: 28rpx;
      height: 28rpx;
      margin-right: 8rpx;
    }

    .hotel-address {
      font-size: 26rpx;
      color: #666;
    }
  }

  .hotel-rating-row {
    display: flex;
    align-items: center;

    .rating-score {
      font-size: 36rpx;
      color: #ff4d4f;
      font-weight: 600;
    }

    .rating-text {
      font-size: 24rpx;
      color: #ff4d4f;
    }

    .rating-count {
      font-size: 24rpx;
      color: #999;
      margin-left: 16rpx;
    }
  }
}

.section {
  background: #fff;
  margin-top: 20rpx;
  padding: 30rpx;

  .section-title {
    font-size: 32rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 24rpx;
    display: block;
  }
}

.facilities-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20rpx;

  .facility-item {
    display: flex;
    flex-direction: column;
    align-items: center;

    .facility-icon {
      width: 56rpx;
      height: 56rpx;
      margin-bottom: 8rpx;
    }

    .facility-name {
      font-size: 22rpx;
      color: #666;
    }
  }
}

.room-section {
  .date-picker-row {
    display: flex;
    align-items: center;
    background: #f8f8f8;
    border-radius: 12rpx;
    padding: 24rpx;
    margin-bottom: 30rpx;

    .date-item {
      flex: 1;

      .date-label {
        font-size: 24rpx;
        color: #999;
        display: block;
        margin-bottom: 4rpx;
      }

      .date-value {
        font-size: 28rpx;
        color: #333;
      }
    }

    .date-arrow {
      padding: 0 30rpx;
      color: #999;
    }

    .night-count {
      background: #007AFF;
      color: #fff;
      padding: 8rpx 20rpx;
      border-radius: 20rpx;
      font-size: 24rpx;
    }
  }
}

.room-card {
  display: flex;
  background: #fff;
  border: 2rpx solid #f0f0f0;
  border-radius: 12rpx;
  padding: 20rpx;
  margin-bottom: 20rpx;

  .room-image {
    width: 180rpx;
    height: 180rpx;
    border-radius: 8rpx;
    flex-shrink: 0;
    background: #ddd;
  }

  .room-info {
    flex: 1;
    margin-left: 20rpx;

    .room-name {
      font-size: 28rpx;
      font-weight: 600;
      color: #333;
      display: block;
      margin-bottom: 8rpx;
    }

    .room-specs {
      display: flex;
      flex-wrap: wrap;
      font-size: 24rpx;
      color: #999;
      margin-bottom: 12rpx;

      text {
        margin-right: 16rpx;
      }
    }

    .room-facilities {
      display: flex;

      .amenity-tag {
        font-size: 22rpx;
        color: #666;
        background: #f5f5f5;
        padding: 4rpx 12rpx;
        border-radius: 6rpx;
        margin-right: 8rpx;
      }
    }
  }

  .room-price-book {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    justify-content: space-between;

    .room-price {
      font-size: 32rpx;
      color: #ff4d4f;
      font-weight: 600;
    }

    .room-price-unit {
      font-size: 22rpx;
      color: #999;
    }

    .book-btn {
      width: 120rpx;
      height: 56rpx;
      background: #07c160;
      color: #fff;
      font-size: 24rpx;
      border-radius: 28rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0;
    }
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
  justify-content: space-between;
  padding: 0 30rpx;
  border-top: 1rpx solid #f0f0f0;

  .price-info {
    display: flex;
    align-items: baseline;

    .total-price {
      font-size: 40rpx;
      color: #ff4d4f;
      font-weight: 600;
    }

    .price-tip {
      font-size: 24rpx;
      color: #999;
      margin-left: 8rpx;
    }
  }

  .book-now-btn {
    width: 240rpx;
    height: 80rpx;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: #fff;
    font-size: 30rpx;
    border-radius: 40rpx;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}
</style>
