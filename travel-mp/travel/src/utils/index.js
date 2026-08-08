/**
 * 工具函数
 */

/**
 * 格式化日期
 * @param {Date|string|number} date - 日期
 * @param {string} format - 格式 YYYY-MM-DD HH:mm:ss
 */
export function formatDate(date, format = 'YYYY-MM-DD') {
  if (!date) return ''

  const d = new Date(date)

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 格式化金额
 * @param {number} amount - 金额
 */
export function formatPrice(amount) {
  return `¥${parseFloat(amount).toFixed(2)}`
}

/**
 * 微信手机号登录
 */
export function wxLogin() {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (res) => {
        resolve(res.code)
      },
      fail: (err) => {
        reject(err)
      }
    })
  })
}

/**
 * 获取微信手机号
 */
export function getPhoneNumber(e) {
  return new Promise((resolve, reject) => {
    if (e.detail.errMsg === 'getPhoneNumber:ok') {
      resolve(e.detail)
    } else {
      reject(new Error('用户拒绝'))
    }
  })
}

/**
 * 校验手机号
 */
export function validatePhone(phone) {
  return /^1[3-9]\d{9}$/.test(phone)
}

/**
 * 显示加载提示
 */
export function showLoading(title = '加载中...') {
  uni.showLoading({
    title,
    mask: true
  })
}

/**
 * 隐藏加载提示
 */
export function hideLoading() {
  uni.hideLoading()
}

/**
 * 显示成功提示
 */
export function showSuccess(title = '成功') {
  uni.showToast({
    title,
    icon: 'success'
  })
}

/**
 * 显示错误提示
 */
export function showError(title = '错误') {
  uni.showToast({
    title,
    icon: 'none'
  })
}

/**
 * 页面跳转
 */
export function navigateTo(url) {
  uni.navigateTo({ url })
}

export function switchTab(url) {
  uni.switchTab({ url })
}

export function reLaunch(url) {
  uni.reLaunch({ url })
}

export default {
  formatDate,
  formatPrice,
  wxLogin,
  getPhoneNumber,
  validatePhone,
  showLoading,
  hideLoading,
  showSuccess,
  showError,
  navigateTo,
  switchTab,
  reLaunch
}
