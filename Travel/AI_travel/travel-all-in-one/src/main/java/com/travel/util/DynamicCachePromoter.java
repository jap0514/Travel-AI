package com.travel.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.travel.context.SpringContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 动态缓存提升器
 * 将热点数据从Redis提升到Caffeine本地缓存
 *
 * 热点数据访问流程：
 * 1. 热点key优先从Caffeine本地缓存获取
 * 2. 未命中则查Redis，命中后回填Caffeine
 * 3. 本地缓存LRU淘汰
 */
@Slf4j
@Component
public class DynamicCachePromoter {

    /** 本地热点缓存的最大容量 */
    private static final int HOT_CACHE_MAX_SIZE = 500;

    /** 本地热点缓存的TTL（分钟） */
    private static final int HOT_CACHE_TTL_MINUTES = 5;

    /** 本地热点缓存（热点数据专用，比普通缓存优先级更高） */
    private Cache<String, Object> hotDataCache;

    /**
     * 热点数据的缓存key集合（按cacheName分组）
     * 用于快速判断某个key是否为热点
     */
    private final ConcurrentHashMap<String, String> hotKeyIndex = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 初始化热点数据专用缓存（LRU策略）
        hotDataCache = Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(HOT_CACHE_MAX_SIZE)
                .expireAfterWrite(HOT_CACHE_TTL_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();

        log.info("【动态缓存】热点数据本地缓存初始化完成，容量={}, TTL={}分钟",
                HOT_CACHE_MAX_SIZE, HOT_CACHE_TTL_MINUTES);
    }

    /**
     * 将热点数据提升到本地缓存
     *
     * @param cacheName 缓存名称（如 "hotels"）
     * @param fullKey 完整缓存key（如 "hotels:北京"）
     */
    public void promoteToLocalCache(String cacheName, String fullKey) {
        try {
            // 先从Redis获取数据
            Object data = getDataFromRedis(cacheName, fullKey);

            if (data != null) {
                // 存入热点数据本地缓存
                hotDataCache.put(fullKey, data);
                hotKeyIndex.put(fullKey, cacheName);

                log.info("【动态缓存】热点数据已提升到本地: key={}", fullKey);
            }

        } catch (Exception e) {
            log.warn("【动态缓存】提升热点数据失败: key={}, error={}", fullKey, e.getMessage());
        }
    }

    /**
     * 从热点本地缓存获取数据
     *
     * @param cacheName 缓存名称
     * @param cacheKey 缓存key
     * @return 缓存值，如果不存在返回null
     */
    public Object getFromHotCache(String cacheName, String cacheKey) {
        String fullKey = cacheName + ":" + cacheKey;

        // 先判断是否为热点key
        if (!hotKeyIndex.containsKey(fullKey)) {
            return null;
        }

        // 从热点缓存获取
        Object result = hotDataCache.getIfPresent(fullKey);

        if (result != null) {
            log.debug("【动态缓存】【热点缓存命中】key={}", fullKey);
        }

        return result;
    }

    /**
     * 判断某个key是否为热点数据
     */
    public boolean isHotKey(String cacheName, String cacheKey) {
        String fullKey = cacheName + ":" + cacheKey;
        return hotKeyIndex.containsKey(fullKey);
    }

    /**
     * 从Redis获取数据
     */
    private Object getDataFromRedis(String cacheName, String fullKey) {
        try {
            CacheManager cacheManager = SpringContextHolder.getCacheManager();
            if (cacheManager == null) {
                return null;
            }

            org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);
            if (springCache == null) {
                return null;
            }

            org.springframework.cache.Cache.ValueWrapper wrapper = springCache.get(fullKey);
            return wrapper != null ? wrapper.get() : null;

        } catch (Exception e) {
            log.warn("【动态缓存】从Redis获取数据失败: key={}, error={}", fullKey, e.getMessage());
            return null;
        }
    }

    /**
     * 移除热点数据
     * 当Redis数据更新时调用
     */
    public void removeHotCache(String cacheName, String cacheKey) {
        String fullKey = cacheName + ":" + cacheKey;
        hotDataCache.invalidate(fullKey);
        hotKeyIndex.remove(fullKey);
        log.debug("【动态缓存】移除热点缓存: key={}", fullKey);
    }

    /**
     * 获取热点缓存统计信息
     */
    public String getStats() {
        CacheStats stats = hotDataCache.stats();
        return String.format("热点缓存: 容量=%d/%d, 命中率=%.2f%%, 淘汰数=%d, 加载时间=%.2fms",
                hotDataCache.estimatedSize(),
                HOT_CACHE_MAX_SIZE,
                stats.hitRate() * 100,
                stats.evictionCount(),
                stats.averageLoadPenalty() / 1_000_000.0);
    }
}
