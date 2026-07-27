package com.travel.annotation;

import java.lang.annotation.*;

/**
 * 幂等性注解（基于 Redis Token）
 *
 * 使用方式：
 * <pre>
 * @Idempotent(key = "#token", expireTime = 300, message = "请勿重复提交")
 * public Result<Order> createOrder(@RequestParam("token") String token, ...) {
 *     // 业务逻辑
 * }
 * </pre>
 *
 * 原理：
 * 1. 前端先获取 token：GET /order/token
 * 2. 业务请求带 token
 * 3. 切面检查 Redis 中 token 是否存在
 *    - 不存在 → 存入 Redis（TTL=5分钟），放行
 *    - 存在 → 拒绝请求
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等 key，支持 SpEL 表达式
     * 例如：#token、#orderToken
     */
    String key();

    /**
     * Token 过期时间（秒）
     * 默认 300 秒（5分钟）
     */
    int expireTime() default 300;

    /**
     * 重复提交时的提示信息
     */
    String message() default "请勿重复提交";

    /**
     * 是否在业务执行成功后删除 Token
     * 默认为 true（单次幂等）
     * false 用于可重复操作的场景
     */
    boolean deleteOnSuccess() default true;
}
