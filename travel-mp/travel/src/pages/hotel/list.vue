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
        <text :class="{ active: filters.star }">{{ filters.star ? `≥${minStar}星` : '星级' }}</text>
      </view>
      <view class="filter-item" @click="showPricePicker = !showPricePicker; showFacilityPicker = false">
        <text :class="{ active: filters.price }">{{ priceButtonText }} <text class="arrow-down">▼</text></text>
      </view>
      <view class="filter-item" @click="showFacilityPicker = !showFacilityPicker; showPricePicker = false">
        <text :class="{ active: filters.facility }">{{ facilityButtonText }} <text class="arrow-down">▼</text></text>
      </view>
    </view>

    <!-- 价格下拉：最低/最高范围输入 -->
    <view v-if="showPricePicker" class="facility-dropdown" @click="showPricePicker = false">
      <view class="facility-dropdown-content" @click.stop>
        <view class="price-range-row">
          <view class="price-input-wrap">
            <text class="price-label">最低</text>
            <input class="price-input"
                   type="number"
                   v-model.number="priceMinInput"
                   placeholder="不限"
                   @input="onPriceInput" />
            <text class="price-unit">¥</text>
          </view>
          <text class="price-dash">—</text>
          <view class="price-input-wrap">
            <text class="price-label">最高</text>
            <input class="price-input"
                   type="number"
                   v-model.number="priceMaxInput"
                   placeholder="不限"
                   @input="onPriceInput" />
            <text class="price-unit">¥</text>
          </view>
        </view>
        <view class="price-actions">
          <view class="price-action-btn price-cancel" @click="resetPrice">
            <text>清空</text>
          </view>
          <view class="price-action-btn price-confirm" @click="applyPriceRange">
            <text>确认</text>
          </view>
        </view>
        <view class="facility-divider"></view>
        <view class="facility-option facility-reset-option" @click="resetFilters">
          <text>重置全部筛选</text>
        </view>
      </view>
    </view>

    <!-- 设施下拉选择（多选） -->
    <view v-if="showFacilityPicker" class="facility-dropdown" @click="showFacilityPicker = false">
      <view class="facility-dropdown-content" @click.stop>
        <view v-for="f in availableFacilities"
              :key="f"
              class="facility-option facility-multi-option"
              :class="{ selected: isFacilitySelected(f) }"
              @click="toggleFacility(f)">
          <view class="checkbox">
            <text v-if="isFacilitySelected(f)">✓</text>
          </view>
          <text>{{ f }}</text>
        </view>
        <view class="facility-divider"></view>
        <view class="facility-option facility-reset-option" @click="resetFilters">
          <text>重置全部筛选</text>
        </view>
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
            <text v-for="(value, key) in getTopFacilities(hotel.facilities, 3)" :key="key" class="facility-tag">
              {{ key }}<text v-if="Array.isArray(value)">：{{ value.join('、') }}</text>
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
import { getHotelByCity } from '@/api/request.js'

