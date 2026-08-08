package com.travel.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.common.ChatMessageRoleEnum;
import com.travel.common.ResultCode;
import com.travel.dto.ChatMessageDTO;
import com.travel.entity.ChatMessage;
import com.travel.exception.BusinessException;
import com.travel.mapper.UserMapper;
import com.travel.service.ChatMessageService;
import com.travel.mapper.ChatMessageMapper;
import com.travel.service.ReceiveAIService;
import com.travel.util.MqMessageUtil;
import com.travel.vo.ChatMessageVO;
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

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ReceiveAIService receiveAIService;

    /**
     * 发送用户消息给python
     * @param chatMessageDTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO sendMessageToPython(ChatMessageDTO chatMessageDTO, Long userId) {
        //1、获取DTO里面的信息
        Long sessionId = chatMessageDTO.getSessionId();
        String content = chatMessageDTO.getContent();
        String planJson = chatMessageDTO.getPlanJson();
        LocalDateTime now = LocalDateTime.now();

        //2、保存到数据库
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(content);
        chatMessage.setRole(ChatMessageRoleEnum.USER);
        chatMessage.setPlanJson(planJson);
        chatMessage.setUserId(userId);
        chatMessage.setSessionId(sessionId);
        chatMessage.setCreateTime(now);

        int saved = chatMessageMapper.insert(chatMessage);
        if (saved==0) {
            log.error("保存用户消息失败，sessionId={}, userId={}", sessionId, userId);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "消息保存失败");
        }
        log.info("用户消息已保存，msgId={}", chatMessage.getMsgId());

        //获取messageId
        //因为mybatis-plus的insert插入后会回填主键到对象中，直接get就行
        Long msg_id = chatMessage.getMsgId();

        //4、将消息转发给python，同时需要python做收到确认返回，还需要从数据库中获得刚刚存进去的消息ID传给python
        // flowId 优先用前端回传的；前端没传时，服务端兜底：若上一句 AI 消息正处于 waiting_ 交互状态，
        // 说明本条消息是对该交互的回答，需要带上它的 flowId 让 Python 恢复中断的 flow
        String flowId = chatMessageDTO.getFlowId();
        if (flowId == null || flowId.isBlank()) {
            flowId = resolvePendingFlowId(sessionId);
        }

        receiveAIService.sendRequestToPythonAsync(sessionId, userId, msg_id, content,
                chatMessageDTO.getStartDate(), chatMessageDTO.getDays(), flowId);

        ChatMessageVO chatMessageVO=new ChatMessageVO();
        chatMessageVO.setContent(content);
        chatMessageVO.setRole(ChatMessageRoleEnum.USER);
        chatMessageVO.setCreateTime(chatMessage.getCreateTime());
        chatMessageVO.setMsgId(chatMessage.getMsgId());
        chatMessageVO.setSessionId(chatMessage.getSessionId());
        chatMessageVO.setPlanJson(chatMessage.getPlanJson() != null ? chatMessage.getPlanJson().toString() : null);
        chatMessageVO.setUserId(chatMessage.getUserId());
        chatMessageVO.setUserNickname(userMapper.selectById(userId).getNickname());

        return chatMessageVO;
    }

    /**
     * 查找该会话中处于挂起状态的 flowId
     *
     * 规则：取会话中最新一条 AI 消息，若它带有 interaction 且 status 以 "waiting_" 开头，
     * 说明 AI 上一句正在等待用户回答，用户当前这条消息即是对它的回复，返回其 flowId。
     * 只看最新一条是有意的——一旦 AI 发出了新的非等待消息，之前的交互就不该再被续上。
     *
     * @param sessionId 会话ID
     * @return 挂起的 flowId；没有挂起的交互时返回 null
     */
    private String resolvePendingFlowId(Long sessionId) {
        try {
            LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSessionId, sessionId)
                    .eq(ChatMessage::getRole, ChatMessageRoleEnum.ASSISTANT)
                    .orderByDesc(ChatMessage::getMsgId)
                    .last("LIMIT 1");
            ChatMessage lastAiMessage = baseMapper.selectOne(wrapper);

            if (lastAiMessage == null || lastAiMessage.getInteraction() == null
                    || lastAiMessage.getFlowId() == null) {
                return null;
            }

            JSONObject interaction = JSON.parseObject(lastAiMessage.getInteraction());
            String status = interaction == null ? null : interaction.getString("status");
            if (status != null && status.startsWith("waiting_")) {
                log.info("检测到挂起的交互，续传 flowId={}, status={}, sessionId={}",
                        lastAiMessage.getFlowId(), status, sessionId);
                return lastAiMessage.getFlowId();
            }
            return null;
        } catch (Exception e) {
            // 兜底逻辑失败不应影响正常发消息，降级为新建 flow
            log.error("查询挂起 flowId 失败，sessionId={}, error={}", sessionId, e.getMessage());
            return null;
        }
    }




//    /**
//     * 从用户的message里面获取到具体的内容content
//     * @param chatMessageDTO
//     */
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public ChatMessageVO getContentFromMessage(ChatMessageDTO chatMessageDTO, Long userId) {
//        //1、获取DTO里面的信息
//        Long sessionId = chatMessageDTO.getSessionId();
//        String content = chatMessageDTO.getContent();
//        String planJson = chatMessageDTO.getPlanJson();
////        Long userId = chatMessageDTO.getUserId();
//
//        //2、保存到数据库
//        ChatMessage chatMessage = new ChatMessage();
//        chatMessage.setContent(content);
//        chatMessage.setRole(ChatMessageRoleEnum.USER);
//        chatMessage.setPlan_json(planJson);
//        chatMessage.setUser_id(userId);
//        chatMessage.setSession_id(sessionId);
//        chatMessage.setCreate_time(LocalDateTime.now());
//
//        int saved = chatMessageMapper.insert(chatMessage);
//        if (saved==0) {
//            log.error("保存用户消息失败，sessionId={}, userId={}", sessionId, userId);
//            throw new RuntimeException("消息保存失败");
//        }
//        log.info("用户消息已保存，msgId={}", chatMessage.getMsg_id());
//
//        //2、将content发送给rocketmq
//
//        //3、构建消息体
//        String mqMessage = mqMessageUtil.buildContentMessage(chatMessage);
//
//        //4、发送到 Rocketmq
//        String destination=contentTopic+":"+contentTag;
//        try{
//            Message<String> message = MessageBuilder.withPayload(mqMessage).build();
//            rocketMQTemplate.syncSend(destination,message);
//            log.info("消息已发送至 RocketMQ，destination={}, sessionId={}", destination, sessionId);
//        }catch (Exception e){
//            log.error("发送消息到 RocketMQ 失败，destination={}, sessionId={}", destination, sessionId, e);
//            //根据业务决定是否抛出异常（例如可记录失败等待重试）
//            throw new RuntimeException("MQ消息发送失败",e);
//        }
//
//        ChatMessageVO chatMessageVO=new ChatMessageVO();
//        chatMessageVO.setContent(content);
//        chatMessageVO.setRole(ChatMessageRoleEnum.USER);
//        chatMessageVO.setCreateTime(chatMessage.getCreate_time());
//        chatMessageVO.setMsgId(chatMessage.getMsg_id());
//        chatMessageVO.setSessionId(chatMessage.getSession_id());
//        chatMessageVO.setPlanJson(chatMessage.getPlan_json().toString());
//        chatMessageVO.setUserId(chatMessage.getUser_id());
//        chatMessageVO.setUserNickname(userMapper.selectById(userId).getNickname());
//
//        return chatMessageVO;
//    }
}




