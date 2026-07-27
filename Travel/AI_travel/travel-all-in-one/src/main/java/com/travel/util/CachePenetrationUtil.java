package com.travel.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 缓存穿透防护工具类
 * 处理空值缓存，防止缓存穿透到DB
 */
@Slf4j
@Component
public class CachePenetrationUtil {

    /** 空值标记 */
    private static final String NULL_VALUE = "NULL";

    /** 空值缓存的TTL（秒）*/
    private static final long NULL_VALUE_TTL = 30;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 判断是否为缓存的空值
     *
     * @param key 缓存key
     * @return true = 是空值缓存（表示数据库也没有）
     */
    public boolean isNullValue(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            return NULL_VALUE.equals(value);
        } catch (Exception e) {
            log.warn("检查空值缓存失败: key={}, error={}", key, e.getMessage());
            return false;  // 出错时保守处理，继续查DB
        }
    }

    /**
     * 设置空值到缓存
     *
     * @param key 缓存key
     */
    public void setNullValue(String key) {
        try {
            redisTemplate.opsForValue().set(key, NULL_VALUE, NULL_VALUE_TTL, TimeUnit.SECONDS);
            log.debug("缓存空值: key={}, ttl={}s", key, NULL_VALUE_TTL);
        } catch (Exception e) {
            log.warn("设置空值缓存失败: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 判断结果是否为空，并处理缓存
     *
     * @param key      缓存key
     * @param dbResult 数据库查询结果
     * @param cacheName 缓存名称（用于记录日志）
     * @return true = 结果为空（且已设置空值缓存），false = 结果不为空
     */
    public boolean handleEmptyResult(String key, Object dbResult, String cacheName) {
        if (dbResult == null) {
            // 数据库也没有，设置空值缓存
            setNullValue(key);
            log.info("【缓存穿透防护】数据库无数据，已设置空值缓存: cacheName={}, key={}", cacheName, key);
            return true;
        }
        return false;
    }
}
