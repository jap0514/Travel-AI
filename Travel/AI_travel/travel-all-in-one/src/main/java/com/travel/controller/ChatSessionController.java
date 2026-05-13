package com.travel.controller;

import com.travel.common.Result;
import com.travel.dto.ChatSessionDTO;
import com.travel.service.ChatSessionService;
import com.travel.vo.ChatSessionVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/session")
public class ChatSessionController {
    /**
     * 创建新会话
     */
    @Autowired
    private ChatSessionService chatSessionService;

    @PostMapping("/createSession")
    public Result<ChatSessionVO> createSession(@RequestBody @Valid ChatSessionDTO chatSessionDTO,
                                               @RequestAttribute Long userId){
        ChatSessionVO chatSessionVO=chatSessionService.createSession(chatSessionDTO,userId);
        return Result.success(chatSessionVO);
    }

}
