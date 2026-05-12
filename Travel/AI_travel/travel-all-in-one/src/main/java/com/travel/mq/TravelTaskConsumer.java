package com.travel.mq;

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
        String msg=new String(message.getBody(), StandardCharsets.UTF_8);
        //System.out.println(msg);

        try{
            //执行业务
            System.out.println("正在处理业务: "+msg);
            //业务执行成功---->自动ACK
        }catch (Exception e){
            System.out.println("业务处理失败，消息会重新投递: "+e.getMessage());

            //抛出异常，不会ACK，消息会重试
            throw new RuntimeException("消费失败，消息重新投递");
        }
    }
}
