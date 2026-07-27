package com.travel.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Python AI 服务降级处理
 *
 * 当 Python 服务熔断或不可用时，返回友好的降级响应
 */
@Slf4j
@Component
public class PythonServiceFallback {

    /**
     * AI服务降级时的默认回复
     */
    public static final String FALLBACK_MESSAGE = "AI服务暂时繁忙，请稍后再试";

    /**
     * 获取降级后的AI回复
     * 用于当Python服务熔断时，返回给用户一个友好的提示
     */
    public String getFallbackResponse() {
        log.warn("【降级】Python服务不可用，返回降级响应");
        return FALLBACK_MESSAGE;
    }

    /**
     * 获取降级后的AI回复（带原因）
     */
    public String getFallbackResponse(String reason) {
        log.warn("【降级】Python服务不可用，原因={}，返回降级响应", reason);
        return FALLBACK_MESSAGE;
    }
}
