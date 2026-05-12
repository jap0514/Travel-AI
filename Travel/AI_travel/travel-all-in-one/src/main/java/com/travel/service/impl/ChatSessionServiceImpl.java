package com.travel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.entity.ChatSession;
import com.travel.service.ChatSessionService;
import com.travel.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;

/**
* @author 13922
* @description 针对表【chat_session(会话记录表)】的数据库操作Service实现
* @createDate 2026-05-11 13:04:47
*/
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
    implements ChatSessionService{

}




