package com.travel.service;

import com.travel.dto.ChatMessageDTO;
import com.travel.entity.ChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 13922
* @description 针对表【chat_message(对话消息表)】的数据库操作Service
* @createDate 2026-05-11 13:05:07
*/
public interface ChatMessageService extends IService<ChatMessage> {

    /**
     * 从用户的message里面获取到具体的内容content
     * @param chatMessageDTO
     */
    void getContentFromMessage(ChatMessageDTO chatMessageDTO,Long userId);
}
