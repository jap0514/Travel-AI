package com.travel.common;

public enum ResultCode {
      /* 成功 */
      SUCCESS(200, "操作成功"),

      /* 客户端错误 */
      BAD_REQUEST(400, "请求参数错误"),
      UNAUTHORIZED(401, "未授权，请登录"),
      FORBIDDEN(403, "无权限访问"),
      NOT_FOUND(404, "资源不存在"),
      RATE_LIMITED(429, "请求过于频繁，请稍后再试"),

      /* 业务错误 */
      USER_NOT_FOUND(10001, "用户不存在"),
      ORDER_NOT_FOUND(10002, "订单不存在"),
      ORDER_CANCELLED(10003, "订单已取消"),
      ORDER_PAID(10004, "订单已支付"),
      INSUFFICIENT_BALANCE(10005, "余额不足"),
      ROOM_NOT_AVAILABLE(10006, "房间不可用"),

      /* 远程服务错误 */
      PYTHON_SERVICE_ERROR(20001, "AI服务异常"),
      PYTHON_SERVICE_TIMEOUT(20002, "AI服务超时"),
      PYTHON_SERVICE_UNAVAILABLE(20003, "AI服务暂不可用"),

      /* 系统错误 */
      INTERNAL_ERROR(500, "系统异常，请稍后再试"),
      REDIS_ERROR(50001, "缓存服务异常"),
      DATABASE_ERROR(50002, "数据库服务异常");

      private final int code;
      private final String message;

      ResultCode(int code, String message) {
          this.code = code;
          this.message = message;
      }

      public int getCode() { return code; }
      public String getMessage() { return message; }
  }