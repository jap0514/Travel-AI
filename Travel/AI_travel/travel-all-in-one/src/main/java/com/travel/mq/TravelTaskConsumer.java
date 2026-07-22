package com.travel.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.travel.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
//开启手动ACK
@RocketMQMessageListener(
        topic = TravelRocketMQConstant.TRAVEL_TASK_RESULT_TOPIC,
        consumerGroup = TravelRocketMQConstant.TRAVEL_CONSUMER_GROUP,
        messageModel = MessageModel.CLUSTERING,    //集群模式
        consumeMode = ConsumeMode.CONCURRENTLY    //并发模式
)
public class TravelTaskConsumer implements RocketMQListener<MessageExt> {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(MessageExt message) {
        String msgJson = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            // 从消息中提取 traceId 并放入 MDC，保持链路追踪
            String traceId = extractTraceId(msgJson);
            TraceIdUtil.setTraceId(traceId);

            log.info("【MQ消费】收到消息 | traceId={} | msg={}", traceId, msgJson.substring(0, Math.min(100, msgJson.length())));

            // 解析消息内容
            JSONObject messageObj = JSON.parseObject(msgJson);
            JSONObject header = messageObj.getJSONObject("header");
            JSONObject body = messageObj.getJSONObject("body");

            if (header != null) {
                log.info("【MQ消费】header={}", header);
            }
            if (body != null) {
                log.info("【MQ消费】body={}", body);
            }

            // 业务执行成功---->自动ACK
            TraceIdUtil.clear();

        } catch (Exception e) {
            log.error("【MQ消费】业务处理失败，消息会重新投递: {}", e.getMessage(), e);
            TraceIdUtil.clear();
            //抛出异常，不会ACK，消息会重试
            throw new RuntimeException("消费失败，消息重新投递");
        }
    }

    /**
     * 从消息JSON中提取traceId
     */
    private String extractTraceId(String msgJson) {
        try {
            JSONObject messageObj = JSON.parseObject(msgJson);
            JSONObject header = messageObj.getJSONObject("header");
            if (header != null) {
                String traceId = header.getString("traceId");
                if (traceId != null && !traceId.isEmpty()) {
                    return traceId;
                }
            }
        } catch (Exception e) {
            log.warn("提取traceId失败: {}", e.getMessage());
        }
        // 如果提取失败，生成新的
        return TraceIdUtil.generateTraceId();
    }
}
