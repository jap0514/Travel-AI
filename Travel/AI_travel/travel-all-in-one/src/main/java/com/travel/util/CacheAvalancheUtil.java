package com.travel.util;

import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 缓存雪崩防护工具类
 * 通过 TTL 随机偏移防止大量 key 同时过期
 */
@Component
public class CacheAvalancheUtil {

    /** 随机数生成器 */
    private static final Random RANDOM = new Random();

    /**
     * 计算带随机偏移的 TTL
     * 防止大量 key 同时过期导致缓存雪崩
     *
     * @param baseTtl 基础TTL（秒）
     * @param offset  随机偏移范围（秒）
     * @return 实际TTL = baseTtl ± offset
     */
    public long calculateTtlWithJitter(long baseTtl, long offset) {
        // 基础TTL ± 随机偏移
        // 例如：baseTtl=1800, offset=300
        // 实际TTL = 1500~2100 秒
        long jitter = ThreadLocalRandom.current().nextLong(-offset, offset + 1);
        long actualTtl = baseTtl + jitter;

        // 确保TTL不会小于1秒
        return Math.max(1, actualTtl);
    }

    /**
     * 计算 Redis TTL（毫秒）
     */
    public long calculateTtlMillisWithJitter(long baseTtlSeconds, long offsetSeconds) {
        long actualSeconds = calculateTtlWithJitter(baseTtlSeconds, offsetSeconds);
        return actualSeconds * 1000;
    }

    /**
     * 常见缓存的 TTL + 随机偏移
     * 使用示例：calculateTtlWithJitter(1800, 300) // 30分钟 ± 5分钟
     */
    public static class TtlConstants {
        // 酒店列表：30分钟 ± 5分钟
        public static final long HOTEL_LIST_BASE_TTL = 1800;
        public static final long HOTEL_LIST_OFFSET = 300;

        // 房型列表：30分钟 ± 5分钟
        public static final long ROOM_TYPE_BASE_TTL = 1800;
        public static final long ROOM_TYPE_OFFSET = 300;

        // 用户会话：60分钟 ± 10分钟
        public static final long SESSION_BASE_TTL = 3600;
        public static final long SESSION_OFFSET = 600;

        // 用户Profile：30分钟 ± 5分钟
        public static final long USER_PROFILE_BASE_TTL = 1800;
        public static final long USER_PROFILE_OFFSET = 300;

        // 热点数据（永不过期级别）：2小时 ± 10分钟
        public static final long HOT_DATA_BASE_TTL = 7200;
        public static final long HOT_DATA_OFFSET = 600;
    }
}
