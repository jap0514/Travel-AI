"use strict";
const common_vendor = require("../../common/vendor.js");
const common_assets = require("../../common/assets.js");
const _sfc_main = {
  data() {
    return {
      hotelId: null,
      hotel: {},
      roomTypes: [],
      checkInDate: "",
      checkOutDate: "",
      nightCount: 0,
      selectedRoom: null
    };
  },
  computed: {
    totalPrice() {
      if (!this.selectedRoom) return 0;
      return this.selectedRoom.price * this.nightCount;
    }
  },
  onLoad(options) {
    this.hotelId = options.id;
    this.loadHotelDetail();
    this.initDates();
  },
  methods: {
    initDates() {
      const today = /* @__PURE__ */ new Date();
      const tomorrow = new Date(today);
      tomorrow.setDate(tomorrow.getDate() + 1);
      this.checkInDate = this.formatDate(today);
      this.checkOutDate = this.formatDate(tomorrow);
      this.nightCount = 1;
    },
    formatDate(date) {
      return `${date.getMonth() + 1}月${date.getDate()}日`;
    },
    loadHotelDetail() {
      this.hotel = {
        id: this.hotelId,
        name: "北京饭店",
        address: "北京市东城区东长安街33号",
        star: 5,
        rating: "4.8",
        commentCount: 1234,
        facilities: ["免费WiFi", "停车场", "健身房", "游泳池", "SPA", "餐厅", "酒吧", "商务中心"],
        images: [
          "/static/images/hotel1.jpg",
          "/static/images/hotel2.jpg",
          "/static/images/hotel3.jpg"
        ]
      };
      this.roomTypes = [
        {
          id: 1,
          name: "标准间",
          bedType: "大床1.8m/双床1.2m",
          area: 35,
          capacity: 2,
          price: 588,
          amenities: ["免费WiFi", "早餐", "独立卫浴"],
          image: "/static/images/room1.jpg"
        },
        {
          id: 2,
          name: "豪华间",
          bedType: "大床2.0m",
          area: 45,
          capacity: 2,
          price: 888,
          amenities: ["免费WiFi", "早餐", "海景", "迷你吧"],
          image: "/static/images/room2.jpg"
        },
        {
          id: 3,
          name: "套房",
          bedType: "大床2.2m",
          area: 70,
          capacity: 3,
          price: 1588,
          amenities: ["免费WiFi", "早餐", "客厅", "按摩浴缸"],
          image: "/static/images/room3.jpg"
        }
      ];
    },
    selectRoom(room) {
      this.selectedRoom = room;
    },
    goBookRoom(room) {
      this.selectedRoom = room;
      this.goBook();
    },
    goBook() {
      if (!this.selectedRoom) {
        common_vendor.index.showToast({ title: "请选择房型", icon: "none" });
        return;
      }
      const token = common_vendor.index.getStorageSync("token");
      if (!token) {
        common_vendor.index.navigateTo({ url: "/pages/login/login" });
        return;
      }
      common_vendor.index.navigateTo({
        url: `/pages/order/confirm?hotelId=${this.hotelId}&roomId=${this.selectedRoom.id}&checkIn=${this.checkInDate}&checkOut=${this.checkOutDate}&price=${this.totalPrice}`
      });
    }
  }
};
function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
  return {
    a: common_vendor.f($data.hotel.images, (img, index, i0) => {
      return {
        a: img,
        b: index
      };
    }),
    b: common_vendor.t($data.hotel.name),
    c: common_vendor.f($data.hotel.star, (n, k0, i0) => {
      return {
        a: n
      };
    }),
    d: common_assets._imports_0$3,
    e: common_vendor.t($data.hotel.address),
    f: common_vendor.t($data.hotel.rating || "5.0"),
    g: common_vendor.t($data.hotel.commentCount || 0),
    h: common_vendor.f($data.hotel.facilities, (facility, index, i0) => {
      return {
        a: common_vendor.t(facility),
        b: index
      };
    }),
    i: common_assets._imports_1$3,
    j: common_vendor.t($data.checkInDate || "选择日期"),
    k: common_vendor.t($data.checkOutDate || "选择日期"),
    l: common_vendor.t($data.nightCount),
    m: common_vendor.f($data.roomTypes, (room, k0, i0) => {
      return {
        a: room.image || "/static/images/room-default.jpg",
        b: common_vendor.t(room.name),
        c: common_vendor.t(room.bedType),
        d: common_vendor.t(room.area),
        e: common_vendor.t(room.capacity),
        f: common_vendor.f(room.amenities.slice(0, 2), (item, index, i1) => {
          return {
            a: common_vendor.t(item),
            b: index
          };
        }),
        g: common_vendor.t(room.price),
        h: common_vendor.o(($event) => $options.goBookRoom(room), room.id),
        i: room.id,
        j: common_vendor.o(($event) => $options.selectRoom(room), room.id)
      };
    }),
    n: common_vendor.t($options.totalPrice),
    o: common_vendor.t($data.nightCount),
    p: common_vendor.o((...args) => $options.goBook && $options.goBook(...args))
  };
}
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["render", _sfc_render], ["__scopeId", "data-v-bd64164f"]]);
wx.createPage(MiniProgramPage);
