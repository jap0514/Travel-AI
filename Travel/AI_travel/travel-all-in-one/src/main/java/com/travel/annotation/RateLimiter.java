package com.travel.annotation;

import java.lang.annotation.*;

/**
 * Sentinel 限流注解
 *
 * 使用方式：
 * <pre>
 * @RateLimiter(value = 100, timeout = 1000)
 * public Result<String> sendMessage(SendMessageDTO dto) {
 *     // 限流：1秒内最多100个请求
 * }
 * </pre>
 *
 * 限流维度说明：
 * - count: 时间窗口内的最大请求数
 * - timeout: 等待获取令牌的最大时间（毫秒），超时后直接拒绝
 *
 * 限流策略：
 * - QPS模式：统计时间窗口内的请求数
 * - 支持多维度：用户ID、IP、接口名等作为资源名
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {

    /**
     * 限流资源名，默认使用方法全限定名
     * 可自定义，用于区分不同接口
     */
    String resourceName() default "";

    /**
     * 每秒允许的最大请求数（QPS）
     * 默认100
     */
    double count() default 100;

    /**
     * 获取令牌的等待超时时间（毫秒）
     * 默认1000ms，超时后抛出 BlockException
     */
    long timeout() default 1000;

    /**
     * 限流类型
     * - DEFAULT: 使用方法名作为资源名
     * - USER: 用户维度限流（需配合 UserContext）
     * - IP: IP维度限流
     */
    LimitType limitType() default LimitType.DEFAULT;

    /**
     * 限流类型枚举
     */
    enum LimitType {
        /** 默认：按接口维度限流 */
        DEFAULT,
        /** 按用户维度限流 */
        USER,
        /** 按IP维度限流 */
        IP
    }
}
