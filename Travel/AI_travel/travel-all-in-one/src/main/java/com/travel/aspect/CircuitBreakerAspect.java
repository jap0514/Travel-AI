package com.travel.aspect;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.travel.annotation.CircuitBreaker;
import com.travel.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * Sentinel 熔断器切面（简化版）
 *
 * 当前实现：try-catch 实现基础降级
 * 后续优化：接入 Sentinel Dashboard，实现完整的熔断配置
 *
 * Sentinel Dashboard 方案：
 * 1. 部署 Sentinel Dashboard（Java Web 控制台，端口 8080）
 * 2. Java 应用引入 sentinel-dashboard 依赖
 * 3. 配置 sentinel transporter 连接 Dashboard
 * 4. 在 Dashboard 上动态配置熔断规则（失败率/慢调用比例）
 * 5. 规则实时生效，无需重启应用
 *
 * 熔断原理（Sentinel 官方实现）：
 * 1. 统计接口的响应时间和异常比例
 * 2. 超过阈值触发熔断（直接拒绝或降级）
 * 3. 熔断一段时间后半开，尝试放行一个请求
 * 4. 请求成功则关闭熔断，失败则继续熔断
 */
@Aspect
@Component
@Slf4j
public class CircuitBreakerAspect {

    @Autowired
    private TraceIdUtil traceIdUtil;

    @PostConstruct
    public void init() {
        log.info("【熔断器】初始化完成（简化版，后续可接入 Sentinel Dashboard）");
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

        try {
            // ========== 进入资源（Sentinel 自动统计响应时间和异常）==========
            // TODO: 后续接入 Dashboard 后，这里会由 Sentinel 自动判断是否熔断
            // 简化版：直接执行业务，通过 try-catch 实现降级
            Entry entry = SphU.entry(resourceName, EntryType.OUT);

            // 执行业务
            Object result = joinPoint.proceed();

            // 正常结束
            entry.exit();
            return result;

        } catch (BlockException e) {
            // ========== 熔断触发（Sentinel Dashboard 方案中是自动触发的）==========
            // 简化版：手动降级
            log.warn("【熔断器】【熔断触发】traceId={}, resource={}", traceId, resourceName);
            return executeFallback(joinPoint, annotation.fallbackMethod(), e);

        } catch (Exception e) {
            // ========== 业务异常：记录到 Sentinel（用于 Dashboard 统计）==========
            // 简化版：打印日志
            // TODO: 后续接入 Dashboard 后，Sentinel 会自动统计异常比例
            Tracer.trace(e);
            log.warn("【熔断器】【业务异常】traceId={}, resource={}, error={}",
                    traceId, resourceName, e.getMessage());
            throw e;
        }
    }

    /**
     * 执行降级方法
     */
    private Object executeFallback(ProceedingJoinPoint joinPoint, String fallbackMethod, Exception originalException) throws Throwable {
        Method fallback = findFallbackMethod(joinPoint.getTarget(), fallbackMethod);

        if (fallback == null) {
            log.error("【熔断器】未找到降级方法: {}", fallbackMethod);
            throw originalException;
        }

        try {
            Object[] args = buildFallbackArgs(fallback, originalException);
            return fallback.invoke(joinPoint.getTarget(), args);
        } catch (UndeclaredThrowableException e) {
            throw (Throwable) e.getCause();
        }
    }

    /**
     * 查找降级方法
     */
    private Method findFallbackMethod(Object target, String fallbackMethodName) {
        Class<?> clazz = target.getClass();

        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(fallbackMethodName)) {
                return m;
            }
        }

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
     */
    private Object[] buildFallbackArgs(Method fallbackMethod, Exception originalException) {
        Class<?>[] paramTypes = fallbackMethod.getParameterTypes();

        // 无参数
        if (paramTypes.length == 0) {
            return new Object[0];
        }

        // 只有一个 Throwable 参数
        if (paramTypes.length == 1 && Throwable.class.isAssignableFrom(paramTypes[0])) {
            return new Object[]{originalException};
        }

        // 其他情况返回空
        return new Object[0];
    }
}
