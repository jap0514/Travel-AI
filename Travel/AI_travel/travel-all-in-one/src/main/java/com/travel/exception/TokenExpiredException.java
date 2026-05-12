package com.travel.exception;

public class TokenExpiredException extends RuntimeException {
    public TokenExpiredException() {
        super("Token已过期，请重新登录");
    }
}