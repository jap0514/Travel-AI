# AI 旅行规划小程序

基于 uni-app + Vue3 开发的微信小程序前端

## 页面结构

```
pages/
├── index/          # 首页
├── login/          # 登录页
├── chat/           # AI 对话页
├── hotel/
│   ├── list.vue    # 酒店列表
│   └── detail.vue  # 酒店详情
├── order/
│   ├── list.vue    # 订单列表
│   └── detail.vue  # 订单详情
└── my/             # 个人中心

src/
├── api/
│   └── request.js  # API 请求封装
└── utils/
    └── index.js    # 工具函数
```

## 功能页面

| 页面 | 说明 | 对应后端 API |
|------|------|-------------|
| 首页 | 轮播图、热门目的地、功能入口 | `/statistics/destinations` |
| 登录 | 微信授权登录 | `/login` |
| AI 对话 | 与 AI 对话生成行程规划 | `/message/sendMessage`, `/session/*` |
| 酒店列表 | 城市选择、酒店搜索 | `/hotel/hotelInfo/getHotelByCity` |
| 酒店详情 | 房型列表、预订 | `/hotel/hotelInfo/getHotelRoomType` |
| 订单列表 | 订单管理、状态筛选 | `/hotel/order/list` |
| 订单详情 | 订单信息、支付/取消 | `/hotel/order/{orderNo}` |
| 个人中心 | 用户信息、统计 | `/statistics/user/{userId}/destinations` |

## 待做

1. **静态资源**: 需要在 `static/images/` 添加以下图片
   - `banner1.jpg`, `banner2.jpg`, `banner3.jpg` - 首页轮播图
   - `dest-*.jpg` - 目的地图片
   - `hotel*.jpg`, `room*.jpg` - 酒店/房间图片
   - `tabbar/*.png` - 底部导航图标
   - `ic-*.png`, `menu-*.png` - 功能图标

2. **业务逻辑**: 所有 `TODO` 注释处需要对接真实 API

3. **配置文件**: 修改 `src/api/request.js` 中的 `BASE_URL` 为实际后端地址

## 运行项目

```bash
# 安装依赖
npm install

# 运行微信小程序
npm run dev:mp-weixin

# 或者 H5 预览
npm run dev:h5
```

## 技术栈

- **框架**: uni-app + Vue3
- **样式**: SCSS
- **请求**: uni.request
- **状态管理**: uni.getStorageSync / localStorage

## 后端对接

后端运行在 `http://localhost:8080`，需要配置合法域名到微信公众平台。
