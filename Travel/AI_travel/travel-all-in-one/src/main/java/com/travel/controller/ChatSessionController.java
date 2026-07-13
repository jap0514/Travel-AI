package com.travel.controller;

import com.travel.common.Result;
import com.travel.dto.ChatSessionDTO;
import com.travel.service.ChatSessionService;
import com.travel.vo.ChatMessageVO;
import com.travel.vo.ChatSessionVO;
import com.travel.vo.PageVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/session")
public class ChatSessionController {

    @Autowired
    private ChatSessionService chatSessionService;

    /**
     * 创建新会话
     */
    @PostMapping("/createSession")
    public Result<ChatSessionVO> createSession(@RequestBody @Valid ChatSessionDTO chatSessionDTO,
                                               @RequestAttribute Long userId){
        ChatSessionVO chatSessionVO=chatSessionService.createSession(chatSessionDTO,userId);
        return Result.success(chatSessionVO);
    }


    /**
     * 分页查询用户的所有会话记录
     * @param userId
     * @param page
     * @param size
     * @return
     */
    @GetMapping("/getUserSessions/{userId}")
    public Result<PageVO<ChatSessionVO>> getUserSessions(@PathVariable("userId") Long userId,
                                                         @RequestParam(defaultValue = "1") Long page,
                                                         @RequestParam(defaultValue = "10") Long size){
        PageVO<ChatSessionVO> sessionsList=chatSessionService.getUserSessions(userId,page,size);
        return Result.success(sessionsList);
    }

    /**
     * 分页查询会话里面的所有消息
     * @param useId
     * @param sessionId
     * @param page
     * @param size
     * @return
     */
    @GetMapping("/{userId}/{sessionId}/message")
    public Result<PageVO<ChatMessageVO>> getMessageBySessionId(@PathVariable("userId") Long useId,
                                                               @PathVariable("sessionId") Long sessionId,
                                                               @RequestParam(defaultValue = "1") Long page,
                                                               @RequestParam(defaultValue = "10") Long size){
        PageVO<ChatMessageVO> messageBySessionList=chatSessionService.getMessageBySessionId(useId,sessionId,page,size);
        return Result.success(messageBySessionList);
    }

}
