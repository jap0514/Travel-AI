<template>
  <view class="hotel-list-container">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="city-picker" @click="showCityPicker">
        <text class="city-name">{{ selectedCity || '选择城市' }}</text>
        <text class="arrow">V</text>
      </view>
      <view class="search-input-wrap">
        <input
          class="search-input"
          v-model="keyword"
          placeholder="搜索酒店名称"
          confirm-type="search"
          @confirm="searchHotel"
        />
        <image class="search-icon" src="/static/images/search.png" mode="aspectFit"></image>
      </view>
    </view>

    <!-- 筛选栏 -->
    <view class="filter-bar">
      <view class="filter-item" @click="toggleFilter('star')">
        <text :class="{ active: filters.star }">星级</text>
      </view>
      <view class="filter-item" @click="toggleFilter('price')">
        <text :class="{ active: filters.price }">价格</text>
      </view>
      <view class="filter-item" @click="toggleFilter('type')">
        <text :class="{ active: filters.type }">房型</text>
      </view>
      <view class="filter-item" @click="toggleFilter('facility')">
        <text :class="{ active: filters.facility }">设施</text>
      </view>
    </view>

    <!-- 酒店列表 -->
    <scroll-view class="hotel-list" scroll-y @scrolltolower="loadMore">
      <view v-if="hotels.length === 0" class="empty-state">
        <image class="empty-icon" src="/static/images/empty-hotel.png" mode="aspectFit"></image>
        <text class="empty-text">暂无酒店数据</text>
      </view>

      <view
        v-for="hotel in hotels"
        :key="hotel.id"
        class="hotel-card"
        @click="goDetail(hotel)"
      >
        <image class="hotel-image" :src="hotel.mainImage || '/static/images/hotel-default.jpg'" mode="aspectFill"></image>
        <view class="hotel-info">
          <view class="hotel-header">
            <text class="hotel-name">{{ hotel.name }}</text>
            <view class="hotel-star">
              <text v-for="n in hotel.star" :key="n">*</text>
              <text class="star-text">{{ hotel.star }}星级</text>
            </view>
          </view>
          <text class="hotel-address">{{ hotel.address }}</text>
          <view class="hotel-facilities">
            <text v-for="(facility, index) in hotel.facilities.slice(0, 3)" :key="index" class="facility-tag">
              {{ facility }}
            </text>
          </view>
          <view class="hotel-bottom">
            <text class="hotel-price">¥{{ hotel.startPrice }}<text class="price-unit">起/晚</text></text>
            <text class="hotel-rating">评分 {{ hotel.rating || '5.0' }}</text>
          </view>
        </view>
      </view>

      <view v-if="isLoading" class="loading-more">
        <text>加载中...</text>
      </view>
      <view v-if="noMore" class="no-more">
        <text>没有更多了</text>
      </view>
    </scroll-view>

    <!-- 城市选择弹出层 -->
    <view v-if="showCity" class="city-popup" @click="showCity = false">
      <view class="city-content" @click.stop>
        <view class="city-header">
          <text class="city-title">选择城市</text>
          <text class="city-close" @click="showCity = false">X</text>
        </view>
        <scroll-view class="city-list" scroll-y>
          <view
            v-for="city in cities"
            :key="city"
            class="city-item"
            :class="{ selected: city === selectedCity }"
            @click="selectCity(city)"
          >
            {{ city }}
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      selectedCity: '',
      keyword: '',
      showCity: false,
      isLoading: false,
      noMore: false,
      page: 1,
      pageSize: 10,
      cities: ['北京', '上海', '杭州', '成都', '三亚', '广州', '深圳', '重庆', '西安', '厦门'],
      hotels: [],
      filters: {
        star: false,
        price: false,
        type: false,
        facility: false
      }
    }
  },
  onLoad() {
    this.loadHotels()
  },
  onShow() {
    const city = uni.getStorageSync('selectedCity')
    if (city) {
      this.selectedCity = city
      this.searchHotel()
    }
  },
  methods: {
    showCityPicker() {
      this.showCity = true
    },

    selectCity(city) {
      this.selectedCity = city
      this.showCity = false
      uni.setStorageSync('selectedCity', city)
      this.searchHotel()
    },

    toggleFilter(type) {
      this.filters[type] = !this.filters[type]
    },

    searchHotel() {
      this.page = 1
      this.noMore = false
      this.hotels = []
      this.loadHotels()
    },

    loadHotels() {
      if (this.isLoading || this.noMore) return
      this.isLoading = true

      // TODO: 调用 /hotel/hotelInfo/getHotelByCity 接口
      // 模拟数据
      setTimeout(() => {
        const mockHotels = [
          {
            id: 1,
            name: '北京饭店',
            address: '北京市东城区东长安街33号',
            star: 5,
            startPrice: 888,
            rating: '4.8',
            facilities: ['免费WiFi', '停车场', '健身房', '游泳池'],
            mainImage: '/static/images/hotel1.jpg'
          },
          {
            id: 2,
            name: '上海外滩酒店',
            address: '上海市黄浦区外滩18号',
            star: 5,
            startPrice: 1288,
            rating: '4.9',
            facilities: ['海景房', 'SPA', '餐厅', '酒吧'],
            mainImage: '/static/images/hotel2.jpg'
          },
          {
            id: 3,
            name: '杭州西湖酒店',
            address: '杭州市西湖区西湖大道',
            star: 4,
            startPrice: 588,
            rating: '4.6',
            facilities: ['免费WiFi', '停车场', '会议室'],
            mainImage: '/static/images/hotel3.jpg'
          }
        ]

        if (this.page >= 3) {
          this.noMore = true
        } else {
          this.hotels = this.hotels.concat(mockHotels)
          this.page++
        }

        this.isLoading = false
      }, 1000)
    },

    loadMore() {
      this.loadHotels()
    },

    goDetail(hotel) {
      uni.navigateTo({
        url: `/pages/hotel/detail?id=${hotel.id}`
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.hotel-list-container {
  min-height: 100vh;
  background: #f5f5f5;
}

.search-bar {
  display: flex;
  align-items: center;
  padding: 20rpx 30rpx;
  background: #fff;

  .city-picker {
    display: flex;
    align-items: center;
    margin-right: 20rpx;

    .city-name {
      font-size: 28rpx;
      color: #333;
    }

    .arrow {
      font-size: 20rpx;
      color: #999;
      margin-left: 8rpx;
    }
  }

  .search-input-wrap {
    flex: 1;
    position: relative;

    .search-input {
      width: 100%;
      height: 64rpx;
      padding: 0 60rpx 0 30rpx;
      background: #f5f5f5;
      border-radius: 32rpx;
      font-size: 26rpx;
    }

    .search-icon {
      position: absolute;
      right: 20rpx;
      top: 50%;
      transform: translateY(-50%);
      width: 32rpx;
      height: 32rpx;
    }
  }
}

.filter-bar {
  display: flex;
  background: #fff;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f0f0;

  .filter-item {
    flex: 1;
    text-align: center;

    text {
      font-size: 26rpx;
      color: #666;
      padding: 8rpx 16rpx;
      border-radius: 20rpx;

      &.active {
        color: #007AFF;
        background: rgba(0, 122, 255, 0.1);
      }
    }
  }
}

.hotel-list {
  height: calc(100vh - 220rpx);
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
}

.hotel-card {
  display: flex;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;

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

    .hotel-header {
      display: flex;
      align-items: center;
      margin-bottom: 8rpx;

      .hotel-name {
        font-size: 30rpx;
        font-weight: 600;
        color: #333;
        margin-right: 12rpx;
      }

      .hotel-star {
        font-size: 20rpx;
        color: #ffc107;

        .star-text {
          color: #666;
          margin-left: 8rpx;
        }
      }
    }

    .hotel-address {
      font-size: 24rpx;
      color: #999;
      margin-bottom: 12rpx;
    }

    .hotel-facilities {
      display: flex;
      flex-wrap: wrap;

      .facility-tag {
        font-size: 22rpx;
        color: #666;
        background: #f5f5f5;
        padding: 4rpx 12rpx;
        border-radius: 8rpx;
        margin-right: 8rpx;
        margin-bottom: 8rpx;
      }
    }

    .hotel-bottom {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: auto;

      .hotel-price {
        font-size: 32rpx;
        color: #ff4d4f;
        font-weight: 600;

        .price-unit {
          font-size: 22rpx;
          color: #999;
          font-weight: normal;
        }
      }

      .hotel-rating {
        font-size: 24rpx;
        color: #666;
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

.city-popup {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
  z-index: 999;
}

.city-content {
  width: 100%;
  height: 70%;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;

  .city-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 30rpx;
    border-bottom: 1rpx solid #f0f0f0;

    .city-title {
      font-size: 32rpx;
      font-weight: 600;
      color: #333;
    }

    .city-close {
      font-size: 36rpx;
      color: #999;
    }
  }

  .city-list {
    height: calc(100% - 100rpx);
    padding: 20rpx 30rpx;

    .city-item {
      padding: 30rpx 0;
      font-size: 28rpx;
      color: #333;
      border-bottom: 1rpx solid #f0f0f0;

      &.selected {
        color: #007AFF;
      }
    }
  }
}
</style>
