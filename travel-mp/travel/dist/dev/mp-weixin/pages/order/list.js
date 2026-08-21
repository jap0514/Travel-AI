"use strict";
const common_vendor = require("../../common/vendor.js");
const api_request = require("../../api/request.js");
const common_assets = require("../../common/assets.js");
const _sfc_main = {
  data() {
    return {
      currentTab: "all",
      page: 1,
      pageSize: 10,
      isLoading: false,
      noMore: false,
      tabs: [
        { status: "all", name: "全部" },
        { status: "0", name: "待支付" },
        { status: "1", name: "已支付" },
        { status: "2", name: "已确认" },
        { status: "4", name: "已完成" }
      ],
      orders: []
    };
  },
  onLoad() {
    this.checkLoginAndLoad();
  },
  onShow() {
    this.loadOrders();
  },
  methods: {
    checkLoginAndLoad() {
      const token = common_vendor.index.getStorageSync("token");
      if (!token) {
        common_vendor.index.navigateTo({ url: "/pages/login/login" });
        return;
      }
      this.loadOrders();
    },
    switchTab(status) {
      this.currentTab = status;
      this.page = 1;
      this.noMore = false;
      this.orders = [];
      this.loadOrders();
    },
    // 把 LocalDateTime 格式化为 "1月15日" 形式
    formatDateShort(dateTime) {
      if (!dateTime) return "";
      const d = new Date(dateTime);
      if (isNaN(d.getTime())) return "";
      return `${d.getMonth() + 1}月${d.getDate()}日`;
    },
    // 字段映射：后端 HotelBookingVO → 前端展示结构
    mapOrder(o) {
      return {
        orderNo: o.orderNo,
        hotelName: o.hotelName,
        roomName: o.roomTypeName,
        // 后端是 roomTypeName
        roomImage: "/static/images/room-default.jpg",
        // 后端无此字段
        checkInDate: this.formatDateShort(o.checkInDate),
        checkOutDate: this.formatDateShort(o.checkOutDate),
        nightCount: o.days,
        // 后端是 days
        totalPrice: Number(o.totalPrice),
        status: o.status
      };
    },
    async loadOrders() {
      if (this.isLoading || this.noMore) return;
      const userInfo = common_vendor.index.getStorageSync("userInfo") || {};
      const userId = userInfo.id;
      if (!userId) {
        common_vendor.index.navigateTo({ url: "/pages/login/login" });
        return;
      }
      this.isLoading = true;
      try {
        const statusParam = this.currentTab === "all" ? void 0 : parseInt(this.currentTab);
        const pageVO = await api_request.getOrderList(userId, this.page, this.pageSize, statusParam);
        const records = pageVO && (pageVO.records || pageVO.list || pageVO) || [];
        const mapped = records.map((o) => this.mapOrder(o));
        if (mapped.length < this.pageSize) {
          this.noMore = true;
        }
        this.orders = this.orders.concat(mapped);
        this.page++;
      } catch (e) {
      } finally {
        this.isLoading = false;
      }
    },
    loadMore() {
      this.loadOrders();
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
    goDetail(order) {
      common_vendor.index.navigateTo({
        url: `/pages/order/detail?orderNo=${order.orderNo}`
      });
    },
    cancelOrder(order) {
      common_vendor.index.showModal({
        title: "提示",
        content: "确定要取消该订单吗？",
        editable: true,
        placeholderText: "请输入取消原因（可选）",
        success: async (res) => {
          if (!res.confirm) return;
          try {
            await api_request.cancelOrder(order.orderNo, { cancelReason: res.content || "用户主动取消" });
            common_vendor.index.showToast({ title: "取消成功", icon: "success" });
            this.page = 1;
            this.noMore = false;
            this.orders = [];
            this.loadOrders();
          } catch (e) {
          }
        }
      });
    },
    async payOrder(order) {
      try {
        await api_request.payOrder(order.orderNo, {});
        common_vendor.index.showToast({ title: "支付成功", icon: "success" });
        this.page = 1;
        this.noMore = false;
        this.orders = [];
        this.loadOrders();
      } catch (e) {
      }
    },
    goHotel() {
      common_vendor.index.switchTab({ url: "/pages/hotel/list" });
    }
  }
};
function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
  return common_vendor.e({
    a: common_vendor.f($data.tabs, (tab, k0, i0) => {
      return {
        a: common_vendor.t(tab.name),
        b: tab.status,
        c: $data.currentTab === tab.status ? 1 : "",
        d: common_vendor.o(($event) => $options.switchTab(tab.status), tab.status)
      };
    }),
    b: $data.orders.length === 0
  }, $data.orders.length === 0 ? {
    c: common_assets._imports_0$4,
    d: common_vendor.o((...args) => $options.goHotel && $options.goHotel(...args))
  } : {}, {
    e: common_vendor.f($data.orders, (order, k0, i0) => {
      return common_vendor.e({
        a: common_vendor.t(order.orderNo),
        b: common_vendor.t($options.getStatusText(order.status)),
        c: common_vendor.n($options.getStatusClass(order.status)),
        d: order.roomImage || "/static/images/room-default.jpg",
        e: common_vendor.t(order.hotelName),
        f: common_vendor.t(order.roomName),
        g: common_vendor.t(order.checkInDate),
        h: common_vendor.t(order.checkOutDate),
        i: common_vendor.t(order.totalPrice),
        j: common_vendor.t(order.nightCount),
        k: order.status === 0
      }, order.status === 0 ? {
        l: common_vendor.o(($event) => $options.cancelOrder(order), order.orderNo),
        m: common_vendor.o(($event) => $options.payOrder(order), order.orderNo)
      } : {}, {
        n: order.orderNo,
        o: common_vendor.o(($event) => $options.goDetail(order), order.orderNo)
      });
    }),
    f: $data.isLoading
  }, $data.isLoading ? {} : {}, {
    g: $data.noMore && $data.orders.length > 0
  }, $data.noMore && $data.orders.length > 0 ? {} : {}, {
    h: common_vendor.o((...args) => $options.loadMore && $options.loadMore(...args))
  });
}
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["render", _sfc_render], ["__scopeId", "data-v-80f8e5f8"]]);
wx.createPage(MiniProgramPage);
