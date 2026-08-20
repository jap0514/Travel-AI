"use strict";
const common_vendor = require("../../common/vendor.js");
const api_request = require("../../api/request.js");
const _sfc_main = {
  data() {
    return {
      isLogin: false,
      userInfo: {},
      banners: [
        { image: "" },
        { image: "" },
        { image: "" }
      ],
      hotDestinations: [
        { id: 1, name: "北京", image: "", count: 1234 },
        { id: 2, name: "上海", image: "", count: 987 },
        { id: 3, name: "杭州", image: "", count: 856 },
        { id: 4, name: "成都", image: "", count: 765 },
        { id: 5, name: "三亚", image: "", count: 654 }
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
            image: "",
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
    a: common_vendor.t($data.isLogin ? $data.userInfo.nickname : "点击登录"),
    b: common_vendor.o((...args) => $options.goLogin && $options.goLogin(...args)),
    c: common_vendor.f($data.banners, (item, index, i0) => {
      return {
        a: item.image,
        b: index
      };
    }),
    d: common_vendor.o((...args) => $options.goHotel && $options.goHotel(...args)),
    e: common_vendor.f($data.hotDestinations, (item, k0, i0) => {
      return {
        a: item.image,
        b: common_vendor.t(item.name),
        c: common_vendor.t(item.count),
        d: item.id
      };
    }),
    f: common_vendor.o((...args) => $options.goChat && $options.goChat(...args)),
    g: common_vendor.o((...args) => $options.goHotel && $options.goHotel(...args)),
    h: common_vendor.o((...args) => $options.goOrder && $options.goOrder(...args)),
    i: common_vendor.o((...args) => $options.goMy && $options.goMy(...args)),
    j: common_vendor.o((...args) => $options.goChat && $options.goChat(...args))
  };
}
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["render", _sfc_render], ["__scopeId", "data-v-83a5a03c"]]);
wx.createPage(MiniProgramPage);
