package com.travel.service;

import com.travel.dto.AiMessageCallbackDTO;
import com.travel.vo.ChatMessageVO;

/**
 * Python AI 服务调用接口
 *
 * 定义调用 Python 服务的标准接口，配合熔断器使用
 */
public interface PythonServiceCaller {

    /**
     * 接收 Python 回调的 AI 消息
     * 带有熔断保护
     *
     * @param callbackDTO 回调数据
     * @param userId 用户ID
     * @return 消息VO
     */
    ChatMessageVO receiveAiMessageWithProtection(AiMessageCallbackDTO callbackDTO, Long userId);

    /**
     * 降级方法：熔断触发时执行
     */
    default ChatMessageVO receiveAiFallback(AiMessageCallbackDTO callbackDTO, Long userId, Throwable e) {
        return null;
    }
}
