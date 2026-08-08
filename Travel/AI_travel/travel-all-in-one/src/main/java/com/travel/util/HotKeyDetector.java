package com.travel.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 热点数据识别器
 * 使用滑动窗口算法识别热点key
 *
 * 原理：
 * 1. 每次缓存访问，ZADD 写入一条记录，member = cacheKey，score = 时间戳
 * 2. 定时任务用 ZRANGEBYSCORE 获取窗口内的所有记录
 * 3. 统计每个 cacheKey 出现次数，超过阈值则标记为热点
 */
@Slf4j
@Component
public class HotKeyDetector {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DynamicCachePromoter dynamicCachePromoter;

    /** Redis key前缀：热点key的ZSet */
    private static final String HOT_KEY_ZSET_PREFIX = "hot:key:access:";

    /** 时间窗口大小（秒）：统计多久内的访问 */
    private static final long WINDOW_SIZE_SECONDS = 60;

    /** 热点阈值：窗口内访问次数超过此值则标记为热点 */
    private static final int HOT_THRESHOLD = 10;

    /** 热点key的本地缓存TTL（秒） */
    private static final long HOT_KEY_LOCAL_TTL_SECONDS = 300;

    /** 本地缓存的热点key集合 */
    private final ConcurrentHashMap<String, Long> localHotKeys = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("【热点key检测】初始化完成，窗口大小={}秒，热点阈值={}", WINDOW_SIZE_SECONDS, HOT_THRESHOLD);
    }

    /**
     * 记录一次缓存访问
     * 使用 ZSet，member = cacheKey:timestamp:random，score = 时间戳
     * 加入随机数保证同一秒内多次访问不会覆盖
     *
     * @param cacheName 缓存名称
     * @param cacheKey 缓存key
     */
    public void recordAccess(String cacheName, String cacheKey) {
        try {
            String zsetKey = HOT_KEY_ZSET_PREFIX + cacheName;
            // member 格式：cacheName:cacheKey:timestamp:random
            // 用时间戳+随机数确保唯一性，同一秒内多次访问不会覆盖
            long nowSeconds = System.currentTimeMillis() / 1000;
            long random = System.nanoTime();
            String member = cacheName + ":" + cacheKey + ":" + nowSeconds + ":" + random;

            // ZADD：score = 时间戳（秒），用于按时间窗口筛选
            redisTemplate.opsForZSet().add(zsetKey, member, nowSeconds);

            // 设置过期时间，自动清理窗口外的数据
            redisTemplate.expire(zsetKey, WINDOW_SIZE_SECONDS * 2, TimeUnit.SECONDS);

            log.debug("【热点key检测】记录访问: key={}", cacheName + ":" + cacheKey);

        } catch (Exception e) {
            log.warn("记录热点key失败: cacheName={}, cacheKey={}, error={}", cacheName, cacheKey, e.getMessage());
        }
    }

    /**
     * 判断某个key是否为本地缓存的热点key
     */
    public boolean isLocalHotKey(String cacheName, String cacheKey) {
        String fullKey = cacheName + ":" + cacheKey;
        // 检查是否过期
        Long expiration = localHotKeys.get(fullKey);
        if (expiration != null && expiration < System.currentTimeMillis()) {
            localHotKeys.remove(fullKey);
            return false;
        }
        return localHotKeys.containsKey(fullKey);
    }

    /**
     * 定时任务：每10秒执行，清理过期数据 + 识别热点
     */
    @Scheduled(fixedRate = 10000)
    public void scanAndPromoteHotKeys() {
        try {
            // 扫描热点缓存的热点key
            scanHotKeysForCache("hotels");
            scanHotKeysForCache("roomTypes");

            // 清理过期的本地热点key
            long nowMs = System.currentTimeMillis();
            localHotKeys.entrySet().removeIf(entry -> entry.getValue() < nowMs);

        } catch (Exception e) {
            log.error("【热点key扫描】扫描失败: {}", e.getMessage());
        }
    }

    /**
     * 扫描某个缓存的热点key
     * 使用 ZRANGEBYSCORE 获取窗口内的所有访问记录，统计每个 key 的出现次数
     */
    private void scanHotKeysForCache(String cacheName) {
        String zsetKey = HOT_KEY_ZSET_PREFIX + cacheName;
        long nowSeconds = System.currentTimeMillis() / 1000;
        long windowStart = nowSeconds - WINDOW_SIZE_SECONDS;

        // ZRANGEBYSCORE：获取窗口内的所有记录（score 在 windowStart ~ nowSeconds 之间）
        Set<Object> records = redisTemplate.opsForZSet().rangeByScore(zsetKey, windowStart, nowSeconds);

        if (records == null || records.isEmpty()) {
            return;
        }

        log.debug("【热点key扫描】cache={}, 窗口内记录数={}", cacheName, records.size());

        // 统计每个 key 的访问次数
        // member 格式：cacheName:cacheKey:timestamp:random
        Map<String, Long> keyCountMap = new HashMap<>();
        for (Object record : records) {
            String member = (String) record;
            // 提取 cacheName:cacheKey（前两个部分）
            String[] parts = member.split(":");
            if (parts.length >= 2) {
                String fullKey = parts[0] + ":" + parts[1];
                keyCountMap.merge(fullKey, 1L, Long::sum);
            }
        }

        // 找出超过热点的 key
        for (Map.Entry<String, Long> entry : keyCountMap.entrySet()) {
            String fullKey = entry.getKey();
            long count = entry.getValue();

            if (count >= HOT_THRESHOLD) {
                // 标记为热点key（带过期时间）
                long expiration = System.currentTimeMillis() + HOT_KEY_LOCAL_TTL_SECONDS * 1000;
                if (!localHotKeys.containsKey(fullKey)) {
                    localHotKeys.put(fullKey, expiration);
                    log.info("【热点key识别】发现热点key: key={}, 窗口内访问次数={}", fullKey, count);

                    // 通知动态缓存提升器将热点key提升到本地缓存
                    if (dynamicCachePromoter != null) {
                        dynamicCachePromoter.promoteToLocalCache(cacheName, fullKey);
                    }
                }
            }
        }

        // 删除窗口外的老数据（score < windowStart）
        redisTemplate.opsForZSet().removeRangeByScore(zsetKey, 0, windowStart);
    }

    /**
     * 获取当前热点key统计信息
     */
    public String getHotKeyStats() {
        long now = System.currentTimeMillis();
        int activeCount = (int) localHotKeys.entrySet().stream()
                .filter(entry -> entry.getValue() >= now)
                .count();
        return String.format("本地热点key数量=%d, 活跃=%d", localHotKeys.size(), activeCount);
    }
}
