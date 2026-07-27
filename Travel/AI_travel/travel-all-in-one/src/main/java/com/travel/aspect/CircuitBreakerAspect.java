package com.travel.aspect;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DeadefaultRules;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreaker;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerRegistry;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.strategy.SlowRatioCircuitBreakerStrategy;
import com.travel.annotation.CircuitBreaker;
import com.travel.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sentinel 熔断器切面
 *
 * 核心逻辑：
 * 1. 解析 @CircuitBreaker 注解配置
 * 2. 为每个熔断器注册 Sentinel 降级规则
 * 3. 通过 SphU.entry() 进入资源
 * 4. 记录异常到 Sentinel（触发熔断计算）
 * 5. 熔断触发时调用降级方法
 */
@Aspect
@Component
@Slf4j
public class CircuitBreakerAspect {

    @Autowired
    private TraceIdUtil traceIdUtil;

    @Value("${travel.python-api-timeout:3000}")
    private int pythonApiTimeout;

    /** 已注册的熔断器缓存 */
    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("【熔断器】初始化完成，默认超时时间={}ms", pythonApiTimeout);
    }

    /**
     * 环绕通知：拦截所有带@CircuitBreaker注解的方法
     */
    @Around("@annotation(com.travel.annotation.CircuitBreaker)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        CircuitBreaker annotation = method.getAnnotation(CircuitBreaker.class);

        String resourceName = annotation.value();
        String traceId = traceIdUtil.getTraceId();

        log.debug("【熔断器】开始检查，traceId={}, resource={}", traceId, resourceName);

        // 确保熔断规则已注册
        registerDegradeRuleIfNeeded(resourceName, annotation);

        try {
            // 进入资源（会检查熔断状态）
            Entry entry = SphU.entry(resourceName, EntryType.OUT);

            // 执行目标方法
            Object result = joinPoint.proceed();

            // 正常结束，记录成功
            if (entry != null) {
                entry.exit();
            }

            return result;

        } catch (BlockException e) {
            // 熔断触发，执行降级方法
            log.warn("【熔断器】【熔断触发】traceId={}, resource={}", traceId, resourceName);
            return executeFallback(joinPoint, annotation.fallbackMethod(), e);

        } catch (Exception e) {
            // 业务异常，记录到 Sentinel（触发熔断计算）
            Tracer.trace(e);
            log.warn("【熔断器】【业务异常】traceId={}, resource={}, error={}", traceId, resourceName, e.getMessage());
            throw e;
        }
    }

    /**
     * 注册熔断规则（如果尚未注册）
     */
    private void registerDegradeRuleIfNeeded(String resourceName, CircuitBreaker annotation) {
        if (circuitBreakers.containsKey(resourceName)) {
            return;
        }

        // 创建熔断规则
        DegradeRule rule = new DegradeRule(resourceName)
                .setGrade(com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule.SLOW_REQUEST_RATIO)
                .setCount(annotation.failureRatioThreshold() / 100.0)
                .setSlowRequestRatioThreshold(annotation.slowCallRatioThreshold() / 100.0)
                .setMinRequestAmount(annotation.minimumNumberOfCalls())
                .setStatIntervalMs(annotation.waitDurationInOpenState() * 1000)
                .setRecoverTimeoutSec(annotation.waitDurationInOpenState())
                .setMaxAllowedStateepingDurationSec(annotation.waitDurationInOpenState());

        // 加载规则
        DegradeRuleManager.loadRules(java.util.Collections.singletonList(rule));

        // 获取或创建熔断器实例
        CircuitBreaker breaker = CircuitBreakerRegistry.of(rule);
        circuitBreakers.put(resourceName, breaker);

        log.info("【熔断器】规则已注册，resource={}, slowCallRatio={}%, slowCallDuration={}s, failureRatio={}%, waitDuration={}s",
                resourceName,
                annotation.slowCallRatioThreshold(),
                annotation.slowCallDurationThreshold(),
                annotation.failureRatioThreshold(),
                annotation.waitDurationInOpenState());
    }

    /**
     * 执行降级方法
     */
    private Object executeFallback(ProceedingJoinPoint joinPoint, String fallbackMethod, Exception originalException) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Method fallback = findFallbackMethod(joinPoint.getTarget(), method, fallbackMethod, originalException);

        if (fallback == null) {
            log.error("【熔断器】未找到降级方法: {}", fallbackMethod);
            throw originalException;
        }

        try {
            // 构建降级方法参数
            Object[] args = buildFallbackArgs(joinPoint.getArgs(), method, fallback, originalException);
            return fallback.invoke(joinPoint.getTarget(), args);
        } catch (UndeclaredThrowableException e) {
            // 降级方法本身抛出的异常
            throw e.getUndeclaredThrowable();
        }
    }

    /**
     * 查找降级方法
     */
    private Method findFallbackMethod(Object target, Method originalMethod, String fallbackMethodName, Exception originalException) {
        Class<?> clazz = target.getClass();

        // 查找同名降级方法（参数兼容）
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(fallbackMethodName)) {
                return m;
            }
        }

        // 降级方法可能在父类中
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null) {
            for (Method m : superClass.getDeclaredMethods()) {
                if (m.getName().equals(fallbackMethodName)) {
                    return m;
                }
            }
        }

        return null;
    }

    /**
     * 构建降级方法参数
     * 降级方法可以额外接收一个 Throwable 参数接收原始异常
     */
    private Object[] buildFallbackArgs(Object[] originalArgs, Method originalMethod, Method fallbackMethod, Exception originalException) {
        Class<?>[] fallbackParamTypes = fallbackMethod.getParameterTypes();

        if (fallbackParamTypes.length == 0) {
            return new Object[0];
        }

        // 降级方法参数数量与原方法相同，且最后一个参数是 Throwable
        if (fallbackParamTypes.length == originalMethod.getParameterCount() + 1
                && fallbackParamTypes[fallbackParamTypes.length - 1].isAssignableFrom(originalException.getClass())) {
            Object[] args = new Object[fallbackParamTypes.length];
            System.arraycopy(originalArgs, 0, args, 0, originalArgs.length);
            args[fallbackParamTypes.length - 1] = originalException;
            return args;
        }

        // 降级方法参数与原方法相同
        if (fallbackParamTypes.length == originalMethod.getParameterCount()) {
            return originalArgs;
        }

        // 降级方法只有一个 Throwable 参数
        if (fallbackParamTypes.length == 1 && fallbackParamTypes[0].isAssignableFrom(originalException.getClass())) {
            return new Object[]{originalException};
        }

        return originalArgs;
    }
}
