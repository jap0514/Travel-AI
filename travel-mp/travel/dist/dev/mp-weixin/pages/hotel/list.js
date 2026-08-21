"use strict";
const common_vendor = require("../../common/vendor.js");
const api_request = require("../../api/request.js");
const common_assets = require("../../common/assets.js");
const _sfc_main = {
  data() {
    return {
      selectedCity: "",
      keyword: "",
      showCity: false,
      showFacilityPicker: false,
      showPricePicker: false,
      isLoading: false,
      noMore: false,
      page: 1,
      pageSize: 10,
      cities: ["北京", "上海", "杭州", "成都", "三亚", "广州", "深圳", "重庆", "西安", "厦门"],
      hotels: [],
      filters: {
        star: false,
        price: false,
        facility: false
      },
      // 筛选参数
      minStar: 4,
      // 星级筛选的阈值
      priceMin: null,
      // 价格范围：最低
      priceMax: null,
      // 价格范围：最高
      priceMinInput: "",
      // 输入框：最低价（字符串）
      priceMaxInput: "",
      // 输入框：最高价（字符串）
      selectedFacilities: [],
      // 用户选中的设施列表（多选）
      // 常用设施下拉选项
      availableFacilities: ["WiFi", "游泳池", "健身房", "停车场", "餐厅", "SPA", "空调", "24小时前台", "行李寄存", "会议室"]
    };
  },
  computed: {
    hasAnyFilter() {
      return this.filters.star || this.filters.price || this.filters.facility;
    },
    priceButtonText() {
      if (this.priceMin != null && this.priceMax != null) {
        return `¥${this.priceMin}-${this.priceMax}`;
      }
      if (this.priceMin != null) {
        return `≥¥${this.priceMin}`;
      }
      if (this.priceMax != null) {
        return `≤¥${this.priceMax}`;
      }
      return "价格";
    },
    facilityButtonText() {
      const n = this.selectedFacilities.length;
      if (n === 0) return "设施";
      if (n === 1) return `含${this.selectedFacilities[0]}`;
      if (n <= 3) return `含${this.selectedFacilities.join("、")}`;
      return `含${this.selectedFacilities.slice(0, 2).join("、")}等${n}项`;
    }
  },
  onLoad() {
    this.loadHotels();
  },
  onShow() {
    const city = common_vendor.index.getStorageSync("selectedCity");
    if (city) {
      this.selectedCity = city;
      this.searchHotel();
    }
  },
  methods: {
    showCityPicker() {
      this.showCity = true;
    },
    selectCity(city) {
      this.selectedCity = city;
      this.showCity = false;
      common_vendor.index.setStorageSync("selectedCity", city);
      this.searchHotel();
    },
    toggleFilter(type) {
      if (type === "price" || type === "facility") return;
      this.filters[type] = !this.filters[type];
      this.searchHotel();
    },
    // 应用价格范围
    applyPriceRange() {
      const min = this.priceMinInput === "" || this.priceMinInput == null ? null : Number(this.priceMinInput);
      const max = this.priceMaxInput === "" || this.priceMaxInput == null ? null : Number(this.priceMaxInput);
      if (min != null && min < 0) {
        common_vendor.index.showToast({ title: "最低价不能为负", icon: "none" });
        return;
      }
      if (max != null && max < 0) {
        common_vendor.index.showToast({ title: "最高价不能为负", icon: "none" });
        return;
      }
      if (min != null && max != null && min > max) {
        common_vendor.index.showToast({ title: "最低价不能大于最高价", icon: "none" });
        return;
      }
      this.priceMin = min;
      this.priceMax = max;
      this.filters.price = min != null || max != null;
      this.showPricePicker = false;
      this.searchHotel();
    },
    // 清空价格筛选
    resetPrice() {
      this.priceMin = null;
      this.priceMax = null;
      this.priceMinInput = "";
      this.priceMaxInput = "";
      this.filters.price = false;
      this.showPricePicker = false;
      this.searchHotel();
    },
    // 输入时实时同步（可选，用于校验）
    onPriceInput() {
    },
    // 判断某设施是否已选中
    isFacilitySelected(facility) {
      return this.selectedFacilities.includes(facility);
    },
    // 切换设施的选中状态
    toggleFacility(facility) {
      const idx = this.selectedFacilities.indexOf(facility);
      if (idx >= 0) {
        this.selectedFacilities.splice(idx, 1);
      } else {
        this.selectedFacilities.push(facility);
      }
      this.filters.facility = this.selectedFacilities.length > 0;
      this.searchHotel();
    },
    resetFilters() {
      this.filters.star = false;
      this.filters.price = false;
      this.filters.facility = false;
      this.priceMin = null;
      this.priceMax = null;
      this.priceMinInput = "";
      this.priceMaxInput = "";
      this.selectedFacilities = [];
      this.showPricePicker = false;
      this.showFacilityPicker = false;
      this.searchHotel();
    },
    searchHotel() {
      this.page = 1;
      this.noMore = false;
      this.hotels = [];
      this.loadHotels();
    },
    // 从设施对象里取前 N 个展示项，过滤掉值为 falsy 的
    getTopFacilities(facilities, limit) {
      if (!facilities || typeof facilities !== "object") return {};
      const keys = Object.keys(facilities).filter((k) => facilities[k]).slice(0, limit);
      const result = {};
      keys.forEach((k) => {
        result[k] = facilities[k];
      });
      return result;
    },
    async loadHotels() {
      if (this.isLoading || this.noMore) return;
      if (!this.selectedCity) {
        common_vendor.index.showToast({ title: "请先选择城市", icon: "none" });
        return;
      }
      this.isLoading = true;
      try {
        const list = await api_request.getHotelByCity(
          this.selectedCity,
          this.keyword,
          this.filters.star ? this.minStar : null,
          this.filters.price ? this.priceMin : null,
          this.filters.price ? this.priceMax : null,
          this.selectedFacilities.length > 0 ? this.selectedFacilities : null,
          this.page,
          this.pageSize
        );
        const mapped = (list || []).map((h) => ({
          id: h.hotelId,
          name: h.name,
          address: h.address,
          star: h.star,
          startPrice: h.minPrice,
          // 用后端查到的最低房型价格
          rating: null,
          // 后端无此字段
          facilities: h.facilities || {},
          mainImage: h.mainImage || "/static/images/hotel-default.jpg"
        }));
        if (mapped.length === 0) {
          this.noMore = true;
        } else {
          this.hotels = this.hotels.concat(mapped);
          this.page++;
          if (mapped.length < this.pageSize) {
            this.noMore = true;
          }
        }
      } catch (e) {
      } finally {
        this.isLoading = false;
      }
    },
    loadMore() {
      this.loadHotels();
    },
    goDetail(hotel) {
      common_vendor.index.navigateTo({
        url: `/pages/hotel/detail?id=${hotel.id}`
      });
    }
  }
};
function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
  return common_vendor.e({
    a: common_vendor.t($data.selectedCity || "选择城市"),
    b: common_vendor.o((...args) => $options.showCityPicker && $options.showCityPicker(...args)),
    c: common_vendor.o((...args) => $options.searchHotel && $options.searchHotel(...args)),
    d: $data.keyword,
    e: common_vendor.o(($event) => $data.keyword = $event.detail.value),
    f: common_assets._imports_0$2,
    g: common_vendor.t($data.filters.star ? `≥${$data.minStar}星` : "星级"),
    h: $data.filters.star ? 1 : "",
    i: common_vendor.o(($event) => $options.toggleFilter("star")),
    j: common_vendor.t($options.priceButtonText),
    k: $data.filters.price ? 1 : "",
    l: common_vendor.o(($event) => {
      $data.showPricePicker = !$data.showPricePicker;
      $data.showFacilityPicker = false;
    }),
    m: common_vendor.t($options.facilityButtonText),
    n: $data.filters.facility ? 1 : "",
    o: common_vendor.o(($event) => {
      $data.showFacilityPicker = !$data.showFacilityPicker;
      $data.showPricePicker = false;
    }),
    p: $data.showPricePicker
  }, $data.showPricePicker ? {
    q: common_vendor.o([common_vendor.m(($event) => $data.priceMinInput = $event.detail.value, {
      number: true
    }), (...args) => $options.onPriceInput && $options.onPriceInput(...args)]),
    r: $data.priceMinInput,
    s: common_vendor.o([common_vendor.m(($event) => $data.priceMaxInput = $event.detail.value, {
      number: true
    }), (...args) => $options.onPriceInput && $options.onPriceInput(...args)]),
    t: $data.priceMaxInput,
    v: common_vendor.o((...args) => $options.resetPrice && $options.resetPrice(...args)),
    w: common_vendor.o((...args) => $options.applyPriceRange && $options.applyPriceRange(...args)),
    x: common_vendor.o((...args) => $options.resetFilters && $options.resetFilters(...args)),
    y: common_vendor.o(() => {
    }),
    z: common_vendor.o(($event) => $data.showPricePicker = false)
  } : {}, {
    A: $data.showFacilityPicker
  }, $data.showFacilityPicker ? {
    B: common_vendor.f($data.availableFacilities, (f, k0, i0) => {
      return common_vendor.e({
        a: $options.isFacilitySelected(f)
      }, $options.isFacilitySelected(f) ? {} : {}, {
        b: common_vendor.t(f),
        c: f,
        d: $options.isFacilitySelected(f) ? 1 : "",
        e: common_vendor.o(($event) => $options.toggleFacility(f), f)
      });
    }),
    C: common_vendor.o((...args) => $options.resetFilters && $options.resetFilters(...args)),
    D: common_vendor.o(() => {
    }),
    E: common_vendor.o(($event) => $data.showFacilityPicker = false)
  } : {}, {
    F: $data.hotels.length === 0
  }, $data.hotels.length === 0 ? {
    G: common_assets._imports_1$2
  } : {}, {
    H: common_vendor.f($data.hotels, (hotel, k0, i0) => {
      return {
        a: hotel.mainImage || "/static/images/hotel-default.jpg",
        b: common_vendor.t(hotel.name),
        c: common_vendor.f(hotel.star, (n, k1, i1) => {
          return {
            a: n
          };
        }),
        d: common_vendor.t(hotel.star),
        e: common_vendor.t(hotel.address),
        f: common_vendor.f($options.getTopFacilities(hotel.facilities, 3), (value, key, i1) => {
          return common_vendor.e({
            a: common_vendor.t(key),
            b: Array.isArray(value)
          }, Array.isArray(value) ? {
            c: common_vendor.t(value.join("、"))
          } : {}, {
            d: key
          });
        }),
        g: common_vendor.t(hotel.startPrice),
        h: common_vendor.t(hotel.rating || "5.0"),
        i: hotel.id,
        j: common_vendor.o(($event) => $options.goDetail(hotel), hotel.id)
      };
    }),
    I: $data.isLoading
  }, $data.isLoading ? {} : {}, {
    J: $data.noMore
  }, $data.noMore ? {} : {}, {
    K: common_vendor.o((...args) => $options.loadMore && $options.loadMore(...args)),
    L: $data.showCity
  }, $data.showCity ? {
    M: common_vendor.o(($event) => $data.showCity = false),
    N: common_vendor.f($data.cities, (city, k0, i0) => {
      return {
        a: common_vendor.t(city),
        b: city,
        c: city === $data.selectedCity ? 1 : "",
        d: common_vendor.o(($event) => $options.selectCity(city), city)
      };
    }),
    O: common_vendor.o(() => {
    }),
    P: common_vendor.o(($event) => $data.showCity = false)
  } : {});
}
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["render", _sfc_render], ["__scopeId", "data-v-1104f636"]]);
wx.createPage(MiniProgramPage);
