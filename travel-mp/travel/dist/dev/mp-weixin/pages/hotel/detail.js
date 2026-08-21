"use strict";
const common_vendor = require("../../common/vendor.js");
const api_request = require("../../api/request.js");
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
      selectedRoom: null,
      loading: false
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
    this.initDates();
    this.loadHotelDetail();
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
    // 过滤设施：只保留值为 truthy 的项，可选限制数量
    filterFacilities(facilities, limit) {
      if (!facilities || typeof facilities !== "object") return {};
      const result = {};
      const keys = Object.keys(facilities).filter((k) => facilities[k]);
      const limited = typeof limit === "number" ? keys.slice(0, limit) : keys;
      limited.forEach((k) => {
        result[k] = facilities[k];
      });
      return result;
    },
    async loadHotelDetail() {
      if (!this.hotelId) return;
      this.loading = true;
      try {
        const [hotelVO, roomTypeList] = await Promise.all([
          api_request.getHotelById(this.hotelId),
          api_request.getHotelRoomType(this.hotelId)
        ]);
        this.hotel = {
          id: hotelVO.hotelId,
          name: hotelVO.name,
          address: hotelVO.address,
          star: hotelVO.star,
          rating: "5.0",
          // 后端无此字段，给默认值
          commentCount: 0,
          // 后端无此字段
          facilities: this.filterFacilities(hotelVO.facilities),
          images: hotelVO.mainImage ? [hotelVO.mainImage] : ["/static/images/hotel-default.jpg"],
          description: hotelVO.description
        };
        this.roomTypes = roomTypeList.map((rt) => ({
          id: rt.roomTypeId,
          hotelId: rt.hotelId,
          hotelName: rt.hotelName,
          name: rt.name,
          price: Number(rt.price),
          capacity: rt.capacity,
          bedType: rt.bedType,
          area: parseFloat(rt.area) || 0,
          amenities: this.filterFacilities(rt.amenities),
          image: "/static/images/room-default.jpg"
          // 后端无此字段
        }));
      } catch (e) {
      } finally {
        this.loading = false;
      }
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
    h: common_vendor.f($data.hotel.facilities, (value, key, i0) => {
      return common_vendor.e({
        a: common_vendor.t(key),
        b: Array.isArray(value)
      }, Array.isArray(value) ? {
        c: common_vendor.t(value.join("、"))
      } : {}, {
        d: key
      });
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
        f: common_vendor.f($options.filterFacilities(room.amenities, 2), (value, key, i1) => {
          return {
            a: common_vendor.t(key),
            b: key
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
