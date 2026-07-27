package com.travel.mq;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.entity.HotelBooking;
import com.travel.mapper.HotelBookingMapper;
import com.travel.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 订单超时消息 Consumer
 *
 * 特点：
 * 1. 集群模式消费：多实例部署时，消息只会被一个实例处理
 * 2. 状态机校验：只有待支付(0)状态才取消
 * 3. 分布式锁：保证同一订单不会被并发处理
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = TravelRocketMQConstant.ORDER_TIMEOUT_TOPIC,
        consumerGroup = TravelRocketMQConstant.ORDER_TIMEOUT_CONSUMER_GROUP,
        messageModel = MessageModel.CLUSTERING,    // 集群模式，保证消息不重复
        consumeMode = ConsumeMode.ORDERLY           // 顺序消费，保证同一订单按顺序处理
)
public class OrderTimeoutConsumer implements org.apache.rocketmq.spring.core.RocketMQListener<MessageExt> {

    @Autowired
    private HotelBookingMapper hotelBookingMapper;

    @Override
    public void onMessage(MessageExt message) {
        String msgJson = new String(message.getBody(), StandardCharsets.UTF_8);
        String traceId = extractTraceId(message);

        try {
            // 设置链路追踪
            TraceIdUtil.setTraceId(traceId);

            log.info("【订单超时消费】【开始处理】traceId={}, msg={}", traceId, msgJson);

            // 解析消息
            OrderTimeoutProducer.OrderTimeoutMessage timeoutMsg =
                    JSON.parseObject(msgJson, OrderTimeoutProducer.OrderTimeoutMessage.class);

            String orderNo = timeoutMsg.getOrderNo();
            log.info("【订单超时消费】【解析成功】traceId={}, orderNo={}", traceId, orderNo);

            // 处理超时订单
            processTimeoutOrder(orderNo, traceId);

            // 消费成功，自动 ACK
            log.info("【订单超时消费】【处理完成】traceId={}, orderNo={}", traceId, orderNo);

        } catch (Exception e) {
            log.error("【订单超时消费】【处理异常】traceId={}, error={}", traceId, e.getMessage(), e);
            // 抛出异常，触发重试
            throw new RuntimeException("订单超时处理失败", e);
        } finally {
            TraceIdUtil.clear();
        }
    }

    /**
     * 处理超时订单（带状态机校验）
     */
    private void processTimeoutOrder(String orderNo, String traceId) {
        // 查询订单
        HotelBooking booking = hotelBookingMapper.selectOne(
                new LambdaQueryWrapper<HotelBooking>()
                        .eq(HotelBooking::getOrderNo, orderNo)
        );

        if (booking == null) {
            log.warn("【订单超时消费】【订单不存在】traceId={}, orderNo={}", traceId, orderNo);
            return;
        }

        log.info("【订单超时消费】【订单状态检查】traceId={}, orderNo={}, currentStatus={}",
                traceId, orderNo, booking.getStatus());

        // 状态机校验：只有待支付(0)状态才需要取消
        // 1=已支付, 2=已确认, 3=已取消, 4=已完成 都跳过
        if (booking.getStatus() != 0) {
            log.info("【订单超时消费】【跳过】订单状态不是待支付，orderNo={}, status={}",
                    orderNo, booking.getStatus());
            return;
        }

        // 执行取消
        cancelTimeoutOrder(booking, traceId);
    }

    /**
     * 取消超时订单
     */
    private void cancelTimeoutOrder(HotelBooking booking, String traceId) {
        String orderNo = booking.getOrderNo();

        // 更新状态
        booking.setStatus(3);  // 已取消
        booking.setCancelReason("支付超时（30分钟）自动取消");
        booking.setCancelTime(LocalDateTime.now());
        booking.setUpdateTime(LocalDateTime.now());

        int rows = hotelBookingMapper.updateById(booking);

        if (rows > 0) {
            log.info("【订单超时消费】【取消成功】traceId={}, orderNo={}", traceId, orderNo);
        } else {
            log.warn("【订单超时消费】【取消失败】影响行数为0，orderNo={}", orderNo);
        }
    }

    /**
     * 从消息中提取 traceId
     */
    private String extractTraceId(MessageExt message) {
        try {
            String traceId = message.getUserProperty("TRACE_ID");
            if (traceId != null && !traceId.isEmpty()) {
                return traceId;
            }
        } catch (Exception e) {
            log.warn("提取traceId失败: {}", e.getMessage());
        }
        return TraceIdUtil.generateTraceId();
    }
}
