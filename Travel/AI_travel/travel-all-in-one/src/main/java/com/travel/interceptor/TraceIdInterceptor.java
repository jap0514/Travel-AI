package com.travel.interceptor;

import com.travel.util.TraceIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * TraceId 拦截器
 *
 * 功能：
 * 1. 从请求头获取 TraceId（如果外部传入）
 * 2. 如果没有，则生成新的 TraceId
 * 3. 设置到 MDC，供 Logback 使用
 * 4. 响应头回传 TraceId
 */
@Slf4j
@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    private static final String TRACE_ID_HEADER = "TRACE_ID";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 尝试从请求头获取 TraceId
        String traceId = request.getHeader(TRACE_ID_HEADER);

        // 2. 如果没有，生成新的
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceIdUtil.generateTraceId();
            log.debug("【TraceId】新生成 traceId={}", traceId);
        } else {
            log.debug("【TraceId】透传 traceId={}", traceId);
        }

        // 3. 设置到 MDC（Logback 会自动从 MDC 读取并打印到日志）
        TraceIdUtil.setTraceId(traceId);

        // 4. 响应头回传 TraceId（方便前端排查问题）
        response.setHeader(TRACE_ID_HEADER, traceId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束后清除 MDC
        TraceIdUtil.clear();
    }
}
