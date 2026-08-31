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
        <view v-for="(f, idx) in hotel.facilities" :key="idx" class="facility-item">
          <image class="facility-icon" src="/static/images/facility-default.png" mode="aspectFit"></image>
          <text class="facility-name">{{ f }}</text>
        </view>
      </view>
    </view>

    <!-- 房型列表 -->
    <view class="section room-section">
      <text class="section-title">房型选择</text>
      <view class="date-picker-row">
        <picker mode="date"
                :value="checkInDateFull"
                :start="todayStr"
                :end="maxDateStr"
                @change="onCheckInChange"
                class="date-picker">
          <view class="date-item">
            <text class="date-label">入住</text>
            <text class="date-value">{{ checkInDate || '选择日期' }}</text>
          </view>
        </picker>
        <view class="date-arrow">
          <text>-</text>
        </view>
        <picker mode="date"
                :value="checkOutDateFull"
                :start="minCheckOutDate"
                :end="maxDateStr"
                @change="onCheckOutChange"
                class="date-picker">
          <view class="date-item">
            <text class="date-label">离店</text>
            <text class="date-value">{{ checkOutDate || '选择日期' }}</text>
          </view>
        </picker>
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
            <text v-for="(f, idx) in filterFacilities(room.amenities, 2)" :key="idx" class="amenity-tag">
              {{ f }}
            </text>
          </view>
        </view>
        <view class="room-price-book">
          <text class="room-price">¥{{ room.price }}</text>
          <text class="room-price-unit">/晚</text>
          <button class="book-btn" @click.stop="goBookRoom(room)">选择</button>
        </view>
      </view>
    </view>

    <!-- 底部预订栏 -->
    <view class="bottom-bar">
      <view class="price-info">
        <text class="total-price">¥{{ totalPrice }}</text>
        <text class="price-tip">
          {{ nightCount }}晚<text v-if="selectedRoomNo"> · {{ selectedRoomNo }}号房</text>
        </text>
      </view>
      <button class="book-now-btn" @click="goBook">立即预订</button>
    </view>

    <!-- 房间号选择弹窗 -->
    <view v-if="showRoomPicker" class="room-picker-mask" @click="showRoomPicker = false">
      <view class="room-picker" @click.stop>
        <view class="room-picker-header">
          <text class="room-picker-title">选择房间号</text>
          <text class="room-picker-close" @click="showRoomPicker = false">×</text>
        </view>
        <view class="room-picker-subtitle">
          <text>{{ currentRoomType ? currentRoomType.name : '' }}（{{ currentRoomType ? currentRoomType.price : '' }}元/晚）</text>
        </view>
        <view v-if="selectedRoomNo" class="room-picker-selected">
          <text>当前已选：{{ selectedRoomNo }}号房</text>
        </view>
        <view v-if="loadingRooms" class="room-loading">
          <text>加载中...</text>
        </view>
        <view v-else-if="roomList.length === 0" class="room-empty">
          <text>暂无可用房间</text>
        </view>
        <scroll-view v-else class="room-list" scroll-y>
          <view
            v-for="room in roomList"
            :key="room.roomId"
            class="room-item"
            :class="{ selected: selectedRoomNo === room.roomNo }"
            @click="selectRoomNo(room)">
            <view class="room-no">
              <text>{{ room.roomNo }}</text>
              <text class="room-floor">{{ room.floor || '' }}楼</text>
            </view>
            <view class="room-status">
              <text :class="room.statusName === '可用' ? 'status-available' : 'status-unavailable'">
                {{ room.statusName }}
              </text>
              <text v-if="selectedRoomNo === room.roomNo" class="room-check">✓</text>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import { getHotelById, getHotelRoomType, getHotelRoom } from '@/api/request.js'

