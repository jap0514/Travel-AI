package com.travel.annotation;

import java.lang.annotation.*;

/**
 * Sentinel 熔断器注解
 *
 * 使用方式：
 * <pre>
 * @CircuitBreaker(
 *     value = "pythonService",
 *     fallbackMethod = "pythonFallback",
 *     slowCallRatioThreshold = 50,      // 慢调用比例阈值 50%
 *     slowCallDurationThreshold = 3s,   // 慢调用时长阈值 3秒
 *     failureRatioThreshold = 50,      // 异常比例阈值 50%
 *     minimumNumberOfCalls = 5,        // 最小调用数
 *     waitDurationInOpenState = 60s    // 熔断持续时间
 * )
 * public String callPythonAI(String message) {
 *     // 调用 Python 服务
 * }
 *
 * public String pythonFallback(String message, Exception e) {
 *     return "AI服务暂时繁忙，请稍后再试";
 * }
 * </pre>
 *
 * 熔断策略说明：
 * - 慢调用比例：请求响应时间 > slowCallDurationThreshold 视为慢调用
 * - 异常比例：请求异常率 > failureRatioThreshold
 * - 熔断后，经过 waitDurationInOpenState 后进入半开状态，尝试放行一个请求
 * - 如果成功，恢复正常；失败则继续熔断
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitBreaker {

    /**
     * 熔断器名称（对应 Sentinel 资源名）
     */
    String value();

    /**
     * 降级方法名（必须与原方法参数兼容）
     * 当熔断触发时，会调用此方法返回降级结果
     */
    String fallbackMethod();

    /**
     * 慢调用比例阈值（百分比）
     * 当慢调用比例超过此值时，触发熔断
     * 默认 50%
     */
    int slowCallRatioThreshold() default 50;

    /**
     * 慢调用时长阈值（秒）
     * 请求响应时间超过此值视为慢调用
     * 默认 3 秒
     */
    int slowCallDurationThreshold() default 3;

    /**
     * 异常比例阈值（百分比）
     * 当异常比例超过此值时，触发熔断
     * 默认 50%
     */
    int failureRatioThreshold() default 50;

    /**
     * 最小调用数
     * 熔断器达到此调用数后才开始计算慢调用比例/异常比例
     * 默认 5
     */
    int minimumNumberOfCalls() default 5;

    /**
     * 熔断持续时间（秒）
     * 熔断状态持续多久后进入半开状态，尝试放行
     * 默认 60 秒
     */
    int waitDurationInOpenState() default 60;

    /**
     * 半开状态允许通过的调用数
     * 默认 1
     */
    int permittedNumberOfCallsInHalfOpenState() default 1;
}
