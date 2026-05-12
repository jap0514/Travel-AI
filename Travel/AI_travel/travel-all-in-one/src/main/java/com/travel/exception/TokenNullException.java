package com.travel.exception;

public class TokenNullException extends RuntimeException {
    public TokenNullException() {
        super("请先登录");
    }
}