package com.travel.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 热点数据识别器
 * 使用滑动窗口算法识别热点key
 *
 * 原理：
 * 1. 每次缓存访问，在Redis SortedSet中记录访问次数
 * 2. SortedSet的score为时间戳，value为cacheKey
 * 3. 定时任务统计窗口内访问次数，超过阈值则标记为热点
 */
@Slf4j
@Component
public class HotKeyDetector {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DynamicCachePromoter dynamicCachePromoter;

    /** Redis key前缀：热点key的SortedSet */
    private static final String HOT_KEY_ZSET_PREFIX = "hot:key:access:";

    /** 热点key池的Redis key */
    private static final String HOT_KEY_POOL = "hot:key:pool";

    /** 时间窗口大小（秒）：统计多久内的访问 */
    private static final long WINDOW_SIZE_SECONDS = 60;

    /** 热点阈值：窗口内访问次数超过此值则标记为热点 */
    private static final int HOT_THRESHOLD = 10;

    /** 热点key的本地缓存TTL（秒） */
    private static final long HOT_KEY_LOCAL_TTL_SECONDS = 300;

    /** 本地缓存的热点key集合 */
    private final ConcurrentHashMap<String, Long> localHotKeys = new ConcurrentHashMap<>();

    /**
     * 记录一次缓存访问
     * 在滑动窗口中增加访问次数
     *
     * @param cacheName 缓存名称
     * @param cacheKey 缓存key
     */
    public void recordAccess(String cacheName, String cacheKey) {
        try {
            String zsetKey = HOT_KEY_ZSET_PREFIX + cacheName;
            String fullKey = cacheName + ":" + cacheKey;

            // 使用时间戳作为score，统计窗口内的访问次数
            long now = System.currentTimeMillis() / 1000;
            long windowStart = now - WINDOW_SIZE_SECONDS;

            // 先删除窗口外的老数据
            redisTemplate.opsForZSet().removeRangeByScore(zsetKey, 0, windowStart);

            // 增加访问次数
            redisTemplate.opsForZSet().incrementScore(zsetKey, fullKey, 1);

            // 设置过期时间，防止数据一直积累
            redisTemplate.expire(zsetKey, WINDOW_SIZE_SECONDS * 2, java.util.concurrent.TimeUnit.SECONDS);

        } catch (Exception e) {
            log.warn("记录热点key失败: cacheName={}, cacheKey={}, error={}", cacheName, cacheKey, e.getMessage());
        }
    }

    /**
     * 判断某个key是否为本地缓存的热点key
     */
    public boolean isLocalHotKey(String cacheName, String cacheKey) {
        String fullKey = cacheName + ":" + cacheKey;
        return localHotKeys.containsKey(fullKey);
    }

    /**
     * 定时任务：扫描热点key并提升到本地缓存
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60000)
    public void scanAndPromoteHotKeys() {
        try {
            log.debug("【热点key扫描】开始扫描...");

            // 获取所有缓存名称的热点key
            scanHotKeysForCache("hotels");
            scanHotKeysForCache("roomTypes");

            log.debug("【热点key扫描】扫描完成，本地热点key数量={}", localHotKeys.size());

        } catch (Exception e) {
            log.error("【热点key扫描】扫描失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 扫描某个缓存的热点key
     */
    private void scanHotKeysForCache(String cacheName) {
        String zsetKey = HOT_KEY_ZSET_PREFIX + cacheName;

        // 获取窗口内的所有key及其访问次数
        long now = System.currentTimeMillis() / 1000;
        long windowStart = now - WINDOW_SIZE_SECONDS;

        // 删除窗口外的数据
        redisTemplate.opsForZSet().removeRangeByScore(zsetKey, 0, windowStart);

        // 获取窗口内的所有key，按分数（访问次数）倒序
        Set<ZSetOperations.TypedTuple<Object>> hotKeys =
                redisTemplate.opsForZSet().reverseRangeWithScores(zsetKey, 0, -1);

        if (hotKeys == null || hotKeys.isEmpty()) {
            return;
        }

        for (ZSetOperations.TypedTuple<Object> tuple : hotKeys) {
            String fullKey = (String) tuple.getValue();
            Double score = tuple.getScore();

            if (fullKey == null || score == null) {
                continue;
            }

            // 判断是否超过热点阈值
            if (score >= HOT_THRESHOLD) {
                // 标记为本地热点key
                localHotKeys.put(fullKey, System.currentTimeMillis() + HOT_KEY_LOCAL_TTL_SECONDS * 1000);
                log.info("【热点key识别】发现热点key: key={}, 访问次数={}", fullKey, score.intValue());

                // 通知动态缓存提升器将热点key提升到本地缓存
                dynamicCachePromoter.promoteToLocalCache(cacheName, fullKey);
            }
        }

        // 清理过期的本地热点key
        long nowMs = System.currentTimeMillis();
        localHotKeys.entrySet().removeIf(entry -> entry.getValue() < nowMs);
    }

    /**
     * 获取当前热点key统计信息
     */
    public String getHotKeyStats() {
        return String.format("本地热点key数量=%d, Redis热点key正在扫描中...",
                localHotKeys.size());
    }
}
