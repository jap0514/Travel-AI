package com.travel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 三级缓存配置
 * 第一层：Caffeine（本地堆内缓存）
 * 第二层：Redis（分布式缓存）
 * 第三层：MySQL（持久化）
 */
@Configuration
@EnableCaching
public class TieredCacheConfig {

    /** ==================== 第一层：Caffeine 本地缓存 ==================== */

    public static final String CAFFEINE_CACHE_MANAGER = "caffeineCacheManager";
    public static final String CAFFEINE_CACHE_HOTELS = "hotels";
    public static final String CAFFEINE_CACHE_ROOM_TYPES = "roomTypes";

    @Bean(CAFFEINE_CACHE_MANAGER)
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(100)          // 初始容量100
                .maximumSize(1000)            // 最大1000条
                .expireAfterWrite(5, TimeUnit.MINUTES)  // 写入后5分钟过期
                .recordStats());              // 开启统计
        return cacheManager;
    }

    /** ==================== 第二层：Redis 分布式缓存 ==================== */

    public static final String REDIS_CACHE_MANAGER = "redisCacheManager";

    @Bean(REDIS_CACHE_MANAGER)
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // 创建支持 Java 8 日期时间 的 ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))  // 默认30分钟
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)))
                .disableCachingNullValues();  // 不缓存null值

        // 为不同缓存设置不同的TTL
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 酒店列表：30分钟
        cacheConfigurations.put("hotels", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        // 房型列表：30分钟
        cacheConfigurations.put("roomTypes", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        // 用户会话：60分钟
        cacheConfigurations.put("sessions", defaultConfig.entryTtl(Duration.ofMinutes(60)));
        // 用户Profile：30分钟
        cacheConfigurations.put("userProfile", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    /** ==================== 缓存 Key 前缀常量 ==================== */

    public static final String CACHE_KEY_HOTEL_CITY = "hotel:city:";
    public static final String CACHE_KEY_ROOM_TYPE = "hotel:roomType:";
    public static final String CACHE_KEY_SESSION = "session:";
    public static final String CACHE_KEY_USER_PROFILE = "user:profile:";
}
