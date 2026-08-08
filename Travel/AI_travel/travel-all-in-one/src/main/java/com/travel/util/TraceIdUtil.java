package com.travel.util;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * TraceId 工具类
 * 统一管理 TraceId 的获取和设置
 */
@Component
public class TraceIdUtil {

    private static final String TRACE_ID = "traceId";

    private TraceIdUtil() {}

    /**
     * 获取当前 TraceId（从 MDC 中）
     * 如果没有，则生成一个新的
     */
    public static String getTraceId() {
        String traceId = MDC.get(TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
        }
        return traceId;
    }

    /**
     * 生成新的 TraceId
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 设置 TraceId 到 MDC
     */
    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID, traceId);
    }

    /**
     * 清除 MDC 中的 TraceId
     */
    public static void clear() {
        MDC.remove(TRACE_ID);
    }
}
