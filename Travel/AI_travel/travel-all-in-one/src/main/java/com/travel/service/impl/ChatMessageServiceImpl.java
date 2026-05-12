package com.travel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.entity.ChatMessage;
import com.travel.service.ChatMessageService;
import com.travel.mapper.ChatMessageMapper;
import org.springframework.stereotype.Service;

/**
* @author 13922
* @description 针对表【chat_message(对话消息表)】的数据库操作Service实现
* @createDate 2026-05-11 13:05:07
*/
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
    implements ChatMessageService{

}




