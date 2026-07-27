package com.travel.annotation;

import java.lang.annotation.*;

/**
 * 三级缓存注解
 * 使用AOP实现三级缓存：Caffeine(本地) → Redis(分布式) → MySQL
 * 三大防护：缓存穿透(布隆过滤器+空值) | 缓存击穿(分布式锁) | 缓存雪崩(TTL随机偏移)
 *
 * 用法：
 * <pre>
 * @ThreeTierCache(
 *     cacheName = "hotels",
 *     key = "#city",
 *     localTtlMinutes = 5,
 *     redisTtlMinutes = 30,
 *     redisTtlOffsetMinutes = 5,
 *     useBloomFilter = true
 * )
 * public List<HotelVO> getAllHotelInfo(String city) {
 *     // 只需要写查DB的逻辑，缓存全部由AOP处理
 *     return hotelMapper.selectList(...);
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ThreeTierCache {

    /**
     * 缓存名称（对应Spring Cache的cacheNames）
     */
    String cacheName();

    /**
     * 缓存key，支持SpEL表达式
     * 例如：#city、#hotelId、#userId
     */
    String key();

    /**
     * 本地缓存(Caffeine)的TTL，单位：分钟
     * 默认5分钟
     */
    int localTtlMinutes() default 5;

    /**
     * Redis缓存的TTL，单位：分钟
     * 默认30分钟
     */
    int redisTtlMinutes() default 30;

    /**
     * Redis TTL的随机偏移范围，单位：分钟
     * 例如：baseTtl=30, offset=5，实际TTL=25~35分钟
     * 用于防止缓存雪崩
     */
    int redisTtlOffsetMinutes() default 5;

    /**
     * 是否使用布隆过滤器
     * 用于防止缓存穿透
     * 默认true
     */
    boolean useBloomFilter() default true;

    /**
     * 是否使用空值缓存
     * 用于防止缓存穿透
     * 默认true
     */
    boolean useNullValueCache() default true;

    /**
     * 空值缓存的TTL，单位：秒
     * 默认30秒
     */
    int nullValueTtlSeconds() default 30;

    /**
     * 分布式锁的等待时间，单位：秒
     * 默认3秒
     */
    int lockWaitSeconds() default 3;

    /**
     * 分布式锁的自动过期时间，单位：秒
     * 默认10秒
     */
    int lockExpireSeconds() default 10;
}
