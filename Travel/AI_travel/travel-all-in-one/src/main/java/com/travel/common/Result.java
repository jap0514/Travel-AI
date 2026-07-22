package com.travel.common;

import lombok.Data;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {
    private int code;
    private String message;
    private T data;
    private long timestamp;
    private String traceId;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(ResultCode.SUCCESS.getCode());
        r.setMessage(ResultCode.SUCCESS.getMessage());
        r.setData(data);
        return r;
    }

    public static <T> Result<T> success(T data, String message) {
        Result<T> r = new Result<>();
        r.setCode(ResultCode.SUCCESS.getCode());
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        Result<T> r = new Result<>();
        r.setCode(resultCode.getCode());
        r.setMessage(resultCode.getMessage());
        return r;
    }

    public static <T> Result<T> error(ResultCode resultCode, String message) {
        Result<T> r = new Result<>();
        r.setCode(resultCode.getCode());
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    // 链式调用
    public Result<T> traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}