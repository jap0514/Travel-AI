package com.travel.common;

public class ResultUtil {

      public static <T> Result<T> ok() {
          return Result.success();
      }

      public static <T> Result<T> ok(T data) {
          return Result.success(data);
      }

      public static <T> Result<T> ok(T data, String message) {
          return Result.success(data, message);
      }

      public static <T> Result<T> fail(ResultCode resultCode) {
          return Result.error(resultCode);
      }

      public static <T> Result<T> fail(ResultCode resultCode, String message) {
          return Result.error(resultCode, message);
      }

      public static <T> Result<T> fail(int code, String message) {
          return Result.error(code, message);
      }

      public static <T> Result<T> fail(String message) {
          return Result.error(ResultCode.INTERNAL_ERROR.getCode(), message);
      }
  }