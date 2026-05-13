package com.travel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.dto.ChatSessionDTO;
import com.travel.entity.ChatSession;
import com.travel.service.ChatSessionService;
import com.travel.mapper.ChatSessionMapper;
import com.travel.vo.ChatSessionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
* @author 13922
* @description 针对表【chat_session(会话记录表)】的数据库操作Service实现
* @createDate 2026-05-11 13:04:47
*/
@Service
@Slf4j
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
    implements ChatSessionService{

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    /**
     * 创建新会话
     * @param chatSessionDTO
     * @param userId
     * @return 返回chatSessionVo
     */
    @Override
    public ChatSessionVO createSession(ChatSessionDTO chatSessionDTO, Long userId) {
        //1、生成唯一的会话sessionID
        ChatSession chatSession=new ChatSession();
        chatSession.setTitle(chatSessionDTO.getTitle());
        chatSession.setUser_id(userId);
        chatSession.setCreate_time(LocalDateTime.now());
        chatSession.setUpdate_time(LocalDateTime.now());

        //3、入库处理
        int insert = chatSessionMapper.insert(chatSession);
        if(insert!=0){
            log.info("会话数据插入成功");
        }else {
            log.error("会话数据插入失败");
        }

        //4、封装好VO
        ChatSessionVO chatSessionVO=new ChatSessionVO();
        chatSessionVO.setSessionId(chatSession.getSession_id());
        chatSessionVO.setTitle(chatSession.getTitle());
        chatSessionVO.setUpdateTime(chatSession.getUpdate_time());

        return chatSessionVO;
    }
}




