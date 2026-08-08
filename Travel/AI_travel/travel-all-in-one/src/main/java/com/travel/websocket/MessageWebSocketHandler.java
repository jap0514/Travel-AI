package com.travel.websocket;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 消息处理器
 * 用于向前端推送 AI 回调消息
 */
@Slf4j
@Component
public class MessageWebSocketHandler extends TextWebSocketHandler {

    /**
     * userId -> WebSocketSession 的映射
     */
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    /**
     * 客户端连接成功
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessions.put(userId, session);
            log.info("WebSocket 连接建立: userId={}", userId);
        } else {
            log.warn("WebSocket 连接建立失败: 无法获取 userId, sessionId={}", session.getId());
            session.close();
        }
    }

    /**
     * 收到客户端消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("收到 WebSocket 消息: {}", message.getPayload());
        // 目前主要用于前端发送心跳，可以扩展其他功能
    }

    /**
     * 客户端断开连接
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessions.remove(userId);
            log.info("WebSocket 连接关闭: userId={}", userId);
        }
    }

    /**
     * 发送消息给指定用户
     *
     * @return true=已成功写入连接；false=用户未连接或发送失败
     */
    public boolean sendMessageToUser(Long userId, Object message) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String json = JSON.toJSONString(message);
                session.sendMessage(new TextMessage(json));
                log.info("WebSocket 发送消息成功: userId={}", userId);
                return true;
            } catch (IOException e) {
                log.error("WebSocket 发送消息失败: userId={}, error={}", userId, e.getMessage());
                return false;
            }
        } else {
            log.warn("WebSocket 发送消息失败: 用户未连接, userId={}", userId);
            return false;
        }
    }

    /**
     * 从 session 中获取 userId
     * session URI 格式: /ws/message?userId=123
     */
    private Long getUserIdFromSession(WebSocketSession session) {
        try {
            String uri = session.getUri().toString();
            int index = uri.indexOf("userId=");
            if (index != -1) {
                String userIdStr = uri.substring(index + 7);
                return Long.parseLong(userIdStr);
            }
        } catch (Exception e) {
            log.error("解析 userId 失败: {}", e.getMessage());
        }
        return null;
    }
}
