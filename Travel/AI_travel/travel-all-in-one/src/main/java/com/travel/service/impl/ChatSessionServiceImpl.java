package com.travel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.common.ChatMessageRoleEnum;
import com.travel.dto.ChatSessionDTO;
import com.travel.entity.ChatMessage;
import com.travel.entity.ChatSession;
import com.travel.mapper.ChatMessageMapper;
import com.travel.mapper.UserMapper;
import com.travel.service.ChatSessionService;
import com.travel.mapper.ChatSessionMapper;
import com.travel.vo.ChatMessageVO;
import com.travel.vo.ChatSessionVO;
import com.travel.vo.PageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private UserMapper userMapper;

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
        chatSession.setUserId(userId);
        chatSession.setCreateTime(LocalDateTime.now());
        chatSession.setUpdateTime(LocalDateTime.now());

        //3、入库处理
        int insert = chatSessionMapper.insert(chatSession);
        if(insert!=0){
            log.info("会话数据插入成功");
        }else {
            log.error("会话数据插入失败");
        }

        //4、封装好VO
        ChatSessionVO chatSessionVO=new ChatSessionVO();
        chatSessionVO.setSessionId(chatSession.getSessionId());
        chatSessionVO.setTitle(chatSession.getTitle());
        chatSessionVO.setCreateTime(chatSession.getCreateTime());
        chatSessionVO.setUpdateTime(chatSession.getUpdateTime());

        return chatSessionVO;
    }

    /**
     * 获取当前用户ID下的所有会话记录
     * @param userId
     * @return
     */
    @Override
    public PageVO<ChatSessionVO> getUserSessions(Long userId, Long page, Long size) {
        //1、构造 IPage，需要适配数据库中的字段
        IPage<ChatSession> iPage=new Page<>(page,size);

        //2、使用wrapper，根据userId查，并且需要按照创建时间倒序排序
        LambdaQueryWrapper<ChatSession> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ChatSession::getUserId,userId)
                .orderByDesc(ChatSession::getCreateTime);

        //3、进行查询
        IPage<ChatSession> result = chatSessionMapper.selectPage(iPage, lambdaQueryWrapper);


        //4、转换成 ChatSessionVO
        List<ChatSessionVO> voList=result.getRecords().stream()
                .map(s->{
                    ChatSessionVO vo=new ChatSessionVO();
                    vo.setSessionId(s.getSessionId());
                    vo.setTitle(s.getTitle());
                    vo.setCreateTime(s.getCreateTime());
                    vo.setUpdateTime(s.getUpdateTime());
                    return vo;
                }).toList();

        //5、构造 PageVO
        return PageVO.of(voList,result.getTotal(),page,size);
    }

    /**
     * 根据用户ID和会话ID来查询消息
     * @param useId
     * @param sessionId
     * @param page
     * @param size
     * @return
     */
    @Override
    public PageVO<ChatMessageVO> getMessageBySessionId(Long useId, Long sessionId, Long page, Long size) {
        IPage<ChatMessage> iPage=new Page<>(page,size);
        LambdaQueryWrapper<ChatMessage> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ChatMessage::getSessionId,sessionId)
                .eq(ChatMessage::getUserId,useId);
        IPage<ChatMessage> result = chatMessageMapper.selectPage(iPage, lambdaQueryWrapper);

        List<ChatMessageVO> voList=result.getRecords().stream()
                .map(s->{
                    ChatMessageVO vo=new ChatMessageVO();
                    if(s.getRole().equals(ChatMessageRoleEnum.USER))vo.setRole(ChatMessageRoleEnum.USER);
                    if(s.getRole().equals(ChatMessageRoleEnum.ASSISTANT))vo.setRole(ChatMessageRoleEnum.ASSISTANT);
                    vo.setContent(s.getContent());
                    vo.setUserNickname(userMapper.selectById(useId).getNickname());
                    vo.setMsgId(s.getMsgId());
                    vo.setSessionId(s.getSessionId());
                    vo.setPlanJson(s.getPlanJson().toString());
                    vo.setCreateTime(s.getCreateTime());
                    vo.setUserId(s.getUserId());
                    return vo;
                }).toList();


        return PageVO.of(voList,result.getTotal(),page,size);
    }
}




