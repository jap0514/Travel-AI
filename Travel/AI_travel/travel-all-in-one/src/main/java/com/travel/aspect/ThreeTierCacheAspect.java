package com.travel.aspect;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.travel.annotation.ThreeTierCache;
import com.travel.util.BloomFilterUtil;
import com.travel.util.CacheAvalancheUtil;
import com.travel.util.CachePenetrationUtil;
import com.travel.util.DynamicCachePromoter;
import com.travel.util.HotKeyDetector;
import com.travel.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 三级缓存AOP切面
 * 实现：Caffeine(本地) → Redis(分布式) → MySQL
 * 防护：缓存穿透(布隆过滤器+空值) | 缓存击穿(分布式锁) | 缓存雪崩(TTL随机偏移)
 * 热点：热点数据识别 → 提升到专用热点缓存
 */
@Aspect
@Component
@Slf4j
@Order(100)
public class ThreeTierCacheAspect {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private BloomFilterUtil bloomFilterUtil;

    @Autowired
    private CachePenetrationUtil cachePenetrationUtil;

    @Autowired
    private CacheAvalancheUtil cacheAvalancheUtil;

    @Autowired
    private TraceIdUtil traceIdUtil;

    @Autowired(required = false)
    private HotKeyDetector hotKeyDetector;

    @Autowired(required = false)
    private DynamicCachePromoter dynamicCachePromoter;

    /** SpEL表达式解析器 */
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /** 本地缓存集合（按cacheName隔离） */
    private final ConcurrentHashMap<String, Cache<String, Object>> localCaches = new ConcurrentHashMap<>();

    /**
     * 环绕通知：拦截所有带@ThreeTierCache注解的方法
     */
    @Around("@annotation(com.travel.annotation.ThreeTierCache)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        ThreeTierCache annotation = method.getAnnotation(ThreeTierCache.class);

        // 解析SpEL表达式，获取实际的cacheKey
        String cacheKey = resolveSpEL(annotation.key(), joinPoint);
        String cacheName = annotation.cacheName();
        String traceId = traceIdUtil.getTraceId();

        // 组合完整缓存key
        String fullKey = cacheName + ":" + cacheKey;

        log.debug("【三级缓存】AOP拦截，traceId={}, cacheName={}, cacheKey={}", traceId, cacheName, cacheKey);

        try {
            // ==================== L0: 热点数据本地缓存（最高优先级）====================
            Object hotCacheResult = getFromHotCache(cacheName, cacheKey);
            if (hotCacheResult != null) {
                log.info("【三级缓存】【L0-热点缓存命中】traceId={}, key={}", traceId, fullKey);
                recordAccessForHotDetection(cacheName, cacheKey);  // 记录访问用于热点分析
                if (isEmptyResult(hotCacheResult)) {
                    return getEmptyResult(method);
                }
                return hotCacheResult;
            }

            // ==================== L1: Caffeine本地缓存 ====================
            Object caffeineResult = getFromLocalCache(cacheName, fullKey);
            if (caffeineResult != null) {
                log.info("【三级缓存】【L1-Caffeine命中】traceId={}, key={}", traceId, fullKey);
                recordAccessForHotDetection(cacheName, cacheKey);  // 记录访问用于热点分析
                if (isEmptyResult(caffeineResult)) {
                    return getEmptyResult(method);
                }
                return caffeineResult;
            }

            // ==================== 防护1: 布隆过滤器 ====================
            if (annotation.useBloomFilter() && !bloomFilterUtil.mightContain(cacheName, fullKey)) {
                log.info("【三级缓存】【布隆过滤器拦截】traceId={}, key={}，一定不存在，直接返回空", traceId, fullKey);
                return getEmptyResult(method);
            }

            // ==================== 防护2: 空值缓存检查 ====================
            if (annotation.useNullValueCache() && cachePenetrationUtil.isNullValue(fullKey)) {
                log.info("【三级缓存】【空值缓存命中】traceId={}, key={}", traceId, fullKey);
                recordAccessForHotDetection(cacheName, cacheKey);  // 记录访问用于热点分析
                return getEmptyResult(method);
            }

            // ==================== L2: Redis分布式缓存 ====================
            Object redisResult = getFromRedisCache(cacheName, fullKey);
            if (redisResult != null) {
                log.info("【三级缓存】【L2-Redis命中】traceId={}, key={}", traceId, fullKey);
                // 回填L1-Caffeine
                putToLocalCache(cacheName, fullKey, redisResult, annotation.localTtlMinutes());
                // 检查是否为热点key，如果是则提升到热点缓存
                promoteToHotCacheIfNeeded(cacheName, cacheKey, redisResult);
                recordAccessForHotDetection(cacheName, cacheKey);  // 记录访问用于热点分析
                return redisResult;
            }

            // ==================== L3: MySQL + 防护3: 分布式锁防击穿 ====================
            return queryWithLock(joinPoint, annotation, cacheName, cacheKey, fullKey, traceId);

        } catch (Exception e) {
            log.error("【三级缓存】AOP执行异常，traceId={}, key={}, error={}", traceId, fullKey, e.getMessage(), e);
            // 降级：直接查DB
            return joinPoint.proceed();
        }
    }