export default {
  data() {
    return {
      selectedCity: '',
      keyword: '',
      showCity: false,
      showFacilityPicker: false,
      showPricePicker: false,
      isLoading: false,
      noMore: false,
      page: 1,
      pageSize: 10,
      cities: ['北京', '上海', '杭州', '成都', '三亚', '广州', '深圳', '重庆', '西安', '厦门'],
      hotels: [],
      filters: {
        star: false,
        price: false,
        facility: false
      },
      // 筛选参数
      minStar: 4,                  // 星级筛选的阈值
      priceMin: null,              // 价格范围：最低
      priceMax: null,              // 价格范围：最高
      priceMinInput: '',           // 输入框：最低价（字符串）
      priceMaxInput: '',           // 输入框：最高价（字符串）
      selectedFacilities: [],      // 用户选中的设施列表（多选）
      // 常用设施下拉选项
      availableFacilities: ['WiFi', '游泳池', '健身房', '停车场', '餐厅', 'SPA', '空调', '24小时前台', '行李寄存', '会议室']
      }
  },
  computed: {
    hasAnyFilter() {
      return this.filters.star || this.filters.price || this.filters.facility
    },
    priceButtonText() {
      if (this.priceMin != null && this.priceMax != null) {
        return `¥${this.priceMin}-${this.priceMax}`
      }
      if (this.priceMin != null) {
        return `≥¥${this.priceMin}`
      }
      if (this.priceMax != null) {
        return `≤¥${this.priceMax}`
      }
      return '价格'
    },
    facilityButtonText() {
      const n = this.selectedFacilities.length
      if (n === 0) return '设施'
      if (n === 1) return `含${this.selectedFacilities[0]}`
      if (n <= 3) return `含${this.selectedFacilities.join('、')}`
      return `含${this.selectedFacilities.slice(0, 2).join('、')}等${n}项`
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
      // 价格/设施都用专用下拉，不通过 toggleFilter 切换
      if (type === 'price' || type === 'facility') return
      this.filters[type] = !this.filters[type]
      this.searchHotel()
    },

    // 应用价格范围
    applyPriceRange() {
      const min = this.priceMinInput === '' || this.priceMinInput == null
        ? null : Number(this.priceMinInput)
      const max = this.priceMaxInput === '' || this.priceMaxInput == null
        ? null : Number(this.priceMaxInput)
      // 校验
      if (min != null && min < 0) {
        uni.showToast({ title: '最低价不能为负', icon: 'none' })
        return
      }
      if (max != null && max < 0) {
        uni.showToast({ title: '最高价不能为负', icon: 'none' })
        return
      }
      if (min != null && max != null && min > max) {
        uni.showToast({ title: '最低价不能大于最高价', icon: 'none' })
        return
      }
      this.priceMin = min
      this.priceMax = max
      this.filters.price = (min != null || max != null)
      this.showPricePicker = false
      this.searchHotel()
    },

    // 清空价格筛选
    resetPrice() {
      this.priceMin = null
      this.priceMax = null
      this.priceMinInput = ''
      this.priceMaxInput = ''
      this.filters.price = false
      this.showPricePicker = false
      this.searchHotel()
    },

    // 输入时实时同步（可选，用于校验）
    onPriceInput() {
      // 这里不做实时搜索，等用户点"确认"再搜
    },

    // 判断某设施是否已选中
    isFacilitySelected(facility) {
      return this.selectedFacilities.includes(facility)
    },

    // 切换设施的选中状态
    toggleFacility(facility) {
      const idx = this.selectedFacilities.indexOf(facility)
      if (idx >= 0) {
        this.selectedFacilities.splice(idx, 1)  // 已选中 → 取消
      } else {
        this.selectedFacilities.push(facility)  // 未选中 → 选中
      }
      this.filters.facility = this.selectedFacilities.length > 0
      this.searchHotel()
    },

    resetFilters() {
      this.filters.star = false
      this.filters.price = false
      this.filters.facility = false
      this.priceMin = null
      this.priceMax = null
      this.priceMinInput = ''
      this.priceMaxInput = ''
      this.selectedFacilities = []
      this.showPricePicker = false
      this.showFacilityPicker = false
      this.searchHotel()
    },

    searchHotel() {
      this.page = 1
      this.noMore = false
      this.hotels = []
      this.loadHotels()
    },

    // 从设施对象里取前 N 个展示项，过滤掉值为 falsy 的
    getTopFacilities(facilities, limit) {
      if (!facilities || typeof facilities !== 'object') return {}
      const keys = Object.keys(facilities)
        .filter(k => facilities[k])
        .slice(0, limit)
      const result = {}
      keys.forEach(k => { result[k] = facilities[k] })
      return result
    },

    async loadHotels() {
      if (this.isLoading || this.noMore) return
      if (!this.selectedCity) {
        // 首次进入未选城市，提示选择
        uni.showToast({ title: '请先选择城市', icon: 'none' })
        return
      }
      this.isLoading = true

      try {
        const list = await getHotelByCity(
          this.selectedCity,
          this.keyword,
          this.filters.star ? this.minStar : null,
          this.filters.price ? this.priceMin : null,
          this.filters.price ? this.priceMax : null,
          this.selectedFacilities.length > 0 ? this.selectedFacilities : null,
          this.page,
          this.pageSize
        )

        // 字段映射：hotelId → id, facilities 保留后端返回的键值对
        const mapped = (list || []).map(h => ({
          id: h.hotelId,
          name: h.name,
          address: h.address,
          star: h.star,
          startPrice: h.minPrice,    // 用后端查到的最低房型价格
          rating: null,           // 后端无此字段
          facilities: h.facilities || {},
          mainImage: h.mainImage || '/static/images/hotel-default.jpg'
        }))

        if (mapped.length === 0) {
          this.noMore = true
        } else {
          this.hotels = this.hotels.concat(mapped)
          this.page++
          // 后端不分页，列表大小等于 pageSize 时认为还有下一页
          if (mapped.length < this.pageSize) {
            this.noMore = true
          }
        }
      } catch (e) {
        // 静默：request.js 已 toast
      } finally {
        this.isLoading = false
      }
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

    .arrow-down {
      font-size: 18rpx;
      margin-left: 4rpx;
      color: #999;
    }
  }
}

// 设施下拉选择
.facility-dropdown {
  position: fixed;
  top: 180rpx;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 998;
}

.facility-dropdown-content {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  background: #fff;
  padding: 20rpx 0;
  max-height: 600rpx;
  overflow-y: auto;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.1);
}

