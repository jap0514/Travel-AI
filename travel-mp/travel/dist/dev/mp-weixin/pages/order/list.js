"use strict";
const common_vendor = require("../../common/vendor.js");
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
    loadOrders() {
      if (this.isLoading || this.noMore) return;
      this.isLoading = true;
      setTimeout(() => {
        const mockOrders = [
          {
            orderNo: "HT202401010001",
            hotelName: "北京饭店",
            roomName: "豪华间",
            roomImage: "/static/images/room2.jpg",
            checkInDate: "1月15日",
            checkOutDate: "1月17日",
            nightCount: 2,
            totalPrice: 1776,
            status: 0
          },
          {
            orderNo: "HT202401010002",
            hotelName: "上海外滩酒店",
            roomName: "标准间",
            roomImage: "/static/images/room1.jpg",
            checkInDate: "1月20日",
            checkOutDate: "1月22日",
            nightCount: 2,
            totalPrice: 1176,
            status: 1
          },
          {
            orderNo: "HT202401010003",
            hotelName: "杭州西湖酒店",
            roomName: "套房",
            roomImage: "/static/images/room3.jpg",
            checkInDate: "12月25日",
            checkOutDate: "12月27日",
            nightCount: 2,
            totalPrice: 3176,
            status: 4
          }
        ];
        if (this.currentTab === "all") {
          this.orders = mockOrders;
        } else {
          this.orders = mockOrders.filter((o) => o.status === parseInt(this.currentTab));
        }
        this.noMore = true;
        this.isLoading = false;
      }, 1e3);
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
        success: (res) => {
          if (res.confirm) {
            common_vendor.index.showToast({ title: "取消成功", icon: "success" });
            this.loadOrders();
          }
        }
      });
    },
    payOrder(order) {
      common_vendor.index.showToast({ title: "支付功能开发中", icon: "none" });
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
