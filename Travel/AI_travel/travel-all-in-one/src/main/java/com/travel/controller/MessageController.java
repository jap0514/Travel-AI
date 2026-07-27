package com.travel.controller;

import com.travel.annotation.RateLimiter;
import com.travel.common.Result;
import com.travel.dto.ChatMessageDTO;
import com.travel.service.ChatMessageService;
import com.travel.vo.ChatMessageVO;
import io.micrometer.core.annotation.Timed;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/message")
public class MessageController {

    @Autowired
    private ChatMessageService chatMessageService;

    /**
     * 接收前端传过来的用户message
     * @param chatMessageDTO
     * @return
     */
    @PostMapping("/sendMessage")
    @RateLimiter(resourceName = "ChatController:sendMessage", count = 100, timeout = 1000)
    @Timed(value = "message.sendMessage", description = "发送消息接口耗时", percentiles = {0.5, 0.90, 0.95, 0.99})
    public Result<ChatMessageVO> sendMessage(@RequestBody @Valid ChatMessageDTO chatMessageDTO,
                                             @RequestAttribute Long userId){
        //获取到message里面的消息内容部分
        System.out.println("到sendMessage了");
        ChatMessageVO chatMessageVO = chatMessageService.sendMessageToPython(chatMessageDTO, userId);
        return Result.success(chatMessageVO);
    }
}
