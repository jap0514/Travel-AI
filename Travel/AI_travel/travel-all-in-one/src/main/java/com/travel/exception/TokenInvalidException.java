package com.travel.exception;

public class TokenInvalidException extends RuntimeException {
    public TokenInvalidException() {
        super("Token无效，请重新登录");
    }

    public TokenInvalidException(String error) {
        super(error);
    }
}