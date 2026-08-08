/**
 * API 请求封装
 * 基于后端接口文档
 */

const BASE_URL = 'http://localhost:9999'

/**
 * 发起请求
 */
function request(options) {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')

    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : '',
        ...options.header
      },
      success: (res) => {
        if (res.statusCode === 200) {
          if (res.data.code === 200) {
            resolve(res.data.data)
          } else {
            uni.showToast({
              title: res.data.message || '请求失败',
              icon: 'none'
            })
            reject(res.data)
          }
        } else if (res.statusCode === 401) {
          uni.removeStorageSync('token')
          uni.removeStorageSync('userInfo')
          uni.navigateTo({ url: '/pages/login/login' })
          reject(res)
        } else {
          uni.showToast({
            title: '网络请求失败',
            icon: 'none'
          })
          reject(res)
        }
      },
      fail: (err) => {
        uni.showToast({
          title: '网络错误',
          icon: 'none'
        })
        reject(err)
      }
    })
  })
}

// ============ 登录相关 ============

export const login = (code) => request({
  url: '/login',
  method: 'POST',
  data: { code }
})

export const logout = () => request({
  url: '/logout',
  method: 'POST'
})

// ============ 会话相关 ============

export const createSession = (data) => request({
  url: '/session/createSession',
  method: 'POST',
  data
})

export const getUserSessions = (userId, page = 1, size = 10) => request({
  url: `/session/getUserSessions/${userId}`,
  method: 'GET',
  data: { page, size }
})

export const getSessionMessages = (userId, sessionId, page = 1, size = 20) => request({
  url: `/session/${userId}/${sessionId}/message`,
  method: 'GET',
  data: { page, size }
})

// ============ 消息相关 ============

export const sendMessage = (data) => request({
  url: '/message/sendMessage',
  method: 'POST',
  data
})

// ============ 酒店相关 ============

export const getHotelByCity = (city, page = 1, size = 10) => request({
  url: '/hotel/hotelInfo/getHotelByCity',
  method: 'GET',
  data: { city, page, size }
})

export const getHotelRoomType = (hotelId) => request({
  url: '/hotel/hotelInfo/getHotelRoomType',
  method: 'GET',
  data: { hotelId }
})

export const getHotelRoom = (roomTypeId) => request({
  url: '/hotel/hotelInfo/getHotelRoom',
  method: 'GET',
  data: { roomTypeId }
})

export const selectEmptyRoom = (city, checkIn, checkOut) => request({
  url: '/hotel/hotelInfo/selectEmptyRoom',
  method: 'GET',
  data: { city, checkIn, checkOut }
})

export const verifyRoom = (roomId, checkIn, checkOut) => request({
  url: '/hotel/api/hotel/verifyRoom',
  method: 'GET',
  data: { roomId, checkIn, checkOut }
})

// ============ 订单相关 ============

export const getIdempotentToken = () => request({
  url: '/hotel/order/token',
  method: 'GET'
})

export const createOrder = (data) => request({
  url: '/hotel/order/createOrder',
  method: 'POST',
  data
})

export const getOrderList = (userId, page = 1, size = 10) => request({
  url: '/hotel/order/list',
  method: 'GET',
  data: { userId, page, size }
})

export const getOrderDetail = (orderNo) => request({
  url: `/hotel/order/${orderNo}`,
  method: 'GET'
})

export const cancelOrder = (orderNo) => request({
  url: `/hotel/order/${orderNo}/cancel`,
  method: 'PUT'
})

export const completeOrder = (orderNo) => request({
  url: `/hotel/order/${orderNo}/complete`,
  method: 'PUT'
})

export const payOrder = (orderNo) => request({
  url: `/hotel/order/${orderNo}/pay`,
  method: 'PUT'
})

// ============ 统计相关 ============

export const getUserDestinations = (userId) => request({
  url: `/statistics/user/${userId}/destinations`,
  method: 'GET'
})

export const getPopularDestinations = (days) => request({
  url: '/statistics/destinations',
  method: 'GET',
  data: { days }
})

export default {
  login,
  logout,
  createSession,
  getUserSessions,
  getSessionMessages,
  sendMessage,
  getHotelByCity,
  getHotelRoomType,
  getHotelRoom,
  selectEmptyRoom,
  verifyRoom,
  getIdempotentToken,
  createOrder,
  getOrderList,
  getOrderDetail,
  cancelOrder,
  completeOrder,
  payOrder,
  getUserDestinations,
  getPopularDestinations
}
