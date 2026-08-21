"use strict";
const common_vendor = require("../../common/vendor.js");
const api_request = require("../../api/request.js");
const common_assets = require("../../common/assets.js");
const _sfc_main = {
  data() {
    return {
      isLogin: false,
      userInfo: {},
      stats: {
        destinationCount: 0,
        orderCount: 0,
        sessionCount: 0
      }
    };
  },
  onLoad() {
    this.checkLogin();
  },
  onShow() {
    console.log("my.vue onShow 被调用了");
    this.checkLogin();
  },
  methods: {
    checkLogin() {
      const token = common_vendor.index.getStorageSync("token");
      const userInfo = common_vendor.index.getStorageSync("userInfo");
      if (token && userInfo) {
        this.isLogin = true;
        this.userInfo = userInfo;
        this.loadStats();
      } else {
        this.isLogin = false;
        this.userInfo = {};
        this.stats = { destinationCount: 0, orderCount: 0, sessionCount: 0 };
      }
    },
    async loadStats() {
      const userId = this.userInfo.id;
      console.log(userId);
      const sessions = await api_request.getUserSessions(userId, 1, 10);
      const destinations = await api_request.getUserDestinations(userId);
      const orders = await api_request.getOrderList(userId, 1, 10);
      this.stats = {
        destinationCount: destinations.length || 0,
        orderCount: orders.length || 0,
        sessionCount: sessions.length || 0
      };
    },
    goLogin() {
      common_vendor.index.navigateTo({ url: "/pages/login/login" });
    },
    goSettings() {
      common_vendor.index.showToast({ title: "设置功能开发中", icon: "none" });
    },
    goMyDestinations() {
      if (!this.isLogin) {
        common_vendor.index.navigateTo({ url: "/pages/login/login" });
        return;
      }
      common_vendor.index.showToast({ title: "我的目的地", icon: "none" });
    },
    goOrder() {
      if (!this.isLogin) {
        common_vendor.index.navigateTo({ url: "/pages/login/login" });
        return;
      }
      common_vendor.index.switchTab({ url: "/pages/order/list" });
    },
    goChatHistory() {
      if (!this.isLogin) {
        common_vendor.index.navigateTo({ url: "/pages/login/login" });
        return;
      }
      common_vendor.index.navigateTo({ url: "/pages/chat/chat?tab=history" });
    },
    goAbout() {
      common_vendor.index.showToast({ title: "关于我们", icon: "none" });
    },
    contactService() {
      common_vendor.index.makePhoneCall({
        phoneNumber: "400-123-4567"
      });
    },
    goHelp() {
      common_vendor.index.showToast({ title: "帮助与反馈", icon: "none" });
    },
    async logout() {
      const res = await common_vendor.index.showModal({
        title: "提示",
        content: "确定要退出登录吗？"
      });
      if (!res.confirm) return;
      try {
        await api_request.logout();
      } catch (e) {
      } finally {
        common_vendor.index.removeStorageSync("token");
        common_vendor.index.removeStorageSync("userInfo");
        common_vendor.index.reLaunch({ url: "/pages/login/login" });
      }
    }
  }
};
function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
  return common_vendor.e({
    a: $data.userInfo.avatar || "/static/images/avatar-default.png",
    b: common_vendor.t($data.userInfo.nickname || "未登录"),
    c: $data.userInfo.id
  }, $data.userInfo.id ? {
    d: common_vendor.t($data.userInfo.id)
  } : {}, {
    e: !$data.isLogin
  }, !$data.isLogin ? {
    f: common_vendor.o((...args) => $options.goLogin && $options.goLogin(...args))
  } : {
    g: common_assets._imports_0$5,
    h: common_vendor.o((...args) => $options.goSettings && $options.goSettings(...args))
  }, {
    i: common_vendor.t($data.stats.destinationCount),
    j: common_vendor.o((...args) => $options.goMyDestinations && $options.goMyDestinations(...args)),
    k: common_vendor.t($data.stats.orderCount),
    l: common_vendor.o((...args) => $options.goOrder && $options.goOrder(...args)),
    m: common_vendor.t($data.stats.sessionCount),
    n: common_assets._imports_1$4,
    o: common_vendor.o((...args) => $options.goMyDestinations && $options.goMyDestinations(...args)),
    p: common_assets._imports_2$1,
    q: common_vendor.o((...args) => $options.goOrder && $options.goOrder(...args)),
    r: common_assets._imports_3,
    s: common_vendor.o((...args) => $options.goChatHistory && $options.goChatHistory(...args)),
    t: common_assets._imports_4,
    v: common_vendor.o((...args) => $options.goAbout && $options.goAbout(...args)),
    w: common_assets._imports_5,
    x: common_vendor.o((...args) => $options.contactService && $options.contactService(...args)),
    y: common_assets._imports_6,
    z: common_vendor.o((...args) => $options.goHelp && $options.goHelp(...args)),
    A: $data.isLogin
  }, $data.isLogin ? {
    B: common_vendor.o((...args) => $options.logout && $options.logout(...args))
  } : {});
}
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["render", _sfc_render], ["__scopeId", "data-v-d3687551"]]);
wx.createPage(MiniProgramPage);
