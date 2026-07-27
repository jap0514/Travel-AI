package com.travel.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel.annotation.RateLimiter;
import com.travel.common.Result;
import com.travel.dto.AiMessageCallbackDTO;
import com.travel.service.PythonServiceCaller;
import com.travel.vo.ChatMessageVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sendMessageByPython")
public class ReceiveAIController {

    @Autowired
    private PythonServiceCaller pythonServiceCaller;

    /**
     * 接收Python AI回调（带限流+熔断保护）
     *
     * 限流：100 QPS
     * 熔断：Python服务慢调用/异常时自动熔断降级
     */
    @PostMapping("/receiveAI")
    @RateLimiter(resourceName = "ChatController:aiMessageCallback", count = 100, timeout = 1000)
    public Result<ChatMessageVO> receiveAI(@RequestBody @Valid AiMessageCallbackDTO callbackDTO) throws JsonProcessingException {
        // Python回调时没有token，userId从DTO中获取
        Long userId = callbackDTO.getUserId();

        // 使用带熔断保护的调用
        ChatMessageVO chatMessageVO = pythonServiceCaller.receiveAiMessageWithProtection(callbackDTO, userId);

        return Result.success(chatMessageVO);
    }
}
