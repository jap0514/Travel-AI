package com.travel.aspect;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.travel.annotation.RateLimiter;
import com.travel.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * Sentinel 限流切面
 *
 * 核心逻辑：
 * 1. 解析 @RateLimiter 注解配置
 * 2. 根据 limitType 确定限流维度（接口/用户/IP）
 * 3. 通过 SphU.entry() 尝试获取令牌
 * 4. 获取成功则执行业务，获取失败则抛出 BlockException
 * 5. 由 GlobalExceptionHandler 处理 BlockException
 */
@Aspect
@Component
@Slf4j
public class RateLimiterAspect {

    @Autowired
    private TraceIdUtil traceIdUtil;

    /**
     * 环绕通知：拦截所有带@RateLimiter注解的方法
     */
    @Around("@annotation(com.travel.annotation.RateLimiter)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimiter rateLimiter = method.getAnnotation(RateLimiter.class);

        // 获取限流资源名
        String resourceName = getResourceName(rateLimiter, joinPoint);
        String traceId = traceIdUtil.getTraceId();

        log.debug("【限流】开始限流检查，traceId={}, resource={}, count={}, timeout={}ms",
                traceId, resourceName, rateLimiter.count(), rateLimiter.timeout());

        try {
            // 使用 Sentinel 门面模式进入资源
            // entryType = EntryType.IN 表示入站流量限流
            // trafficType = inbound
            Entry entry = SphU.entry(resourceName, EntryType.IN,
                    rateLimiter.count(), rateLimiter.timeout());

            // 获取令牌成功，执行业务
            Object result = joinPoint.proceed();

            // 业务执行完毕，记录结束
            if (entry != null) {
                entry.exit();
            }

            return result;

        } catch (BlockException e) {
            // 限流触发，记录日志并抛出异常
            log.warn("【限流触发】traceId={}, resource={}, count={}",
                    traceId, resourceName, rateLimiter.count());

            // 抛出特定异常，由 GlobalExceptionHandler 处理
            throw e;
        }
    }

    /**
     * 获取限流资源名
     * 根据 limitType 确定维度和资源标识
     */
    private String getResourceName(RateLimiter rateLimiter, ProceedingJoinPoint joinPoint) {
        // 如果自定义了资源名，直接使用
        if (StringUtils.isNotBlank(rateLimiter.resourceName())) {
            return rateLimiter.resourceName();
        }

        // 默认使用方法全限定名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String baseResource = className + ":" + methodName;

        switch (rateLimiter.limitType()) {
            case USER:
                // 用户维度限流
                String userId = getCurrentUserId();
                return baseResource + ":user:" + (StringUtils.isNotBlank(userId) ? userId : "anonymous");

            case IP:
                // IP维度限流
                String clientIp = getClientIp();
                return baseResource + ":ip:" + clientIp;

            case DEFAULT:
            default:
                // 接口维度限流
                return baseResource;
        }
    }

    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId() {
        try {
            // 尝试从请求上下文获取
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                // 从Header或Session获取用户ID
                String userId = request.getHeader("X-User-Id");
                if (StringUtils.isNotBlank(userId)) {
                    return userId;
                }
                // 尝试从请求参数获取
                userId = request.getParameter("userId");
                if (StringUtils.isNotBlank(userId)) {
                    return userId;
                }
            }
        } catch (Exception e) {
            log.debug("获取用户ID失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                // 优先从代理头获取真实IP
                String ip = request.getHeader("X-Forwarded-For");
                if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                    return ip.split(",")[0].trim();
                }

                ip = request.getHeader("X-Real-IP");
                if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                    return ip;
                }

                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("获取客户端IP失败: {}", e.getMessage());
        }
        return "unknown";
    }
}
