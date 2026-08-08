package com.travel.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置类
 *
 * 分为4类线程池：
 * 1. ioExecutor    - 通用IO密集型任务（HTTP、文件IO等）
 * 2. httpExecutor  - HTTP调用Python API专用
 * 3. mqExecutor    - MQ消息消费专用
 * 4. scheduledExecutor - 定时任务专用
 *
 * ioExecutor/httpExecutor/mqExecutor 配置了：
 * - setTaskDecorator: 传递MDC上下文（TraceId）到异步任务
 * - 拒绝策略: CallerRunsPolicy（防止任务丢失）
 * - 优雅关闭: waitForTasksToCompleteOnShutdown
 *
 * scheduledExecutor 配置了：
 * - 拒绝策略: CallerRunsPolicy
 * - 优雅关闭: waitForTasksToCompleteOnShutdown
 * - 错误处理: setErrorHandler
 * - 注: setPoolSize(5) 在 initialize() 时直接创建5个线程，无需预启动
 */
@Configuration
@Slf4j
public class ThreadPoolConfig {

    //============== IO密集型线程池 ========
    //多用于HTTP调用和文件IO
    @Bean("ioExecutor")
    public Executor ioExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("io-");

        // 传递MDC上下文到异步线程（TraceId等）
        executor.setTaskDecorator(task -> {
            String traceId = MDC.get("traceId");
            return () -> {
                try {
                    if (traceId != null) {
                        MDC.put("traceId", traceId);
                    }
                    task.run();
                } finally {
                    MDC.remove("traceId");
                }
            };
        });

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setPrestartAllCoreThreads(true);  // 预启动核心线程
        executor.initialize();
        return executor;
    }

    // ========== HTTP专用线程池 ==========
    // 用于：调用Python API（需要独立监控）
    @Bean("httpExecutor")
    public Executor httpExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("http-");

        // 传递MDC上下文到异步线程（TraceId等）
        executor.setTaskDecorator(task -> {
            String traceId = MDC.get("traceId");
            return () -> {
                try {
                    if (traceId != null) {
                        MDC.put("traceId", traceId);
                    }
                    task.run();
                } finally {
                    MDC.remove("traceId");
                }
            };
        });

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setPrestartAllCoreThreads(true);  // 预启动核心线程
        executor.initialize();
        return executor;
    }

    // ========== MQ消费线程池 ==========
    // 用于：RocketMQ消息消费
    @Bean("mqExecutor")
    public Executor mqExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("mq-");

        // 传递MDC上下文到异步线程（TraceId等）
        executor.setTaskDecorator(task -> {
            String traceId = MDC.get("traceId");
            return () -> {
                try {
                    if (traceId != null) {
                        MDC.put("traceId", traceId);
                    }
                    task.run();
                } finally {
                    MDC.remove("traceId");
                }
            };
        });

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setPrestartAllCoreThreads(true);  // 预启动核心线程
        executor.initialize();
        return executor;
    }

    // ========== 定时任务线程池 ==========
    @Bean("scheduledExecutor")
    public TaskScheduler scheduledExecutor() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("scheduled-");

        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);

        // 错误处理：单个任务异常不影响其他任务
        scheduler.setErrorHandler(t -> {
            log.error("定时任务执行失败: {}", t.getMessage(), t);
        });

        scheduler.initialize();
        return scheduler;
    }
}
