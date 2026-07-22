package com.travel.common;

import com.travel.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    //捕获：Token过期异常
    @ExceptionHandler(TokenExpiredException.class)
    public Result<String> handleTokenExpired(TokenExpiredException e){
        return Result.error(ResultCode.UNAUTHORIZED);
    }

    //捕获：Token无效异常
    @ExceptionHandler(TokenInvalidException.class)
    public Result<String> handleTokenInvalid(TokenInvalidException e){
        return Result.error(ResultCode.UNAUTHORIZED);
    }

    //捕获：Token 空异常
    @ExceptionHandler(TokenNullException.class)
    public Result<String> handleTokenNull(TokenNullException e){
        return Result.error(ResultCode.UNAUTHORIZED);
    }

    //============== 业务异常  =================
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBusiness(BusinessException e, HttpServletRequest request){
        log.warn("业务异常 | path={} | code={} | msg={}",
                request.getRequestURI(), e.getCode(), e.getMessage());
        return ResultUtil.fail(e.getCode(), e.getMessage());
    }

    // ========== 远程服务异常 ==========

    @ExceptionHandler(RemoteException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Result<Void> handleRemote(RemoteException e, HttpServletRequest request) {
        log.error("远程服务异常 | service={} | path={} | msg={}",
                e.getServiceName(), request.getRequestURI(), e.getMessage(), e);
        return ResultUtil.fail(ResultCode.PYTHON_SERVICE_ERROR);
    }

    // ========== 参数校验异常 ==========

    // Spring 框架自动抛出，是在 controller 接收参数的时参数校验失败时触发
    @ExceptionHandler(MethodArgumentNotValidException.class)  //处理 JSON 请求体（@RequestBody）里面的字段校验失败
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ":" + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 | msg={}", message);
        return ResultUtil.fail(ResultCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(BindException.class) //处理表单提交/Query 参数（@ModelAttribute或@RequestParam）的参数校验失败
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBind(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(f -> f.getField() + ":" + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败 | msg={}", message);
        return ResultUtil.fail(ResultCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissing(MissingServletRequestParameterException e) {
        String message = "缺少参数: " + e.getParameterName();
        log.warn("缺少参数 | {}", message);
        return ResultUtil.fail(ResultCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMismatch(MethodArgumentTypeMismatchException e) {
        String message = "参数类型错误: " + e.getName();
        log.warn("参数类型错误 | {}", message);
        return ResultUtil.fail(ResultCode.BAD_REQUEST, message);
    }

    // 处理 @Validated + @RequestParam 校验失败（如 @Min/@Max/@Pattern 等）
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败 | msg={}", message);
        return ResultUtil.fail(ResultCode.BAD_REQUEST, message);
    }

    // ========== 404/405异常 ==========

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoHandlerFoundException e) {
        log.warn("404未找到 | path={}", e.getRequestURL());
        return ResultUtil.fail(ResultCode.NOT_FOUND);
    }

    //  处理 HTTP 方法不匹配（405）
    // 前端用错了 HTTP 动词（比如用 GET 请求一个只支持 POST 的接口）
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupport(HttpRequestMethodNotSupportedException e) {
        log.warn("方法不支持 | method={}", e.getMethod());
        return ResultUtil.fail(ResultCode.BAD_REQUEST, "不支持该请求方法");
    }

    // ========== 通用异常（兜底）==========

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleGeneral(Exception e, HttpServletRequest request) {
        log.error("系统异常 | path={} | msg={}",
                request.getRequestURI(), e.getMessage(), e);
        return ResultUtil.fail(ResultCode.INTERNAL_ERROR);
    }
}
