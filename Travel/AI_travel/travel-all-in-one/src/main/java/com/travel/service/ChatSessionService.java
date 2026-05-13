package com.travel.service;

import com.travel.dto.ChatSessionDTO;
import com.travel.entity.ChatSession;
import com.baomidou.mybatisplus.extension.service.IService;
import com.travel.vo.ChatSessionVO;

/**
* @author 13922
* @description 针对表【chat_session(会话记录表)】的数据库操作Service
* @createDate 2026-05-11 13:04:47
*/
public interface ChatSessionService extends IService<ChatSession> {
    /**
     * 创建新会话
     * @param chatSessionDTO
     * @param userId
     * @return 返回chatSessionVo
     */
    ChatSessionVO createSession(ChatSessionDTO chatSessionDTO, Long userId);
}
