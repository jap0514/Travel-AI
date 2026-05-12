package com.travel.common;

import com.travel.exception.TokenExpiredException;
import com.travel.exception.TokenInvalidException;
import com.travel.exception.TokenNullException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    //捕获：Token过期异常
    @ExceptionHandler(TokenExpiredException.class)
    public Result<String> handleExpired(TokenExpiredException e){
        return Result.error(401,e.getMessage());
    }

    //捕获：Token无效异常
    @ExceptionHandler(TokenInvalidException.class)
    public Result<String> handleExpired(TokenInvalidException e){
        return Result.error(401,e.getMessage());
    }

    //捕获：Token 空异常
    @ExceptionHandler(TokenNullException.class)
    public Result<String> handleExpired(TokenNullException e){
        return Result.error(401,e.getMessage());
    }
}
