package com.travel.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer 指标配置
 *
 * 功能：
 * 1. 开启 @Timed 注解支持
 * 2. 配置 Prometheus 端点
 */
@Configuration
public class MetricsConfig {

    /**
     * 开启 @Timed 注解支持
     * 让方法可以自动记录执行时间
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
