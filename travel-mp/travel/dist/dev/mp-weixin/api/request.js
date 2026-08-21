"use strict";
const common_vendor = require("../common/vendor.js");
const BASE_URL = "http://10.63.45.65:9999";
function request(options) {
  return new Promise((resolve, reject) => {
    const token = common_vendor.index.getStorageSync("token");
    const filteredData = {};
    const sourceData = options.data || {};
    for (const key in sourceData) {
      if (sourceData[key] !== void 0 && sourceData[key] !== null && sourceData[key] !== "") {
        filteredData[key] = sourceData[key];
      }
    }
    common_vendor.index.request({
      url: BASE_URL + options.url,
      method: options.method || "GET",
      data: filteredData,
      header: {
        "Content-Type": "application/json",
        "Authorization": token ? `Bearer ${token}` : "",
        ...options.header
      },
      success: (res) => {
        if (res.statusCode === 200) {
          if (res.data.code === 200) {
            resolve(res.data.data);
          } else {
            common_vendor.index.showToast({
              title: res.data.message || "请求失败",
              icon: "none"
            });
            reject(res.data);
          }
        } else if (res.statusCode === 401) {
          common_vendor.index.removeStorageSync("token");
          common_vendor.index.removeStorageSync("userInfo");
          common_vendor.index.navigateTo({ url: "/pages/login/login" });
          reject(res);
        } else {
          common_vendor.index.showToast({
            title: "网络请求失败",
            icon: "none"
          });
          reject(res);
        }
      },
      fail: (err) => {
        common_vendor.index.showToast({
          title: "网络错误",
          icon: "none"
        });
        reject(err);
      }
    });
  });
}
const login = (code) => request({
  url: "/login",
  method: "POST",
  data: { code }
});
const logout = () => request({
  url: "/logout",
  method: "POST"
});
const createSession = (data) => request({
  url: "/session/createSession",
  method: "POST",
  data
});
const getUserSessions = (userId, page = 1, size = 10) => request({
  url: `/session/getUserSessions/${userId}`,
  method: "GET",
  data: { page, size }
});
const getSessionMessages = (userId, sessionId, page = 1, size = 20) => request({
  url: `/session/${userId}/${sessionId}/message`,
  method: "GET",
  data: { page, size }
});
const sendMessage = (data) => request({
  url: "/message/sendMessage",
  method: "POST",
  data
});
const getHotelByCity = (city, keyword = "", minStar = null, minPrice = null, maxPrice = null, facilities = null, page = 1, size = 10) => request({
  url: "/hotel/hotelInfo/getHotelByCity",
  method: "GET",
  data: { city, keyword, minStar, minPrice, maxPrice, facilities, page, size }
});
const getHotelById = (hotelId) => request({
  url: "/hotel/hotelInfo/getHotelById",
  method: "GET",
  data: { hotelId }
});
const getHotelRoomType = (hotelId) => request({
  url: "/hotel/hotelInfo/getHotelRoomType",
  method: "GET",
  data: { hotelId }
});
const getOrderList = (userId, page = 1, size = 10, status) => request({
  url: "/hotel/order/list",
  method: "GET",
  data: { userId, page, size, status }
});
const getOrderDetail = (orderNo) => request({
  url: `/hotel/order/${orderNo}`,
  method: "GET"
});
const cancelOrder = (orderNo, data = {}) => request({
  url: `/hotel/order/${orderNo}/cancel`,
  method: "PUT",
  data
});
const payOrder = (orderNo, data = {}) => request({
  url: `/hotel/order/${orderNo}/pay`,
  method: "PUT",
  data
});
const getUserDestinations = (userId) => request({
  url: `/statistics/user/${userId}/destinations`,
  method: "GET"
});
const getPopularDestinations = (days) => request({
  url: "/statistics/destinations",
  method: "GET",
  data: { days }
});
exports.cancelOrder = cancelOrder;
exports.createSession = createSession;
exports.getHotelByCity = getHotelByCity;
exports.getHotelById = getHotelById;
exports.getHotelRoomType = getHotelRoomType;
exports.getOrderDetail = getOrderDetail;
exports.getOrderList = getOrderList;
exports.getPopularDestinations = getPopularDestinations;
exports.getSessionMessages = getSessionMessages;
exports.getUserDestinations = getUserDestinations;
exports.getUserSessions = getUserSessions;
exports.login = login;
exports.logout = logout;
exports.payOrder = payOrder;
exports.sendMessage = sendMessage;
