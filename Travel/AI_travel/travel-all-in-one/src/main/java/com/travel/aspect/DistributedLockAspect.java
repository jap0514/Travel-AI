package com.travel.aspect;

import com.travel.annotation.DistributedLock;
import com.travel.exception.BusinessException;
import com.travel.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁切面
 *
 * 流程：
 * 1. 解析 SpEL 表达式获取锁的 key
 * 2. 尝试获取分布式锁
 * 3. 获取成功 → 执行目标方法 → 释放锁
 * 4. 获取失败 → 等待或直接抛出异常
 */
@Aspect
@Component
@Slf4j
public class DistributedLockAspect {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private TraceIdUtil traceIdUtil;

    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(com.travel.annotation.DistributedLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock lockAnnotation = method.getAnnotation(DistributedLock.class);

        // 解析 SpEL 获取锁的 key
        String lockKey = resolveSpEL(lockAnnotation.key(), joinPoint);
        String traceId = traceIdUtil.getTraceId();

        if (lockKey == null || lockKey.isBlank()) {
            throw new BusinessException(400, "锁 key 不能为空");
        }

        // 确保 key 有前缀
        if (!lockKey.startsWith("lock:")) {
            lockKey = "lock:" + lockKey;
        }

        log.debug("【分布式锁】尝试获取锁，traceId={}, key={}", traceId, lockKey);

        // 获取锁
        RLock lock = lockAnnotation.fair()
                ? redissonClient.getFairLock(lockKey)
                : redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            // 尝试获取锁
            locked = lock.tryLock(
                    lockAnnotation.waitTime(),
                    lockAnnotation.leaseTime(),
                    TimeUnit.SECONDS
            );

            if (!locked) {
                log.warn("【分布式锁】【获取失败】traceId={}, key={}", traceId, lockKey);
                throw new BusinessException(429, "系统繁忙，请稍后重试");
            }

            log.debug("【分布式锁】【获取成功】traceId={}, key={}", traceId, lockKey);

            // 执行目标方法
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("【分布式锁】【获取被中断】traceId={}, key={}", traceId, lockKey);
            throw new BusinessException(500, "系统繁忙，请稍后重试");

        } finally {
            // 释放锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("【分布式锁】【已释放】traceId={}, key={}", traceId, lockKey);
            }
        }
    }

    /**
     * 解析 SpEL 表达式
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
            Expression expression = parser.parseExpression(spEL);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : spEL;
        }

        return spEL;
    }
}
