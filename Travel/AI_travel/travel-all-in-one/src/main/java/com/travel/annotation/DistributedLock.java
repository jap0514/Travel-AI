package com.travel.annotation;

import java.lang.annotation.*;

/**
 * 分布式锁注解
 *
 * 使用方式：
 * <pre>
 * @DistributedLock(
 *     key = "'lock:order:' + #orderNo",
 *     waitTime = 5,
 *     leaseTime = 10
 * )
 * public Result<Void> payOrder(String orderNo) {
 *     // 业务逻辑
 * }
 * </pre>
 *
 * 注意：
 * - key 支持 SpEL 表达式
 * - waitTime：获取锁等待时间（秒），默认 5 秒
 * - leaseTime：锁自动过期时间（秒），默认 10 秒
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁的 key，支持 SpEL 表达式
     */
    String key();

    /**
     * 获取锁等待时间（秒）
     * 默认 5 秒
     */
    int waitTime() default 5;

    /**
     * 锁自动过期时间（秒）
     * 默认 10 秒
     */
    int leaseTime() default 10;

    /**
     * 是否使用公平锁
     * 公平锁：按请求顺序获取锁
     * 非公平锁：随机获取（性能更高）
     * 默认 false
     */
    boolean fair() default false;
}
