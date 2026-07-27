package com.travel.service.impl;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.travel.annotation.CircuitBreaker;
import com.travel.common.ResultCode;
import com.travel.config.SentinelDegradeConfig;
import com.travel.dto.AiMessageCallbackDTO;
import com.travel.exception.BusinessException;
import com.travel.service.PythonServiceCaller;
import com.travel.service.ReceiveAIService;
import com.travel.util.TraceIdUtil;
import com.travel.vo.ChatMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Python AI 服务调用实现（带熔断保护）
 */
@Service
@Slf4j
public class PythonServiceCallerImpl implements PythonServiceCaller {

    @Autowired
    private ReceiveAIService receiveAIService;

    @Autowired
    private TraceIdUtil traceIdUtil;

    /**
     * 接收 Python 回调消息（带熔断保护）
     *
     * 熔断策略：
     * - 慢调用比例：响应时间 > 3秒 视为慢调用，慢调用比例 > 50% 触发熔断
     * - 最小调用数：5次
     * - 熔断持续：60秒
     */
    @Override
    @CircuitBreaker(
            value = SentinelDegradeConfig.PYTHON_SERVICE,
            fallbackMethod = "receiveAiFallback",
            slowCallRatioThreshold = 50,
            slowCallDurationThreshold = 3,
            failureRatioThreshold = 50,
            minimumNumberOfCalls = 5,
            waitDurationInOpenState = 60
    )
    public ChatMessageVO receiveAiMessageWithProtection(AiMessageCallbackDTO callbackDTO, Long userId) {
        String traceId = traceIdUtil.getTraceId();

        try {
            log.info("【Python服务调用】开始处理回调，traceId={}, sessionId={}",
                    traceId, callbackDTO.getSessionId());

            // 进入 Sentinel 资源
            Entry entry = SphU.entry(SentinelDegradeConfig.PYTHON_SERVICE, EntryType.OUT);

            // 调用实际服务
            ChatMessageVO result = receiveAIService.receiveAIByPython(callbackDTO, userId);

            // 正常结束
            if (entry != null) {
                entry.exit();
            }

            log.info("【Python服务调用】处理成功，traceId={}", traceId);
            return result;

        } catch (BlockException e) {
            // 熔断触发
            log.warn("【熔断器】【熔断触发】traceId={}, resource={}",
                    traceId, SentinelDegradeConfig.PYTHON_SERVICE);
            throw new BusinessException(ResultCode.PYTHON_SERVICE_UNAVAILABLE, "AI服务暂时繁忙，请稍后再试");

        } catch (BusinessException e) {
            // 业务异常，记录到 Sentinel
            Tracer.trace(e);
            throw e;

        } catch (Exception e) {
            // 其他异常，记录到 Sentinel（可能触发熔断）
            log.error("【Python服务调用】处理异常，traceId={}, error={}", traceId, e.getMessage());
            Tracer.trace(e);
            throw new BusinessException(ResultCode.PYTHON_SERVICE_ERROR, "AI服务处理失败");
        }
    }

    /**
     * 降级方法：熔断触发时执行
     *
     * @param callbackDTO 回调数据
     * @param userId 用户ID
     * @param e 原始异常
     * @return 降级响应
     */
    public ChatMessageVO receiveAiFallback(AiMessageCallbackDTO callbackDTO, Long userId, Throwable e) {
        String traceId = traceIdUtil.getTraceId();
        log.warn("【降级】【Python服务降级】traceId={}, sessionId={}, reason={}",
                traceId, callbackDTO.getSessionId(), e.getMessage());

        // 返回降级响应
        ChatMessageVO fallback = new ChatMessageVO();
        fallback.setContent("AI服务暂时繁忙，请稍后再试");
        fallback.setRole("assistant");
        fallback.setSessionId(callbackDTO.getSessionId());
        fallback.setUserId(userId);

        return fallback;
    }
}
