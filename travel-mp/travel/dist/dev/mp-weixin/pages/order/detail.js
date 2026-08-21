"use strict";
const common_vendor = require("../../common/vendor.js");
const api_request = require("../../api/request.js");
const _sfc_main = {
  data() {
    return {
      orderNo: "",
      order: {},
      loading: false
    };
  },
  onLoad(options) {
    this.orderNo = options.orderNo;
    this.loadOrderDetail();
  },
  methods: {
    formatDateShort(dateTime) {
      if (!dateTime) return "";
      const d = new Date(dateTime);
      if (isNaN(d.getTime())) return "";
      return `${d.getMonth() + 1}月${d.getDate()}日`;
    },
    formatDateTime(dateTime) {
      if (!dateTime) return "";
      const d = new Date(dateTime);
      if (isNaN(d.getTime())) return "";
      const pad = (n) => n < 10 ? "0" + n : n;
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
    },
    async loadOrderDetail() {
      if (!this.orderNo) return;
      this.loading = true;
      try {
        const o = await api_request.getOrderDetail(this.orderNo);
        this.order = {
          orderNo: o.orderNo,
          hotelName: o.hotelName,
          roomName: o.roomTypeName,
          roomImage: "/static/images/room-default.jpg",
          checkInDate: this.formatDateShort(o.checkInDate),
          checkOutDate: this.formatDateShort(o.checkOutDate),
          nightCount: o.days,
          roomPrice: o.days > 0 ? Number(o.totalPrice) / o.days : 0,
          // 反推单价
          totalPrice: Number(o.totalPrice),
          status: o.status,
          createTime: this.formatDateTime(o.createTime),
          guestName: o.guestName,
          guestPhone: o.guestPhone
        };
      } catch (e) {
      } finally {
        this.loading = false;
      }
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
        editable: true,
        placeholderText: "请输入取消原因（可选）",
        success: async (res) => {
          if (!res.confirm) return;
          try {
            await api_request.cancelOrder(this.orderNo, { cancelReason: res.content || "用户主动取消" });
            common_vendor.index.showToast({ title: "取消成功", icon: "success" });
            this.loadOrderDetail();
          } catch (e) {
          }
        }
      });
    },
    async payOrder() {
      try {
        await api_request.payOrder(this.orderNo, {});
        common_vendor.index.showToast({ title: "支付成功", icon: "success" });
        this.loadOrderDetail();
      } catch (e) {
      }
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