.facility-option {
  padding: 24rpx 40rpx;
  font-size: 28rpx;
  color: #333;
  border-bottom: 1rpx solid #f0f0f0;

  &:active {
    background: #f5f5f5;
  }

  &.selected {
    color: #007AFF;
    background: rgba(0, 122, 255, 0.08);
  }

  &.facility-reset-option {
    color: #ff4d4f;
    text-align: center;
    border-bottom: none;
    background: #fafafa;
  }
}

// 多选设施项：左侧 checkbox
.facility-multi-option {
  display: flex;
  align-items: center;

  .checkbox {
    width: 36rpx;
    height: 36rpx;
    border: 2rpx solid #ccc;
    border-radius: 50%;
    margin-right: 20rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 28rpx;
    color: #fff;
    background: #fff;
    transition: all 0.2s;
  }

  &.selected .checkbox {
    background: #007AFF;
    border-color: #007AFF;
  }
}

.facility-divider {
  height: 16rpx;
  background: #f5f5f5;
  border-bottom: 1rpx solid #f0f0f0;
}

// 价格范围输入
.price-range-row {
  display: flex;
  align-items: center;
  padding: 30rpx 40rpx;
  gap: 20rpx;
}

.price-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  border: 2rpx solid #e0e0e0;
  border-radius: 12rpx;
  padding: 12rpx 20rpx;

  .price-label {
    font-size: 24rpx;
    color: #999;
    margin-right: 12rpx;
  }

  .price-input {
    flex: 1;
    font-size: 28rpx;
    color: #333;
    text-align: right;
  }

  .price-unit {
    font-size: 24rpx;
    color: #999;
    margin-left: 8rpx;
  }
}

.price-dash {
  font-size: 28rpx;
  color: #999;
}

.price-actions {
  display: flex;
  padding: 0 40rpx 20rpx;
  gap: 20rpx;
}

.price-action-btn {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  border-radius: 12rpx;
  font-size: 28rpx;

  &.price-cancel {
    background: #f5f5f5;
    color: #666;
  }

  &.price-confirm {
    background: #007AFF;
    color: #fff;
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
