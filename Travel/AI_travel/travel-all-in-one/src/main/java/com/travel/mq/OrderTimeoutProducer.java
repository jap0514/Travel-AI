package com.travel.mq;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 订单超时消息 Producer
 *
 * 使用 RocketMQ 延迟消息实现订单超时自动取消
 *
 * 延迟级别：
 * - RocketMQ 支持 1s, 5s, 10s, 30s, 1m, 2m, 3m, 4m, 5m, 6m, 7m, 8m, 9m, 10m, 20m, 30m, 1h, 2h
 * - 这里使用 30分钟 (30m)
 */
@Slf4j
@Component
public class OrderTimeoutProducer {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /** 延迟时间：30分钟 */
    private static final int DELAY_LEVEL = 16;  // RocketMQ 延迟级别，16 = 30分钟

    /**
     * 发送订单超时延迟消息
     *
     * 消息将在 30 分钟后投递给消费者处理
     *
     * @param orderNo 订单号
     * @param userId 用户ID
     * @param traceId 链路追踪ID
     */
    public void sendTimeoutMessage(String orderNo, Long userId, String traceId) {
        try {
            // 构建消息体
            OrderTimeoutMessage message = new OrderTimeoutMessage();
            message.setOrderNo(orderNo);
            message.setUserId(userId);
            message.setCreateTime(System.currentTimeMillis());

            String jsonMsg = JSON.toJSONString(message);

            Message<String> rocketMsg = MessageBuilder
                    .withPayload(jsonMsg)
                    .setHeader("TRACE_ID", traceId)
                    .build();

            // 发送延迟消息（同步发送，等待 broker 确认）
            SendResult sendResult = rocketMQTemplate.syncSend(
                    TravelRocketMQConstant.ORDER_TIMEOUT_TOPIC + ":timeout",
                    rocketMsg,
                    3000,  // 超时时间 3 秒
                    DELAY_LEVEL  // 延迟级别
            );

            log.info("【订单超时消息】【发送成功】orderNo={}, userId={}, traceId={}, msgId={}",
                    orderNo, userId, traceId, sendResult.getMsgId());

        } catch (Exception e) {
            log.error("【订单超时消息】【发送失败】orderNo={}, userId={}, traceId={}, error={}",
                    orderNo, userId, traceId, e.getMessage(), e);
            // 发送失败不抛异常，订单仍然创建成功，只是不会自动取消
            // 可以配合定时扫描作为补偿
        }
    }

    /**
     * 订单超时消息体
     */
    public static class OrderTimeoutMessage {
        private String orderNo;
        private Long userId;
        private long createTime;

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public long getCreateTime() {
            return createTime;
        }

        public void setCreateTime(long createTime) {
            this.createTime = createTime;
        }
    }
}
