"use strict";
const common_vendor = require("../../common/vendor.js");
const _sfc_main = {
  data() {
    return {
      orderNo: "",
      order: {}
    };
  },
  onLoad(options) {
    this.orderNo = options.orderNo;
    this.loadOrderDetail();
  },
  methods: {
    loadOrderDetail() {
      this.order = {
        orderNo: this.orderNo,
        hotelName: "北京饭店",
        roomName: "豪华间",
        roomImage: "/static/images/room2.jpg",
        checkInDate: "1月15日",
        checkOutDate: "1月17日",
        nightCount: 2,
        roomPrice: 888,
        totalPrice: 1776,
        status: 0,
        createTime: "2024-01-10 14:30:00",
        guestName: "张三",
        guestPhone: "138****8888"
      };
    },
    getStatusClass(status) {
      const classMap = {
        "0": "pending",
        "1": "paid",
        "2": "confirmed",
        "3": "cancelled",
        "4": "completed"
      };
      return classMap[status] || "";
    },
    getStatusIcon(status) {
      const iconMap = {
        "0": "⏰",
        "1": "✓",
        "2": "✓",
        "3": "×",
        "4": "★"
      };
      return iconMap[status] || "";
    },
    getStatusText(status) {
      const textMap = {
        "0": "待支付",
        "1": "已支付",
        "2": "已确认",
        "3": "已取消",
        "4": "已完成"
      };
      return textMap[status] || "";
    },
    cancelOrder() {
      common_vendor.index.showModal({
        title: "提示",
        content: "确定要取消该订单吗？",
        success: (res) => {
          if (res.confirm) {
            common_vendor.index.showToast({ title: "取消成功", icon: "success" });
            setTimeout(() => {
              common_vendor.index.navigateBack();
            }, 1500);
          }
        }
      });
    },
    payOrder() {
      common_vendor.index.showToast({ title: "支付功能开发中", icon: "none" });
    },
    reBook() {
      common_vendor.index.navigateBack();
    },
    contactService() {
      common_vendor.index.makePhoneCall({
        phoneNumber: "400-123-4567"
      });
    }
  }
};
function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
  return common_vendor.e({
    a: common_vendor.t($options.getStatusIcon($data.order.status)),
    b: common_vendor.t($options.getStatusText($data.order.status)),
    c: common_vendor.n($options.getStatusClass($data.order.status)),
    d: $data.order.roomImage || "/static/images/room-default.jpg",
    e: common_vendor.t($data.order.hotelName),
    f: common_vendor.t($data.order.roomName),
    g: common_vendor.t($data.order.checkInDate),
    h: common_vendor.t($data.order.checkOutDate),
    i: common_vendor.t($data.order.orderNo),
    j: common_vendor.t($data.order.createTime),
    k: common_vendor.t($data.order.nightCount),
    l: common_vendor.t($data.order.guestPhone || "138****8888"),
    m: common_vendor.t($data.order.guestName || "张三"),
    n: common_vendor.t($data.order.roomName),
    o: common_vendor.t($data.order.roomPrice),
    p: common_vendor.t($data.order.nightCount),
    q: common_vendor.t($data.order.totalPrice),
    r: $data.order.status === 3
  }, $data.order.status === 3 ? {
    s: common_vendor.t($data.order.cancelReason || "用户主动取消")
  } : {}, {
    t: $data.order.status === 0
  }, $data.order.status === 0 ? {
    v: common_vendor.o((...args) => $options.cancelOrder && $options.cancelOrder(...args)),
    w: common_vendor.o((...args) => $options.payOrder && $options.payOrder(...args))
  } : $data.order.status === 1 ? {
    y: common_vendor.o((...args) => $options.cancelOrder && $options.cancelOrder(...args))
  } : $data.order.status === 4 ? {
    A: common_vendor.o((...args) => $options.reBook && $options.reBook(...args))
  } : {
    B: common_vendor.o((...args) => $options.contactService && $options.contactService(...args))
  }, {
    x: $data.order.status === 1,
    z: $data.order.status === 4
  });
}
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["render", _sfc_render], ["__scopeId", "data-v-5511cfa9"]]);
wx.createPage(MiniProgramPage);
