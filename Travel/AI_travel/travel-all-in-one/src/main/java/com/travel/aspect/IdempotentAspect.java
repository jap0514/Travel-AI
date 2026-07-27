package com.travel.aspect;

import com.travel.annotation.Idempotent;
import com.travel.exception.BusinessException;
import com.travel.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 幂等性切面（基于 Redis Token）
 *
 * 流程：
 * 1. 解析 SpEL 表达式获取 token 值
 * 2. 检查 Redis 中是否已存在该 token
 * 3. 不存在 → 存入 Redis（设置过期时间）→ 执行目标方法 → 成功后删除 token（可选）
 * 4. 存在 → 直接返回幂等提示
 */
@Aspect
@Component
@Slf4j
public class IdempotentAspect {

    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:token:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TraceIdUtil traceIdUtil;

    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(com.travel.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        // 解析 SpEL 表达式获取 token
        String token = resolveSpEL(idempotent.key(), joinPoint);
        String traceId = traceIdUtil.getTraceId();

        if (token == null || token.isBlank()) {
            log.warn("【幂等性】Token 为空，traceId={}", traceId);
            throw new BusinessException(400, "Token 不能为空");
        }

        String redisKey = IDEMPOTENT_KEY_PREFIX + token;

        log.debug("【幂等性】检查 Token，traceId={}, key={}", traceId, redisKey);

        try {
            // 尝试存入 Redis（SETNX 语义）
            // setIfAbsent 返回 true 表示不存在（可以执行），false 表示已存在（重复提交）
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                    redisKey,
                    "1",
                    idempotent.expireTime(),
                    java.util.concurrent.TimeUnit.SECONDS
            );

            if (success == null || !success) {
                // Token 已存在，说明是重复提交
                log.warn("【幂等性】【重复提交拦截】traceId={}, key={}", traceId, redisKey);
                throw new BusinessException(429, idempotent.message());
            }

            // Token 不存在，继续执行目标方法
            log.debug("【幂等性】【Token 通过】traceId={}, key={}", traceId, redisKey);

            Object result = joinPoint.proceed();

            // 业务执行成功后删除 Token（可选）
            if (idempotent.deleteOnSuccess()) {
                redisTemplate.delete(redisKey);
                log.debug("【幂等性】【Token 已删除】traceId={}, key={}", traceId, redisKey);
            }

            return result;

        } catch (BusinessException e) {
            // 业务异常直接抛出
            throw e;
        } catch (Exception e) {
            log.error("【幂等性】异常，traceId={}, error={}", traceId, e.getMessage());
            throw e;
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
