"use strict";
const common_vendor = require("../../common/vendor.js");
const api_request = require("../../api/request.js");
const common_assets = require("../../common/assets.js");
const _sfc_main = {
  data() {
    return {
      isLogin: false,
      userInfo: {},
      banners: [
        { image: "/static/images/banner1.jpg" },
        { image: "/static/images/banner2.jpg" },
        { image: "/static/images/banner3.jpg" }
      ],
      hotDestinations: [
        { id: 1, name: "北京", image: "/static/images/dest-beijing.jpg", count: 1234 },
        { id: 2, name: "上海", image: "/static/images/dest-shanghai.jpg", count: 987 },
        { id: 3, name: "杭州", image: "/static/images/dest-hangzhou.jpg", count: 856 },
        { id: 4, name: "成都", image: "/static/images/dest-chengdu.jpg", count: 765 },
        { id: 5, name: "三亚", image: "/static/images/dest-sanya.jpg", count: 654 }
      ]
    };
  },
  onLoad() {
    this.checkLogin();
  },
  onShow() {
    this.checkLogin();
    this.gethotDestinations();
  },
  methods: {
    checkLogin() {
      const token = common_vendor.index.getStorageSync("token");
      const userInfo = common_vendor.index.getStorageSync("userInfo");
      if (token && userInfo) {
        this.isLogin = true;
        this.userInfo = userInfo;
      }
    },
    goLogin() {
      if (!this.isLogin) {
        common_vendor.index.navigateTo({ url: "/pages/login/login" });
      }
    },
    goChat() {
      if (!this.isLogin) {
        common_vendor.index.navigateTo({ url: "/pages/login/login" });
        return;
      }
      common_vendor.index.navigateTo({ url: "/pages/chat/chat" });
    },
    goHotel() {
      common_vendor.index.switchTab({ url: "/pages/hotel/list" });
    },
    goOrder() {
      if (!this.isLogin) {
        common_vendor.index.navigateTo({ url: "/pages/login/login" });
        return;
      }
      common_vendor.index.switchTab({ url: "/pages/order/list" });
    },
    goMy() {
      common_vendor.index.switchTab({ url: "/pages/my/my" });
    },
    async gethotDestinations() {
      try {
        const data = await api_request.getPopularDestinations(30);
        console.log("热门地点: ", data);
        if (data && data.length > 0) {
          this.hotDestinations = data.map((item) => ({
            id: item.id || Math.random(),
            name: item.destination || item.name,
            image: "/static/images/dest-default.jpg",
            count: item.count || 0
          }));
        }
      } catch (error) {
        console.error("获取热门目的地失败", error);
      }
    }
  }
};
function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
  return {
    a: common_assets._imports_0,
    b: common_vendor.t($data.isLogin ? $data.userInfo.nickname : "点击登录"),
    c: common_vendor.o((...args) => $options.goLogin && $options.goLogin(...args)),
    d: common_vendor.f($data.banners, (item, index, i0) => {
      return {
        a: item.image,
        b: index
      };
    }),
    e: common_vendor.o((...args) => $options.goHotel && $options.goHotel(...args)),
    f: common_vendor.f($data.hotDestinations, (item, k0, i0) => {
      return {
        a: item.image,
        b: common_vendor.t(item.name),
        c: common_vendor.t(item.count),
        d: item.id
      };
    }),
    g: common_assets._imports_1,
    h: common_vendor.o((...args) => $options.goChat && $options.goChat(...args)),
    i: common_assets._imports_2,
    j: common_vendor.o((...args) => $options.goHotel && $options.goHotel(...args)),
    k: common_assets._imports_3,
    l: common_vendor.o((...args) => $options.goOrder && $options.goOrder(...args)),
    m: common_assets._imports_4,
    n: common_vendor.o((...args) => $options.goMy && $options.goMy(...args)),
    o: common_vendor.o((...args) => $options.goChat && $options.goChat(...args))
  };
}
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["render", _sfc_render], ["__scopeId", "data-v-83a5a03c"]]);
wx.createPage(MiniProgramPage);