export default {
  data() {
    return {
      hotelId: null,
      hotel: {},
      roomTypes: [],
      checkInDate: '',
      checkOutDate: '',
      checkInDateFull: '',   // YYYY-MM-DD 格式（picker 用）
      checkOutDateFull: '',  // YYYY-MM-DD 格式（picker 用）
      nightCount: 0,
      selectedRoom: null,
      loading: false,
      todayStr: '',
      maxDateStr: '',
      // 房间号选择弹窗
      showRoomPicker: false,
      roomList: [],
      selectedRoomNo: '',
      currentRoomType: null,
      pendingBook: false,
      loadingRooms: false
    }
  },
  computed: {
    // 离店日期最早是入住日期的下一天
    minCheckOutDate() {
      if (!this.checkInDateFull) return this.todayStr
      const d = new Date(this.checkInDateFull)
      d.setDate(d.getDate() + 1)
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${y}-${m}-${day}`
    },
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

      // YYYY-MM-DD 格式给 picker 用
      this.checkInDateFull = this.formatFullDate(today)
      this.checkOutDateFull = this.formatFullDate(tomorrow)
      // M月D日 格式给显示用
      this.checkInDate = this.formatDate(today)
      this.checkOutDate = this.formatDate(tomorrow)
      // picker 可选范围：今天 ~ 一年后
      this.todayStr = this.formatFullDate(today)
      const max = new Date(today)
      max.setFullYear(max.getFullYear() + 1)
      this.maxDateStr = this.formatFullDate(max)
      this.nightCount = 1
    },

    formatDate(date) {
      return `${date.getMonth() + 1}月${date.getDate()}日`
    },

    formatFullDate(date) {
      const y = date.getFullYear()
      const m = String(date.getMonth() + 1).padStart(2, '0')
      const d = String(date.getDate()).padStart(2, '0')
      return `${y}-${m}-${d}`
    },

    // 入住日期变化
    onCheckInChange(e) {
      const dateStr = e.detail.value
      this.checkInDateFull = dateStr
      const [, m, d] = dateStr.split('-').map(Number)
      this.checkInDate = `${m}月${d}日`
      // 如果离店日期 <= 新入住日期，离店日期自动 +1
      if (this.checkOutDateFull <= dateStr) {
        const tomorrow = new Date(dateStr)
        tomorrow.setDate(tomorrow.getDate() + 1)
        this.checkOutDateFull = this.formatFullDate(tomorrow)
        this.checkOutDate = `${tomorrow.getMonth() + 1}月${tomorrow.getDate()}日`
      }
      this.recalcNights()
    },

    // 离店日期变化
    onCheckOutChange(e) {
      const dateStr = e.detail.value
      if (this.checkInDateFull && dateStr <= this.checkInDateFull) {
        uni.showToast({ title: '离店日期必须大于入住日期', icon: 'none' })
        return
      }
      this.checkOutDateFull = dateStr
      const [, m, d] = dateStr.split('-').map(Number)
      this.checkOutDate = `${m}月${d}日`
      this.recalcNights()
    },

    // 重新计算入住晚数
    recalcNights() {
      if (!this.checkInDateFull || !this.checkOutDateFull) {
        this.nightCount = 0
        return
      }
      const diff = (new Date(this.checkOutDateFull) - new Date(this.checkInDateFull)) / (1000 * 60 * 60 * 24)
      this.nightCount = diff > 0 ? diff : 0
    },

    // 设施数组截取前 N 个
    filterFacilities(facilities, limit) {
      if (!Array.isArray(facilities)) return []
      return typeof limit === 'number' ? facilities.slice(0, limit) : facilities
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

    // 点击"预订"按钮：先弹出房间号选择
    goBookRoom(room) {
      this.selectedRoom = room
      this.openRoomPicker()
    },

    // 点击"立即预订"按钮：先弹房间号选择
    goBook() {
      if (!this.selectedRoom) {
        uni.showToast({ title: '请先选择房型', icon: 'none' })
        return
      }
      this.openRoomPicker()
    },

    // 打开房间号选择弹窗
    async openRoomPicker() {
      this.showRoomPicker = true
      this.currentRoomType = this.selectedRoom
      await this.loadRoomList()
    },

    // 加载该房型的所有房间
    async loadRoomList() {
      if (!this.selectedRoom) return
      this.loadingRooms = true
      try {
        const list = await getHotelRoom(this.hotelId, this.selectedRoom.id)
        // 后端返回 statusName（"可用/不可用"），过滤可用的房间
        this.roomList = (list || []).filter(r => r.statusName === '可用')
      } catch (e) {
        console.error('加载房间失败', e)
        this.roomList = []
      } finally {
        this.loadingRooms = false
      }
    },

    // 选择房间号（直接关闭弹窗，标记已选）
    selectRoomNo(room) {
      if (room.statusName !== '可用') {
        uni.showToast({ title: '该房间不可预订', icon: 'none' })
        return
      }
      this.selectedRoomNo = room.roomNo
      this.showRoomPicker = false
      uni.showToast({ title: `已选房间 ${room.roomNo}`, icon: 'success' })
    },

    // 底部"立即预订"按钮：跳到确认页
    goBook() {
      if (!this.selectedRoom) {
        uni.showToast({ title: '请先选择房型', icon: 'none' })
        return
      }
      if (!this.selectedRoomNo) {
        uni.showToast({ title: '请先选择房间号', icon: 'none' })
        // 没选房间号，帮他弹出
        this.openRoomPicker()
        return
      }
      const token = uni.getStorageSync('token')
      if (!token) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.navigateTo({
        url: `/pages/order/confirm?hotelId=${this.hotelId}&roomId=${this.selectedRoom.id}&roomNo=${this.selectedRoomNo}&checkIn=${this.checkInDate}&checkOut=${this.checkOutDate}&price=${this.totalPrice}`
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

// 房间号选择弹窗
.room-picker-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.room-picker {
  width: 100%;
  max-height: 80vh;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  display: flex;
  flex-direction: column;
}

.room-picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 30rpx;
  border-bottom: 1rpx solid #f0f0f0;

  .room-picker-title {
    font-size: 32rpx;
    font-weight: 600;
    color: #333;
  }

  .room-picker-close {
    font-size: 48rpx;
    color: #999;
    line-height: 1;
  }
}

.room-picker-subtitle {
  padding: 16rpx 30rpx;
  font-size: 26rpx;
  color: #666;
  background: #fafafa;
}

.room-picker-selected {
  padding: 16rpx 30rpx;
  font-size: 26rpx;
  color: #007AFF;
  background: rgba(0, 122, 255, 0.08);
  border-bottom: 1rpx solid #f0f0f0;
}

.room-loading, .room-empty {
  padding: 80rpx 0;
  text-align: center;
  color: #999;
  font-size: 28rpx;
}

.room-list {
  flex: 1;
  max-height: 600rpx;
  padding: 0 30rpx;
}

.room-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 30rpx 0;
  border-bottom: 1rpx solid #f0f0f0;

  &:last-child { border-bottom: none; }

  &.selected {
    background: rgba(0, 122, 255, 0.05);
    margin: 0 -30rpx;
    padding: 30rpx;
  }

  .room-no {
    display: flex;
    flex-direction: column;
    gap: 8rpx;

    text:first-child {
      font-size: 32rpx;
      font-weight: 600;
      color: #333;
    }

    .room-floor {
      font-size: 22rpx;
      color: #999;
    }
  }

  .room-status {
    display: flex;
    align-items: center;
    gap: 16rpx;

    .status-available {
      color: #4cd964;
      font-size: 24rpx;
    }

    .status-unavailable {
      color: #999;
      font-size: 24rpx;
    }

    .room-check {
      color: #007AFF;
      font-size: 32rpx;
      font-weight: 700;
    }
  }
}

.room-picker-footer {
  padding: 20rpx 30rpx 40rpx;
  border-top: 1rpx solid #f0f0f0;

  .room-confirm-btn {
    width: 100%;
    background: #007AFF;
    color: #fff;
    font-size: 32rpx;
    border-radius: 12rpx;
    padding: 24rpx 0;

    &[disabled] {
      opacity: 0.6;
    }
  }
}
</style>
