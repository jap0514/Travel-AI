package com.travel.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel.common.Result;
import com.travel.dto.AiMessageCallbackDTO;
import com.travel.service.ReceiveAIService;
import com.travel.vo.ChatMessageVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sendMessageByPython")
public class ReceiveAIController {
    @Autowired
    private ReceiveAIService receiveAIService;

    @PostMapping("/receiveAI")
    public Result<ChatMessageVO> receiveAI(@RequestBody @Valid AiMessageCallbackDTO callbackDTO) throws JsonProcessingException {
        // Python回调时没有token，userId从DTO中获取
        Long userId = callbackDTO.getUserId();
        ChatMessageVO chatMessageVO=receiveAIService.receiveAIByPython(callbackDTO,userId);
        return Result.success(chatMessageVO);
    }
}
