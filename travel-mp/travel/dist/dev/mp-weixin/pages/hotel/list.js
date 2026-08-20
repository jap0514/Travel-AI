"use strict";
const common_vendor = require("../../common/vendor.js");
const common_assets = require("../../common/assets.js");
const _sfc_main = {
  data() {
    return {
      selectedCity: "",
      keyword: "",
      showCity: false,
      isLoading: false,
      noMore: false,
      page: 1,
      pageSize: 10,
      cities: ["北京", "上海", "杭州", "成都", "三亚", "广州", "深圳", "重庆", "西安", "厦门"],
      hotels: [],
      filters: {
        star: false,
        price: false,
        type: false,
        facility: false
      }
    };
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
      this.filters[type] = !this.filters[type];
    },
    searchHotel() {
      this.page = 1;
      this.noMore = false;
      this.hotels = [];
      this.loadHotels();
    },
    loadHotels() {
      if (this.isLoading || this.noMore) return;
      this.isLoading = true;
      setTimeout(() => {
        const mockHotels = [
          {
            id: 1,
            name: "北京饭店",
            address: "北京市东城区东长安街33号",
            star: 5,
            startPrice: 888,
            rating: "4.8",
            facilities: ["免费WiFi", "停车场", "健身房", "游泳池"],
            mainImage: "/static/images/hotel1.jpg"
          },
          {
            id: 2,
            name: "上海外滩酒店",
            address: "上海市黄浦区外滩18号",
            star: 5,
            startPrice: 1288,
            rating: "4.9",
            facilities: ["海景房", "SPA", "餐厅", "酒吧"],
            mainImage: "/static/images/hotel2.jpg"
          },
          {
            id: 3,
            name: "杭州西湖酒店",
            address: "杭州市西湖区西湖大道",
            star: 4,
            startPrice: 588,
            rating: "4.6",
            facilities: ["免费WiFi", "停车场", "会议室"],
            mainImage: "/static/images/hotel3.jpg"
          }
        ];
        if (this.page >= 3) {
          this.noMore = true;
        } else {
          this.hotels = this.hotels.concat(mockHotels);
          this.page++;
        }
        this.isLoading = false;
      }, 1e3);
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
    g: $data.filters.star ? 1 : "",
    h: common_vendor.o(($event) => $options.toggleFilter("star")),
    i: $data.filters.price ? 1 : "",
    j: common_vendor.o(($event) => $options.toggleFilter("price")),
    k: $data.filters.type ? 1 : "",
    l: common_vendor.o(($event) => $options.toggleFilter("type")),
    m: $data.filters.facility ? 1 : "",
    n: common_vendor.o(($event) => $options.toggleFilter("facility")),
    o: $data.hotels.length === 0
  }, $data.hotels.length === 0 ? {
    p: common_assets._imports_1$2
  } : {}, {
    q: common_vendor.f($data.hotels, (hotel, k0, i0) => {
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
        f: common_vendor.f(hotel.facilities.slice(0, 3), (facility, index, i1) => {
          return {
            a: common_vendor.t(facility),
            b: index
          };
        }),
        g: common_vendor.t(hotel.startPrice),
        h: common_vendor.t(hotel.rating || "5.0"),
        i: hotel.id,
        j: common_vendor.o(($event) => $options.goDetail(hotel), hotel.id)
      };
    }),
    r: $data.isLoading
  }, $data.isLoading ? {} : {}, {
    s: $data.noMore
  }, $data.noMore ? {} : {}, {
    t: common_vendor.o((...args) => $options.loadMore && $options.loadMore(...args)),
    v: $data.showCity
  }, $data.showCity ? {
    w: common_vendor.o(($event) => $data.showCity = false),
    x: common_vendor.f($data.cities, (city, k0, i0) => {
      return {
        a: common_vendor.t(city),
        b: city,
        c: city === $data.selectedCity ? 1 : "",
        d: common_vendor.o(($event) => $options.selectCity(city), city)
      };
    }),
    y: common_vendor.o(() => {
    }),
    z: common_vendor.o(($event) => $data.showCity = false)
  } : {});
}
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["render", _sfc_render], ["__scopeId", "data-v-1104f636"]]);
wx.createPage(MiniProgramPage);
