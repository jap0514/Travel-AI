"use strict";
const common_vendor = require("../../common/vendor.js");
const _sfc_main = {
  data() {
    return {
      formData: {
        destination: "",
        startDate: "",
        days: 3
      }
    };
  },
  methods: {
    onDateChange(e) {
      this.formData.startDate = e.detail.value;
    },
    onCancel() {
      common_vendor.index.navigateBack();
    },
    onConfirm() {
      if (!this.formData.destination) {
        common_vendor.index.showToast({ title: "请输入目的地", icon: "none" });
        return;
      }
      if (!this.formData.startDate) {
        common_vendor.index.showToast({ title: "请选择出发日期", icon: "none" });
        return;
      }
      common_vendor.index.setStorageSync("travelPlanInfo", this.formData);
      const content = `我想去${this.formData.destination}玩${this.formData.days}天，出发日期是${this.formData.startDate}`;
      const pages = getCurrentPages();
      const prevPage = pages[pages.length - 2];
      if (prevPage) {
        prevPage.travelInfo = this.formData;
        prevPage.inputMessage = content;
      }
      common_vendor.index.navigateBack();
    }
  }
};
function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
  return {
    a: $data.formData.destination,
    b: common_vendor.o(($event) => $data.formData.destination = $event.detail.value),
    c: common_vendor.t($data.formData.startDate || "请选择出发日期"),
    d: !$data.formData.startDate ? 1 : "",
    e: $data.formData.startDate,
    f: common_vendor.o((...args) => $options.onDateChange && $options.onDateChange(...args)),
    g: $data.formData.days === 1 ? 1 : "",
    h: common_vendor.o(($event) => $data.formData.days = 1),
    i: $data.formData.days === 2 ? 1 : "",
    j: common_vendor.o(($event) => $data.formData.days = 2),
    k: $data.formData.days === 3 ? 1 : "",
    l: common_vendor.o(($event) => $data.formData.days = 3),
    m: $data.formData.days === 4 ? 1 : "",
    n: common_vendor.o(($event) => $data.formData.days = 4),
    o: $data.formData.days === 5 ? 1 : "",
    p: common_vendor.o(($event) => $data.formData.days = 5),
    q: common_vendor.o((...args) => $options.onCancel && $options.onCancel(...args)),
    r: common_vendor.o((...args) => $options.onConfirm && $options.onConfirm(...args))
  };
}
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["render", _sfc_render], ["__scopeId", "data-v-41d76192"]]);
wx.createPage(MiniProgramPage);
