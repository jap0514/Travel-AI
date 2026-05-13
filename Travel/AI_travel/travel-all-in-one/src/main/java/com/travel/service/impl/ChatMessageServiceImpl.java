package com.travel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.common.ChatMessageRoleEnum;
import com.travel.dto.ChatMessageDTO;
import com.travel.entity.ChatMessage;
import com.travel.service.ChatMessageService;
import com.travel.mapper.ChatMessageMapper;
import com.travel.util.MqMessageUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
* @author 13922
* @description 针对表【chat_message(对话消息表)】的数据库操作Service实现
* @createDate 2026-05-11 13:05:07
*/
@Service
@Slf4j
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
    implements ChatMessageService{

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private MqMessageUtil mqMessageUtil;

    @Value("${travel.mq.content-topic:travel-content-exchange}")
    private String contentTopic;

    @Value("${travel.mq.content-tag:content-exchange}")
    private String contentTag;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    /**
     * 从用户的message里面获取到具体的内容content
     * @param chatMessageDTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void getContentFromMessage(ChatMessageDTO chatMessageDTO,Long userId) {
        //1、获取DTO里面的信息
        Long sessionId = chatMessageDTO.getSessionId();
        String content = chatMessageDTO.getContent();
        String planJson = chatMessageDTO.getPlanJson();
//        Long userId = chatMessageDTO.getUserId();

        //2、保存到数据库
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(content);
        chatMessage.setRole(ChatMessageRoleEnum.USER);
        chatMessage.setPlan_json(planJson);
        chatMessage.setUser_id(userId);
        chatMessage.setSession_id(sessionId);
        chatMessage.setCreate_time(LocalDateTime.now());

        int saved = chatMessageMapper.insert(chatMessage);
        if (saved==0) {
            log.error("保存用户消息失败，sessionId={}, userId={}", sessionId, userId);
            throw new RuntimeException("消息保存失败");
        }
        log.info("用户消息已保存，msgId={}", chatMessage.getMsg_id());

        //2、将content发送给rocketmq

        //3、构建消息体
        String mqMessage = mqMessageUtil.buildContentMessage(chatMessage);

        //4、发送到 Rocketmq
        String destination=contentTopic+":"+contentTag;
        try{
            Message<String> message = MessageBuilder.withPayload(mqMessage).build();
            rocketMQTemplate.syncSend(destination,message);
            log.info("消息已发送至 RocketMQ，destination={}, sessionId={}", destination, sessionId);
        }catch (Exception e){
            log.error("发送消息到 RocketMQ 失败，destination={}, sessionId={}", destination, sessionId, e);
            //根据业务决定是否抛出异常（例如可记录失败等待重试）
            throw new RuntimeException("MQ消息发送失败",e);
        }
    }
}




