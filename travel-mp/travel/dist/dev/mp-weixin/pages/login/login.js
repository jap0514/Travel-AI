"use strict";
const common_vendor = require("../../common/vendor.js");
const api_request = require("../../api/request.js");
const common_assets = require("../../common/assets.js");
const _sfc_main = {
  data() {
    return {
      loading: false
    };
  },
  methods: {
    /**
     * 微信授权登录（标准流程）
     */
    async onWechatLogin() {
      if (this.loading) return;
      common_vendor.index.showLoading({ title: "正在登录..." });
      try {
        const loginRes = await common_vendor.index.login({ provider: "weixin" });
        console.log("微信 login result:", loginRes);
        if (!loginRes.code) {
          common_vendor.index.hideLoading();
          common_vendor.index.showToast({ title: "获取登录凭证失败", icon: "none" });
          return;
        }
        await this.doLogin(loginRes.code);
      } catch (err) {
        console.error("微信登录失败:", err);
        common_vendor.index.hideLoading();
        common_vendor.index.showToast({ title: "登录失败", icon: "none" });
      }
    },
    /**
     * 调试模式模拟登录
     */
    async onDebugLogin() {
      if (this.loading) return;
      common_vendor.index.showModal({
        title: "提示",
        content: "调试模式将使用模拟code登录，是否继续？",
        success: async (res) => {
          if (res.confirm) {
            await this.doLogin("debug_test_code_12345");
          }
        }
      });
    },
    /**
     * 执行登录
     */
    async doLogin(code) {
      if (this.loading) return;
      this.loading = true;
      try {
        const data = await api_request.login(code);
        console.log("登录成功:", data);
        common_vendor.index.setStorageSync("token", data.token);
        common_vendor.index.setStorageSync("userInfo", {
          id: data.userId,
          nickname: data.nickname,
          avatar: data.avatar
        });
        common_vendor.index.hideLoading();
        common_vendor.index.showToast({ title: "登录成功", icon: "success" });
        setTimeout(() => {
          common_vendor.index.navigateBack();
        }, 1500);
      } catch (err) {
        console.error("登录失败:", err);
        common_vendor.index.hideLoading();
      } finally {
        this.loading = false;
      }
    }
  }
};
function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
  return {
    a: common_assets._imports_0$1,
    b: common_assets._imports_1$1,
    c: common_vendor.o((...args) => $options.onWechatLogin && $options.onWechatLogin(...args)),
    d: common_vendor.o((...args) => $options.onDebugLogin && $options.onDebugLogin(...args))
  };
}
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["render", _sfc_render], ["__scopeId", "data-v-cdfe2409"]]);
wx.createPage(MiniProgramPage);