    /**
     * 使用分布式锁查MySQL并回填缓存
     */
    private Object queryWithLock(ProceedingJoinPoint joinPoint, ThreeTierCache annotation,
                                  String cacheName, String cacheKey, String fullKey, String traceId) throws Throwable {

        String lockKey = "lock:" + fullKey.replace(":", "_");
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁（等待N秒，锁自动N秒过期）
            boolean locked = lock.tryLock(annotation.lockWaitSeconds(), annotation.lockExpireSeconds(), TimeUnit.SECONDS);

            if (!locked) {
                // 没抢到锁，等待一下让抢到锁的请求回填
                log.info("【三级缓存】【等待回填】traceId={}, key={}", traceId, fullKey);
                Thread.sleep(200);

                // 再查一次Redis
                Object redisResult = getFromRedisCache(cacheName, fullKey);
                if (redisResult != null) {
                    putToLocalCache(cacheName, fullKey, redisResult, annotation.localTtlMinutes());
                    recordAccessForHotDetection(cacheName, cacheKey);
                    return redisResult;
                }

                // 还没缓存，降级查DB
                log.warn("【三级缓存】【降级查DB】traceId={}, key={}", traceId, fullKey);
                return joinPoint.proceed();
            }

            // 抢到锁，查MySQL
            Object dbResult = joinPoint.proceed();

            // 处理空结果
            if (isEmptyResult(dbResult)) {
                if (annotation.useNullValueCache()) {
                    cachePenetrationUtil.setNullValue(fullKey);
                    log.info("【三级缓存】【缓存穿透防护】traceId={}, key={}，设置空值缓存", traceId, fullKey);
                }
                recordAccessForHotDetection(cacheName, cacheKey);
                return dbResult;
            }

            // ==================== 回填缓存 ====================
            // 计算带随机偏移的TTL
            long redisTtlWithJitter = cacheAvalancheUtil.calculateTtlWithJitter(
                    annotation.redisTtlMinutes(), annotation.redisTtlOffsetMinutes());

            // 回填L2-Redis
            putToRedisCache(cacheName, fullKey, dbResult, redisTtlWithJitter);

            // 回填L1-Caffeine
            putToLocalCache(cacheName, fullKey, dbResult, annotation.localTtlMinutes());

            // 添加到布隆过滤器（因为DB确实有这条数据）
            if (annotation.useBloomFilter()) {
                bloomFilterUtil.put(cacheName, fullKey);
            }

            // 记录访问用于热点分析
            recordAccessForHotDetection(cacheName, cacheKey);

            log.info("【三级缓存】【回填完成】traceId={}, key={}, redisTtl={}min", traceId, fullKey, redisTtlWithJitter / 60);

            return dbResult;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("【三级缓存】【获取锁被中断】traceId={}, key={}", traceId, fullKey);
            // 直接查DB
            return joinPoint.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 记录缓存访问，用于热点数据分析
     */
    private void recordAccessForHotDetection(String cacheName, String cacheKey) {
        if (hotKeyDetector != null) {
            try {
                hotKeyDetector.recordAccess(cacheName, cacheKey);
            } catch (Exception e) {
                log.warn("记录热点key失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 如果是热点key，则提升到热点缓存
     */
    private void promoteToHotCacheIfNeeded(String cacheName, String cacheKey, Object data) {
        if (dynamicCachePromoter != null && hotKeyDetector != null) {
            try {
                if (hotKeyDetector.isLocalHotKey(cacheName, cacheKey)) {
                    dynamicCachePromoter.promoteToLocalCache(cacheName, cacheName + ":" + cacheKey);
                    log.info("【动态缓存】热点数据已提升: key={}", cacheName + ":" + cacheKey);
                }
            } catch (Exception e) {
                log.warn("提升热点缓存失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 从热点缓存获取
     */
    private Object getFromHotCache(String cacheName, String cacheKey) {
        if (dynamicCachePromoter == null) {
            return null;
        }
        return dynamicCachePromoter.getFromHotCache(cacheName, cacheKey);
    }

    /**
     * 解析SpEL表达式
     */
    private String resolveSpEL(String spEL, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (paramNames == null || paramNames.length == 0) {
            return spEL;
        }

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        if (spEL.startsWith("#") && !spEL.contains(" ")) {
            Expression expression = expressionParser.parseExpression(spEL);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : spEL;
        }

        return spEL;
    }

    /**
     * 从本地缓存获取
     */
    private Object getFromLocalCache(String cacheName, String key) {
        Cache<String, Object> cache = localCaches.get(cacheName);
        if (cache == null) {
            return null;
        }
        return cache.getIfPresent(key);
    }

    /**
     * 写入本地缓存
     */
    private void putToLocalCache(String cacheName, String key, Object value, int ttlMinutes) {
        Cache<String, Object> cache = localCaches.computeIfAbsent(cacheName, name ->
                Caffeine.newBuilder()
                        .initialCapacity(100)
                        .maximumSize(1000)
                        .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                        .recordStats()
                        .build()
        );
        cache.put(key, value);
    }

    /**
     * 从Redis缓存获取
     */
    private Object getFromRedisCache(String cacheName, String key) {
        try {
            CacheManager cacheManager = com.travel.context.SpringContextHolder.getCacheManager();
            if (cacheManager == null) {
                return null;
            }
            org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);
            if (springCache == null) {
                return null;
            }
            org.springframework.cache.Cache.ValueWrapper wrapper = springCache.get(key);
            return wrapper != null ? wrapper.get() : null;
        } catch (Exception e) {
            log.warn("【三级缓存】Redis查询失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 写入Redis缓存
     */
    private void putToRedisCache(String cacheName, String key, Object value, long ttlSeconds) {
        try {
            CacheManager cacheManager = com.travel.context.SpringContextHolder.getCacheManager();
            if (cacheManager == null) {
                return;
            }
            org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);
            if (springCache == null) {
                return;
            }
            springCache.put(key, value);
        } catch (Exception e) {
            log.warn("【三级缓存】Redis写入失败: {}", e.getMessage());
        }
    }

    /**
     * 判断结果是否为空
     */
    private boolean isEmptyResult(Object result) {
        if (result == null) {
            return true;
        }
        if (result instanceof java.util.List) {
            return ((java.util.List<?>) result).isEmpty();
        }
        if (result instanceof java.util.Collection) {
            return ((java.util.Collection<?>) result).isEmpty();
        }
        return false;
    }

    /**
     * 获取空结果
     */
    private Object getEmptyResult(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType.isAssignableFrom(java.util.List.class)) {
            return new java.util.ArrayList<>();
        }
        return null;
    }
}
