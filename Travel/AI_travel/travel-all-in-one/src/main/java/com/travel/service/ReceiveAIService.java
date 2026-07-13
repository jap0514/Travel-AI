package com.travel.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel.dto.AiMessageCallbackDTO;
import com.travel.vo.ChatMessageVO;

public interface ReceiveAIService {
    /**
     * 接收python返回的AI回复，并且发送到前端显示
     * @param callbackDTO
     * @param userId
     * @return chatMessageVO
     */
    ChatMessageVO receiveAIByPython(AiMessageCallbackDTO callbackDTO, Long userId) throws JsonProcessingException;

    /**
     * 发送请求到python
     *
     * @param sessionId
     * @param userId
     * @param msg_id
     * @param content
     */
    void sendRequestToPythonAsync(Long sessionId, Long userId, Long msg_id, String content);
}
